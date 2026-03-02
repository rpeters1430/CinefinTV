package com.rpeters.cinefintv.di

import android.content.Context
import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Optimized client factory with connection pooling and performance optimizations.
 * Reuses HTTP connections and provides optimized client configuration.
 */
@Singleton
class OptimizedClientFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jellyfin: Jellyfin,
    private val authRepositoryProvider: Provider<JellyfinAuthRepository>,
) {
    private val clients = mutableMapOf<String, ApiClient>()
    private val clientLock = Any()
    companion object {
        private const val TAG = "OptimizedClientFactory"
        private const val CONNECTION_POOL_SIZE = 5
        private const val CONNECTION_KEEP_ALIVE_MINUTES = 5L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
    }

    /**
     * Create ApiClient bound to the current token for the given server URL.
     * Note: Jellyfin SDK uses its own Ktor client; OkHttp interceptors here would have no effect.
     */
    private fun createOptimizedClient(serverUrl: String, token: String?): ApiClient {
        // Build the Jellyfin ApiClient with the current token so requests include X-Emby-Token
        return jellyfin.createApi(
            baseUrl = serverUrl,
            accessToken = token,
        )
    }

    // Note: Token/header interceptors using OkHttp are not applicable to Jellyfin SDK (Ktor-backed)

    /**
     * Optimized interceptor with keep-alive and compression
     */
    private fun createOptimizedInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Connection", "keep-alive")
                .addHeader("Accept-Encoding", "gzip, deflate")
                .addHeader("User-Agent", "JellyfinAndroid/1.0")
                .build()

            chain.proceed(request)
        }
    }

    /**
     * Logging interceptor for debug builds
     */
    private fun createLoggingInterceptor(): Interceptor {
        return HttpLoggingInterceptor { message ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "HTTP: $message")
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
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
     * Get an ApiClient keyed by (serverUrl, currentToken). When the token changes,
     * a new client is created and cached under a new key.
     */
    suspend fun getOptimizedClient(serverUrl: String): ApiClient {
        val token = authRepositoryProvider.get().token()
        val key = "$serverUrl|${token ?: ""}"
        return synchronized(clientLock) {
            clients.getOrPut(key) {
                logDebug("Creating new optimized client for: $serverUrl")
                createOptimizedClient(serverUrl, token)
            }
        }
    }

    /**
     * Invalidate client cache for a server
     */
    fun invalidateClient(serverUrl: String) {
        synchronized(clientLock) {
            // Keys are stored as "serverUrl|token"; remove all entries for this server URL
            val keysToRemove = clients.keys.filter { it.startsWith("$serverUrl|") || it == serverUrl }
            keysToRemove.forEach { key ->
                clients.remove(key)
            }
            if (keysToRemove.isNotEmpty()) {
                logDebug("Invalidated ${keysToRemove.size} optimized client(s) for: $serverUrl")
            }
        }
    }

    /**
     * Clear all cached clients
     */
    fun clearAllClients() {
        synchronized(clientLock) {
            clients.clear()
            logDebug("Cleared all optimized clients")
        }
    }
}
