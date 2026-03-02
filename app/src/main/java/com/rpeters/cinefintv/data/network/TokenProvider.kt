package com.rpeters.cinefintv.data.network

/**
 * Interface for providing authentication tokens to HTTP requests.
 * Implements lazy token attachment - tokens are fetched fresh for each request.
 */
interface TokenProvider {
    /**
     * Get the current authentication token.
     * @return Current token or null if not authenticated
     */
    suspend fun token(): String?

    /**
     * Attach the current token to an HTTP request.
     * Token is fetched fresh when this method is called.
     *
     * Note: This is a simplified interface. In a real implementation,
     * the token attachment would be handled by HTTP client interceptors.
     */
    suspend fun attachToken(headers: MutableMap<String, String>) {
        token()?.let { token ->
            headers["X-MediaBrowser-Token"] = token
        }
    }
}
