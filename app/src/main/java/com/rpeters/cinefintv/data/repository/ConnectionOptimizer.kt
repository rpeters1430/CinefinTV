package com.rpeters.cinefintv.data.repository

import android.content.Context
import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimized connection management for Jellyfin servers.
 * Implements parallel server discovery with intelligent URL prioritization.
 */
@Singleton
class ConnectionOptimizer @Inject constructor(
    private val sessionManager: JellyfinSessionManager,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ConnectionOptimizer"
        private const val CONNECTION_TIMEOUT_MS = 5000L
        private const val MAX_PARALLEL_REQUESTS = 6 // Increased for better reverse proxy handling
    }

    /**
     * Test server connection with parallel URL discovery
     */
    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return withContext(Dispatchers.IO) {
            logDebug("Starting parallel server discovery for: $serverUrl")

            val urlVariations = getUrlVariations(serverUrl)
            val prioritizedUrls = prioritizeUrls(urlVariations)

            logDebug("Testing ${prioritizedUrls.size} URL variations in parallel")

            // Test URLs in parallel with limited concurrency
            val results = prioritizedUrls
                .take(MAX_PARALLEL_REQUESTS)
                .map { url ->
                    async {
                        logDebug("Testing endpoint: $url")
                        testSingleEndpoint(url)
                    }
                }

            // Return first successful result
            results.forEachIndexed { index, deferred ->
                try {
                    val result = deferred.await()
                    if (result is ApiResult.Success) {
                        logDebug("Successfully connected to: ${prioritizedUrls[index]}")
                        return@withContext result
                    }
                } catch (e: CancellationException) {
                    throw e
                }
            }

            logDebug("No working endpoints found")
            ApiResult.Error("No working endpoints found for $serverUrl")
        }
    }

    /**
     * Get URL variations to test, with special handling for reverse proxy setups
     */
    private fun getUrlVariations(baseUrl: String): List<String> {
        val urls = mutableListOf<String>()

        // Normalize base URL
        val normalizedUrl = baseUrl.trim().removeSuffix("/")

        // Check if this looks like a reverse proxy setup
        val isReverseProxy = normalizedUrl.contains("/jellyfin") ||
            normalizedUrl.substringAfter("://").substringBefore("/").substringBefore(":").count { it == '.' } >= 2 ||
            normalizedUrl.contains(":443") || normalizedUrl.contains(":80")

        if (isReverseProxy) {
            // For reverse proxy setups, prioritize the exact URL first
            urls.add(normalizedUrl)

            // Add /jellyfin path if not already present
            if (!normalizedUrl.endsWith("/jellyfin")) {
                urls.add("$normalizedUrl/jellyfin")
            }

            // Try without the path if it was included (but not if it's just /jellyfin)
            if (normalizedUrl.endsWith("/jellyfin") && normalizedUrl != baseUrl) {
                urls.add(normalizedUrl.removeSuffix("/jellyfin"))
            }

            // Add protocol variations
            if (normalizedUrl.startsWith("https://")) {
                urls.add(normalizedUrl.replace("https://", "http://"))
                // Try standard ports
                try {
                    val uri = URI(normalizedUrl)
                    val host = uri.host
                    val path = uri.path ?: ""
                    urls.add("https://$host:443$path")
                    urls.add("http://$host:80$path")
                    // Only add /jellyfin if not already present
                    if (!path.endsWith("/jellyfin")) {
                        urls.add("https://$host:443$path/jellyfin")
                        urls.add("http://$host:80$path/jellyfin")
                    }
                } catch (e: CancellationException) {
                    throw e
                }
            } else if (normalizedUrl.startsWith("http://")) {
                urls.add(normalizedUrl.replace("http://", "https://"))
                // Try standard ports
                try {
                    val uri = URI(normalizedUrl)
                    val host = uri.host
                    val path = uri.path ?: ""
                    urls.add("https://$host:443$path")
                    urls.add("http://$host:80$path")
                    // Only add /jellyfin if not already present
                    if (!path.endsWith("/jellyfin")) {
                        urls.add("https://$host:443$path/jellyfin")
                        urls.add("http://$host:80$path/jellyfin")
                    }
                } catch (e: CancellationException) {
                    throw e
                }
            } else {
                // No protocol specified
                urls.add("https://$normalizedUrl")
                urls.add("http://$normalizedUrl")
                // Only add /jellyfin if not already present
                if (!normalizedUrl.endsWith("/jellyfin")) {
                    urls.add("https://$normalizedUrl/jellyfin")
                    urls.add("http://$normalizedUrl/jellyfin")
                }
            }
        } else {
            // Standard Jellyfin server setup
            // Add HTTPS variations
            if (normalizedUrl.startsWith("https://")) {
                urls.add(normalizedUrl)
                urls.add(normalizedUrl.replace("https://", "http://"))
            } else if (normalizedUrl.startsWith("http://")) {
                urls.add(normalizedUrl.replace("http://", "https://"))
                urls.add(normalizedUrl)
            } else {
                // No protocol specified
                urls.add("https://$normalizedUrl")
                urls.add("http://$normalizedUrl")
            }

            // Add port variations
            val urlsWithPorts = mutableListOf<String>()
            urls.forEach { url ->
                urlsWithPorts.add(url)
                if (!url.contains(":")) {
                    urlsWithPorts.add("$url:8096") // Default Jellyfin port
                    urlsWithPorts.add("$url:443") // HTTPS port
                    urlsWithPorts.add("$url:80") // HTTP port
                }
            }
            urls.clear()
            urls.addAll(urlsWithPorts)
        }

        // Remove duplicates and ensure we don't have duplicate jellyfin paths
        val distinctUrls = urls.distinct()
        return distinctUrls.filter { url ->
            // Remove any URLs that would result in double /jellyfin paths
            !(url.contains("/jellyfin/jellyfin"))
        }
    }

    /**
     * Prioritize URLs for faster discovery, with special handling for reverse proxy setups
     */
    private fun prioritizeUrls(urls: List<String>): List<String> {
        return urls.sortedBy { url ->
            when {
                // Exact match gets highest priority
                url.contains("/jellyfin") -> 0
                // HTTPS with standard ports next
                url.startsWith("https://") && (url.contains(":443") || !url.contains(":")) -> 1
                // HTTP with standard ports next
                url.startsWith("http://") && (url.contains(":80") || !url.contains(":")) -> 2
                // HTTPS without ports
                url.startsWith("https://") -> 3
                // HTTP without ports
                url.startsWith("http://") -> 4
                // Default Jellyfin ports
                url.contains(":8096") -> 5
                // Other ports last
                else -> 6
            }
        }
    }

    /**
     * Test a single endpoint with timeout
     */
    private suspend fun testSingleEndpoint(url: String): ApiResult<PublicSystemInfo> {
        return try {
            withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                // Skip normalization since we're testing the exact URL variations
                val client = sessionManager.getClientForUrl(url)
                val response = client.systemApi.getPublicSystemInfo()
                ApiResult.Success(response.content)
            } ?: ApiResult.Error("Connection timeout for $url")
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Helper function for debug logging that only logs in debug builds
     */
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
