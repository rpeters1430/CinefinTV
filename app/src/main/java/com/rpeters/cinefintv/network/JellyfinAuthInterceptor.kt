package com.rpeters.cinefintv.network

import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Authentication interceptor for Jellyfin API requests.
 *
 * **Optimization Strategy**:
 * - Proactively refreshes tokens 5 minutes before expiration to minimize blocking
 * - Uses authMutex in JellyfinAuthRepository to prevent concurrent refresh attempts
 * - Optimized backoff delays (0ms, 100ms, 500ms) to reduce total blocking time
 * - Double-check pattern in forceReAuthenticate() avoids redundant refreshes
 *
 * **Thread Safety**:
 * - Uses runBlocking (required by OkHttp's synchronous API)
 * - OkHttp runs interceptors on background threads, so blocking is acceptable
 * - Mutex protection in repository prevents stampeding herd problem
 */
@Singleton
class JellyfinAuthInterceptor @Inject constructor(
    private val authRepositoryProvider: Provider<JellyfinAuthRepository>,
    private val deviceIdentityProvider: DeviceIdentityProvider,
) : Interceptor, Authenticator {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val repository = authRepositoryProvider.get()
        val currentServer = repository.getCurrentServer()
        
        // Only attach Jellyfin auth headers if we're hitting the Jellyfin server
        val isJellyfinRequest = currentServer?.url?.let { request.url.toString().startsWith(it) } ?: false
        val shouldAttachToken = isJellyfinRequest && !isAuthenticationRequest(request)

        val token = if (shouldAttachToken) {
            ensureFreshToken(repository)
            val server = repository.getCurrentServer()
            if (server?.accessToken == null) {
                SecureLogger.w(TAG, "No access token available after token refresh attempt")
            }
            server?.accessToken
        } else {
            null
        }

        // Only build request with Jellyfin headers if it's a Jellyfin request
        val updatedRequest = if (isJellyfinRequest) {
            buildRequestWithHeaders(request, token)
        } else {
            request
        }
        
        return chain.proceed(updatedRequest)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (!shouldRetry(response)) {
            return null
        }

        val repository = authRepositoryProvider.get()
        val attempt = responseCount(response)
        backoff(attempt)

        // Note: runBlocking is necessary here as OkHttp's Authenticator is a synchronous API
        // OkHttp already runs this on a background thread, so we don't need to switch dispatchers
        val refreshed = runBlocking {
            repository.forceReAuthenticate()
        }

        if (!refreshed) {
            SecureLogger.w(TAG, "Failed to refresh token after 401 (attempt $attempt)")
            return null
        }

        val token = repository.getCurrentServer()?.accessToken
        if (token == null) {
            SecureLogger.w(TAG, "No token available after successful re-authentication (attempt $attempt)")
            return null
        }

        return buildRequestWithHeaders(response.request, token)
    }

    private fun ensureFreshToken(repository: JellyfinAuthRepository) {
        if (!repository.isUserAuthenticated()) {
            return
        }

        // Proactively refresh if token is approaching expiration (not just expired)
        // This reduces the likelihood of blocking on expired tokens
        if (!repository.shouldRefreshToken()) {
            return
        }

        // Note: runBlocking is necessary here as Interceptor.intercept() is a synchronous API
        // OkHttp already runs this on a background thread, so we don't need to switch dispatchers
        // The authMutex in forceReAuthenticate() prevents concurrent refresh attempts
        runBlocking {
            val refreshed = repository.forceReAuthenticate()
            if (!refreshed) {
                SecureLogger.w(TAG, "Token refresh failed during request preparation")
            }
        }
    }

    private fun buildRequestWithHeaders(
        request: Request,
        token: String?,
    ): Request {
        val userAgent = "${deviceIdentityProvider.clientName()}/${deviceIdentityProvider.clientVersion()}"
        val builder = request.newBuilder()
            .header(HEADER_CONNECTION, "keep-alive")
            .header(HEADER_ACCEPT_ENCODING, "gzip, deflate")
            .header(HEADER_USER_AGENT, userAgent)

        builder.header(HEADER_AUTHORIZATION, buildAuthorizationHeader(token))
        if (!token.isNullOrBlank()) {
            builder.header(HEADER_MEDIA_BROWSER_AUTHORIZATION, buildMediaBrowserAuthorization(token))
        }

        if (!token.isNullOrBlank() && !isAuthenticationRequest(request)) {
            builder.header(HEADER_TOKEN, token)
        }

        return builder.build()
    }

    /**
     * Builds the X-Emby-Authorization header value.
     * WARNING: This string contains the authentication token and must NEVER be logged.
     * Only the last 4-6 characters should be logged for debugging purposes.
     */
    private fun buildAuthorizationHeader(token: String?): String {
        return buildString {
            append("MediaBrowser Client=\"")
            append(deviceIdentityProvider.clientName())
            append("\", Device=\"")
            append(deviceIdentityProvider.deviceName())
            append("\", DeviceId=\"")
            append(deviceIdentityProvider.deviceId())
            append("\", Version=\"")
            append(deviceIdentityProvider.clientVersion())
            append("\"")
            if (!token.isNullOrBlank()) {
                append(", Token=\"")
                append(token) // SECURITY: Never log this value
                append("\"")
            }
        }
    }

    private fun buildMediaBrowserAuthorization(token: String): String {
        return "MediaBrowser Token=\"$token\""
    }

    private fun shouldRetry(response: Response): Boolean {
        val repository = authRepositoryProvider.get()
        val serverUrl = repository.getCurrentServer()?.url
        
        // Only retry with Jellyfin auth if it's a request to our Jellyfin server
        if (serverUrl == null || !response.request.url.toString().startsWith(serverUrl)) {
            return false
        }

        if (isAuthenticationRequest(response.request)) {
            return false
        }

        if (response.code != 401) {
            return false
        }

        if (responseCount(response) >= MAX_AUTH_RETRIES) {
            SecureLogger.w(TAG, "Max auth retries reached (${responseCount(response)})")
            return false
        }

        return true
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var count = 1
        while (current?.priorResponse != null) {
            current = current.priorResponse
            count++
        }
        return count
    }

    private fun backoff(attempt: Int) {
        val delayMillis = when {
            attempt <= RETRY_BACKOFF_MS.size -> RETRY_BACKOFF_MS[attempt - 1]
            else -> RETRY_BACKOFF_MS.last()
        }

        if (delayMillis <= 0) {
            return
        }

        // Use Thread.sleep for synchronous backoff (required by OkHttp's synchronous API)
        // Optimized delays to reduce blocking: first retry immediate, subsequent retries use minimal backoff
        Thread.sleep(delayMillis)
    }

    private fun isAuthenticationRequest(request: Request): Boolean {
        val url = request.url.toString()
        return AUTH_PATHS.any { url.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val TAG = "JellyfinAuthInterceptor"
        private const val HEADER_TOKEN = "X-Emby-Token"
        private const val HEADER_AUTHORIZATION = "X-Emby-Authorization"
        private const val HEADER_MEDIA_BROWSER_AUTHORIZATION = "Authorization"
        private const val HEADER_CONNECTION = "Connection"
        private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val MAX_AUTH_RETRIES = 3
        private val AUTH_PATHS = listOf("/Users/Authenticate", "/Sessions")

        // Optimized backoff delays: 0ms (immediate), 100ms, 500ms
        // Reduced from 0ms, 250ms, 1000ms to minimize thread blocking
        private val RETRY_BACKOFF_MS = longArrayOf(0L, 100L, 500L)
    }
}
