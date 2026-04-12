package com.rpeters.cinefintv.data.repository

import android.util.Log
import com.rpeters.cinefintv.core.ErrorHandler
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Simplified system repository for server validation and normalization.
 */
@Singleton
class JellyfinSystemRepository @Inject constructor(
    private val sessionManager: JellyfinSessionManager,
) {
    companion object {
        private const val TAG = "JellyfinSystemRepository"
    }

    /**
     * Get Jellyfin API client on background thread to avoid StrictMode violations.
     */
    private suspend fun getClient(serverUrl: String): ApiClient =
        sessionManager.getClientForUrl(serverUrl)

    private fun <T> handleException(e: Exception, defaultMessage: String = "System error"): ApiResult.Error<T> {
        val jellyfinError = ErrorHandler.handleException(e, mapOf("operation" to "system_api", "message" to defaultMessage))
        return ErrorHandler.toApiResult(jellyfinError)
    }

    /**
     * Enhanced server connection testing
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            val client = getClient(serverUrl)
            val response = client.systemApi.getPublicSystemInfo()
            ApiResult.Success(response.content)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleException(e, "Connection failed for $serverUrl")
        }
    }

    /**
     * ✅ IMPROVEMENT: Validate server URL format
     */
    fun validateServerUrl(serverUrl: String): Boolean {
        return try {
            val trimmed = serverUrl.trim()
            when {
                trimmed.isBlank() -> false
                !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> false
                trimmed.contains(" ") -> false
                else -> {
                    // Basic URL validation
                    val url = java.net.URL(trimmed)
                    url.host.isNotBlank()
                }
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * ✅ IMPROVEMENT: Normalize server URL
     */
    fun normalizeServerUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim()
        return when {
            trimmed.isBlank() -> ""
            !trimmed.startsWith("http") -> "https://$trimmed"
            else -> trimmed.trimEnd('/')
        }
    }
}
