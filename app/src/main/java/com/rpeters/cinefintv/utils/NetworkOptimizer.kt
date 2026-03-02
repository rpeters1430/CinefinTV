package com.rpeters.cinefintv.utils

import android.app.Application
import android.net.TrafficStats
import android.os.StrictMode
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Optimizes network operations for StrictMode compliance and performance
 */
object NetworkOptimizer {
    private const val TAG = "NetworkOptimizer"
    private const val NETWORK_TAG = "jellyfin_network"

    /**
     * Initialize network optimizations for the entire application
     */
    fun initialize(application: Application) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Set global traffic stats tagging for untagged operations
                setupGlobalNetworkTagging()

                Log.d(TAG, "Network optimizations initialized successfully")
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun setupGlobalNetworkTagging() {
        // Create a thread-local tag for network operations
        val networkTag = NETWORK_TAG.hashCode()

        // Store the original handler BEFORE setting the new one to avoid infinite recursion
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // This helps with ExoPlayer and other libraries that might not tag their sockets
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.w(TAG, "Uncaught exception in thread ${thread.name}", throwable)

            // Clear any lingering traffic stats tags
            try {
                TrafficStats.clearThreadStatsTag()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }

            // Call the original handler that was stored before setting our handler
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Execute network operation with proper tagging
     */
    suspend fun <T> executeTaggedNetworkOperation(
        tag: String = NETWORK_TAG,
        operation: suspend () -> T,
    ): T {
        return PerformanceOptimizer.executeNetworkOperation {
            TrafficStats.setThreadStatsTag(tag.hashCode())
            try {
                operation()
            } finally {
                TrafficStats.clearThreadStatsTag()
            }
        }
    }

    /**
     * Create a properly configured OkHttpClient for ExoPlayer data sources
     */
    fun createExoPlayerOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                TrafficStats.setThreadStatsTag("exoplayer_media".hashCode())
                try {
                    chain.proceed(chain.request())
                } finally {
                    TrafficStats.clearThreadStatsTag()
                }
            }
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Properly close network resources to prevent leaks
     */
    fun closeNetworkResources(vararg closeables: AutoCloseable?) {
        closeables.forEach { closeable ->
            try {
                closeable?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Failed to close network resource", e)
            }
        }
    }

    /**
     * Configure StrictMode for network optimizations
     * Note: Untagged socket detection disabled due to Jellyfin SDK using internal Ktor client
     */
    fun configureNetworkStrictMode() {
        if (com.rpeters.cinefintv.BuildConfig.DEBUG) {
            @Suppress("DEPRECATION")
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectResourceMismatches()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    // Crash fast on network calls on main thread in debug builds.
                    .penaltyDeathOnNetwork()
                    .build(),
            )

            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .detectActivityLeaks()
                    // Disabled: .detectUntaggedSockets() - Jellyfin SDK uses internal Ktor client
                    .penaltyLog()
                    .build(),
            )
        }
    }
}
