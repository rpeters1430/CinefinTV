package com.rpeters.cinefintv.data.repository.common

import com.rpeters.cinefintv.core.LogCategory
import com.rpeters.cinefintv.core.Logger
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.data.utils.RepositoryUtils
import com.rpeters.cinefintv.ui.utils.RetryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

/**
 * Lightweight base class shared by repository implementations.
 * It centralizes common helpers like client creation and safe
 * execution wrappers so individual repositories can stay focused
 * on their domain logic.
 */
open class BaseJellyfinRepository @Inject constructor(
    protected val authRepository: JellyfinAuthRepository,
    protected val sessionManager: JellyfinSessionManager,
    protected val cache: JellyfinCache,
) {
    // Mutex to prevent race conditions in token refresh
    private val tokenRefreshMutex = Mutex()

    /**
     * Get Jellyfin API client on background thread to avoid StrictMode violations.
     * Client creation involves static initialization that performs file I/O.
     */
    protected suspend fun getClient(serverUrl: String, accessToken: String?): ApiClient =
        sessionManager.getClientForUrl(serverUrl)

    fun validateServer(): JellyfinServer {
        val currentServer = authRepository.getCurrentServer()
        return RepositoryUtils.validateServer(currentServer)
    }

    protected fun parseUuid(id: String, type: String) =
        RepositoryUtils.parseUuid(id, type)

    /**
     * Checks if token needs refresh and proactively refreshes it
     */
    protected suspend fun validateTokenAndRefreshIfNeeded() {
        if (authRepository.isTokenExpired()) {
            tokenRefreshMutex.withLock {
                // Double-check after acquiring lock (another thread might have refreshed)
                if (authRepository.isTokenExpired()) {
                    Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Token expired, proactively refreshing with force")

                    val refreshResult = authRepository.forceReAuthenticate()
                    if (refreshResult) {
                        Logger.d(LogCategory.NETWORK, javaClass.simpleName, "Proactive force token refresh successful")
                        sessionManager.invalidateClients()
                    } else {
                        Logger.w(LogCategory.NETWORK, javaClass.simpleName, "Proactive force token refresh failed")
                        // Don't throw here - let the subsequent API call handle the 401
                    }
                }
            }
        }
    }

    /**
     * Execute operation with 401-aware authentication handling.
     * This eliminates stale token issues and properly handles token refresh for 401 errors.
     * ✅ STRICTMODE FIX: All network operations run on IO dispatcher
     */
    protected suspend fun <T> executeWithClient(
        operationName: String,
        operation: suspend (ApiClient) -> T,
    ): T = withContext(Dispatchers.IO) {
        return@withContext executeWithTokenRefresh {
            // Delegate to session manager for token/401 handling
            sessionManager.executeWithAuth(operationName) { _, client ->
                operation(client)
            }
        }
    }

    /**
     * Execute operation with 401-aware authentication handling.
     * Implements single-flight reauth with mutex to prevent concurrent authentication attempts.
     */
    protected suspend fun <T> executeWithTokenRefresh(
        operation: suspend () -> T,
    ): T {
        // Proactively check and refresh token if expired
        validateTokenAndRefreshIfNeeded()

        return try {
            operation()
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Converts an exception to an ApiResult.Error with appropriate error type and message.
     */
    private fun <T> handleRepositoryException(e: Exception, operationName: String): ApiResult.Error<T> {
        val errorType = RepositoryUtils.getErrorType(e)
        Logger.w(LogCategory.NETWORK, javaClass.simpleName, "$operationName failed: ${e.message}", e)

        // Provide specific default message based on error type
        val defaultMessage = when {
            errorType == ErrorType.AUTHENTICATION -> "Authentication required"
            e is IllegalStateException -> "Invalid application state"
            else -> "Operation failed"
        }

        return ApiResult.Error(e.message ?: defaultMessage, e, errorType)
    }

    /**
     * ✅ NEW: Wraps a client-based operation returning [ApiResult].
     * Uses the new TokenProvider approach to eliminate stale token issues.
     */
    protected suspend fun <T> execute(
        operationName: String,
        block: suspend (ApiClient) -> T,
    ): ApiResult<T> =
        try {
            val result = executeWithClient(operationName, block)
            ApiResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleRepositoryException(e, operationName)
        }

    /**
     * Helper method to make the server/client pattern easier to use safely.
     * This ensures fresh server state and client are created inside the execution block.
     *
     * Usage example:
     * ```kotlin
     * return withServerClient("getLibraryItems") { server, client ->
     *     val response = client.itemsApi.getItems(
     *         userId = server.userId,
     *         // ...
     *     )
     *     response.content.items ?: emptyList()
     * }
     * ```
     */
    protected suspend inline fun <T> withServerClient(
        operationName: String,
        crossinline block: suspend (server: JellyfinServer, client: ApiClient) -> T,
    ): ApiResult<T> = execute(operationName) { client ->
        val server = validateServer()
        block(server, client)
    }

    /**
     * Legacy method for backward compatibility during transition.
     * Wraps a suspend block returning [ApiResult]. Any thrown exception
     * is converted to an [ApiResult.Error] with a best-effort error type.
     * ✅ STRICTMODE FIX: All network operations run on IO dispatcher
     */
    protected suspend fun <T> executeLegacy(
        operationName: String,
        block: suspend () -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            // ensure proactive check + 401-aware retry
            val result = executeWithTokenRefresh { block() }
            ApiResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleRepositoryException(e, operationName)
        }
    }

    /**
     * Executes a block with automatic retry logic and error handling.
     * This provides smart retry behavior for all repository operations.
     * ✅ STRICTMODE FIX: All network operations run on IO dispatcher
     */
    protected suspend fun <T> executeWithRetry(
        operationName: String,
        maxAttempts: Int = 3,
        block: suspend () -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        return@withContext RetryManager.withRetry(maxAttempts, operationName) { attempt ->
            try {
                val result = executeWithTokenRefresh { block() }
                ApiResult.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleRepositoryException(e, operationName)
            }
        }
    }

    /**
     * Executes a block with both retry logic and circuit breaker protection.
     * Use this for critical operations that should be protected from cascading failures.
     * ✅ STRICTMODE FIX: All network operations run on IO dispatcher
     */
    protected suspend fun <T> executeWithRetryAndCircuitBreaker(
        operationName: String,
        maxAttempts: Int = 3,
        circuitBreakerKey: String = operationName,
        block: suspend () -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        return@withContext RetryManager.withRetryAndCircuitBreaker(maxAttempts, operationName, circuitBreakerKey) { attempt ->
            try {
                val result = executeWithTokenRefresh { block() }
                ApiResult.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleRepositoryException(e, operationName)
            }
        }
    }

    /**
     * Executes an operation with cache-first strategy and automatic fallback.
     * First checks cache, then tries network with retry, and caches successful results.
     * ✅ STRICTMODE FIX: Cache checks on IO dispatcher to avoid disk reads on main thread
     */
    protected suspend fun executeWithCache(
        operationName: String,
        cacheKey: String,
        maxAttempts: Int = 3,
        cacheTtlMs: Long = 30 * 60 * 1000L, // 30 minutes default
        block: suspend () -> List<BaseItemDto>,
    ): ApiResult<List<BaseItemDto>> = withContext(Dispatchers.IO) {
        // Try cache first
        val cachedData = cache.getCachedItems(cacheKey)
        if (cachedData != null) {
            return@withContext ApiResult.Success(cachedData)
        }

        // Cache miss, try network with retry
        return@withContext executeWithRetryAndCircuitBreaker(operationName, maxAttempts, cacheKey) {
            val result = block()
            // Cache successful results
            cache.cacheItems(cacheKey, result, cacheTtlMs)
            result
        }
    }

    /**
     * Force refresh data and update cache.
     * ✅ STRICTMODE FIX: Cache operations on IO dispatcher to avoid disk I/O on main thread
     */
    protected suspend fun executeRefreshWithCache(
        operationName: String,
        cacheKey: String,
        maxAttempts: Int = 3,
        cacheTtlMs: Long = 30 * 60 * 1000L,
        block: suspend () -> List<BaseItemDto>,
    ): ApiResult<List<BaseItemDto>> = withContext(Dispatchers.IO) {
        // Invalidate existing cache
        cache.invalidateCache(cacheKey)

        // Execute with retry and cache result
        return@withContext executeWithRetryAndCircuitBreaker(operationName, maxAttempts, cacheKey) {
            val result = block()
            // Cache the fresh results
            cache.cacheItems(cacheKey, result, cacheTtlMs)
            result
        }
    }
}
