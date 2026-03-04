package com.rpeters.cinefintv.data.repository

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.repository.common.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Intelligent retry strategy with exponential backoff and error-specific handling.
 * Provides optimized retry logic for different types of network errors.
 */
@Singleton
class RetryStrategy @Inject constructor() {
    companion object {
        private const val TAG = "RetryStrategy"
        private const val DEFAULT_MAX_RETRIES = 3
        private const val MAX_RETRY_DELAY_MS = 10000L
    }

    /**
     * Execute with intelligent retry based on error type and network conditions
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        operation: suspend () -> T,
    ): ApiResult<T> {
        var lastException: Exception? = null
        val attempts = maxRetries.coerceAtLeast(0) + 1

        repeat(attempts) { attempt ->
            try {
                val result = operation()
                if (attempt > 0) {
                    logDebug("Operation succeeded on attempt ${attempt + 1}")
                }
                return ApiResult.Success(result)
            } catch (e: CancellationException) {
                // Always re-throw cancellation exceptions for proper coroutine cancellation
                throw e
            } catch (e: Exception) {
                lastException = e
                val shouldRetry = attempt < attempts - 1 && shouldRetry(e, attempt)
                if (!shouldRetry) {
                    return ApiResult.Error(
                        message = e.message ?: "Operation failed",
                        cause = e,
                    )
                }

                val delayMs = calculateRetryDelay(e, attempt)
                logDebug("Retrying after ${delayMs}ms due to ${e.javaClass.simpleName}")
                delay(delayMs)
            }
        }

        return ApiResult.Error(
            message = lastException?.message ?: "Operation failed after $attempts attempts",
            cause = lastException,
        )
    }

    /**
     * Determine if operation should be retried based on error type
     */
    private fun shouldRetry(exception: Exception, attempt: Int): Boolean {
        return when (exception) {
            is HttpException -> {
                val statusCode = exception.code()
                when (statusCode) {
                    408, 429, 500, 502, 503, 504 -> {
                        logDebug("Retrying HTTP error $statusCode (attempt $attempt)")
                        true // Retryable status codes
                    }
                    401, 403, 404 -> {
                        logDebug("Not retrying HTTP error $statusCode (auth/not found)")
                        false // Don't retry auth/not found errors
                    }
                    else -> {
                        val shouldRetry = attempt < 2
                        logDebug("Limited retry for HTTP error $statusCode: $shouldRetry")
                        shouldRetry // Limited retries for other errors
                    }
                }
            }
            is SocketTimeoutException -> {
                logDebug("Retrying socket timeout (attempt $attempt)")
                true
            }
            is ConnectException -> {
                logDebug("Retrying connection exception (attempt $attempt)")
                true
            }
            is UnknownHostException -> {
                logDebug("Not retrying DNS failure: ${exception.message}")
                false // Don't retry DNS failures - user needs to fix the hostname
            }
            is IOException -> {
                // Check if this is a wrapped DNS error (GaiException)
                if (isDnsError(exception)) {
                    logDebug("Not retrying DNS failure (GaiException): ${exception.message}")
                    false // Don't retry DNS failures
                } else {
                    logDebug("Retrying I/O exception (attempt $attempt)")
                    true // Retry other I/O errors
                }
            }
            else -> {
                logDebug("Not retrying unknown error type: ${exception.javaClass.simpleName}")
                false // Don't retry unknown errors
            }
        }
    }

    /**
     * Calculate retry delay with exponential backoff and jitter
     */
    private fun calculateRetryDelay(exception: Exception, attempt: Int): Long {
        val baseDelay = when (exception) {
            is HttpException -> when (exception.code()) {
                429 -> 5000L // Rate limited - longer delay
                503 -> 2000L // Service unavailable
                else -> 1000L // Other server errors
            }
            else -> 1000L // Network errors
        }

        val exponentialDelay = baseDelay * (2.0.pow(attempt.toDouble())).toLong()
        val jitter = (Math.random() * 0.1 * exponentialDelay).toLong() // 10% jitter

        return minOf(exponentialDelay + jitter, MAX_RETRY_DELAY_MS)
    }

    /**
     * Execute with custom retry configuration
     */
    suspend fun <T> executeWithCustomRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = MAX_RETRY_DELAY_MS,
        shouldRetryPredicate: (Exception, Int) -> Boolean = { _, _ -> true },
        operation: suspend () -> T,
    ): ApiResult<T> {
        var lastException: Exception? = null
        val attempts = maxRetries.coerceAtLeast(0) + 1

        repeat(attempts) { attempt ->
            try {
                val result = operation()
                if (attempt > 0) {
                    logDebug("Custom retry operation succeeded on attempt ${attempt + 1}")
                }
                return ApiResult.Success(result)
            } catch (e: CancellationException) {
                // Always re-throw cancellation exceptions for proper coroutine cancellation
                throw e
            } catch (e: Exception) {
                lastException = e
                val shouldRetry = attempt < attempts - 1 && shouldRetryPredicate(e, attempt)
                if (!shouldRetry) {
                    return ApiResult.Error(
                        message = e.message ?: "Custom retry operation failed",
                        cause = e,
                    )
                }

                val delayMs = calculateCustomRetryDelay(baseDelayMs, maxDelayMs, attempt)
                logDebug("Custom retry waiting ${delayMs}ms after ${e.javaClass.simpleName}")
                delay(delayMs)
            }
        }

        return ApiResult.Error(
            message = lastException?.message ?: "Custom retry operation failed after $attempts attempts",
            cause = lastException,
        )
    }

    /**
     * Calculate custom retry delay
     */
    private fun calculateCustomRetryDelay(baseDelayMs: Long, maxDelayMs: Long, attempt: Int): Long {
        val exponentialDelay = baseDelayMs * (2.0.pow(attempt.toDouble())).toLong()
        val jitter = (Math.random() * 0.1 * exponentialDelay).toLong()
        return minOf(exponentialDelay + jitter, maxDelayMs)
    }

    /**
     * Helper function for debug logging
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    /**
     * Checks if an IOException is actually a DNS resolution error (GaiException).
     * This handles cases where android.system.GaiException is wrapped in IOException.
     */
    private fun isDnsError(exception: IOException): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            val className = current.javaClass.name
            val message = current.message ?: ""

            // Check for GaiException by class name
            if (className.contains("GaiException")) {
                return true
            }

            // Check for specific DNS error messages
            if (message.contains("EAI_NODATA", ignoreCase = true) ||
                message.contains("EAI_NONAME", ignoreCase = true) ||
                message.contains("No address associated with hostname", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true)
            ) {
                return true
            }

            current = current.cause
        }
        return false
    }
}
