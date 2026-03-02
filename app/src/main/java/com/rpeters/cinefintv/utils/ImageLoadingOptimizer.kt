package com.rpeters.cinefintv.utils

import android.content.Context
import androidx.annotation.VisibleForTesting
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.rpeters.cinefintv.OptInAppExperimentalApis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Optimizes image loading with Coil to prevent memory leaks and improve performance
 */
@OptInAppExperimentalApis
object ImageLoadingOptimizer {
    private const val TAG = "ImageLoadingOptimizer"
    private const val BYTES_PER_MEGABYTE = 1024L * 1024L

    // Coil 3.x: Cache directory is now handled by resolve()
    // This function is no longer needed but kept for backward compatibility
    private fun getCacheDirectory(context: Context): File {
        return context.cacheDir.resolve("image_cache").apply {
            mkdirs()
        }
    }

    suspend fun clearImageCache(context: Context) {
        clearImageCache(SingletonImageLoader.get(context))
    }

    @VisibleForTesting
    internal fun buildImageLoader(
        context: Context,
        okHttpClient: OkHttpClient,
        performanceProfile: DevicePerformanceProfile,
    ): ImageLoader {
        val imageHttpClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "image/webp,image/avif,image/*,*/*;q=0.8")
                    .build()
                val response = chain.proceed(request)

                // Cache 404 responses to prevent repeated failed requests for missing images
                if (response.code == 404) {
                    // Return the modified response with cache headers
                    return@addInterceptor response.newBuilder()
                        .header("Cache-Control", "max-age=3600") // Cache 404s for 1 hour
                        .build()
                }

                response
            }
            .build()

        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, performanceProfile.memoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(performanceProfile.diskCacheSizeMb * BYTES_PER_MEGABYTE)
                    .cleanupCoroutineContext(Dispatchers.IO)
                    .build()
            }
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = imageHttpClient,
                    ),
                )
            }
            // Coil 3.x: crossfade, respectCacheHeaders, allowRgb565, allowHardware
            // are now request-level options, not builder options
            .apply {
                if (com.rpeters.cinefintv.BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }

    @VisibleForTesting
    internal suspend fun clearImageCache(
        imageLoader: ImageLoader,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                SecureLogger.d(TAG, "Image cache cleared")
            }.onFailure { throwable ->
                SecureLogger.e(TAG, "Failed to clear image cache", throwable)
            }
        }
    }
}
