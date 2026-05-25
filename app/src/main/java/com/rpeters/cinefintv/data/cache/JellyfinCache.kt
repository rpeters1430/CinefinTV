package com.rpeters.cinefintv.data.cache

import android.content.Context
import com.rpeters.cinefintv.utils.SecureLogger
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

import com.rpeters.cinefintv.data.common.DispatcherProvider
import com.rpeters.cinefintv.data.repository.RemoteConfigRepository
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/**
 * Intelligent caching system for Jellyfin content to support offline functionality.
 * Part of Phase 2: Enhanced Error Handling & User Experience improvements.
 */
@Singleton
class JellyfinCache @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val remoteConfig: RemoteConfigRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {

    companion object {
        private const val TAG = "JellyfinCache"
        private const val CACHE_DIR = "jellyfin_cache"
        
        // Remote Config Keys
        private const val KEY_MAX_DISK_CACHE_SIZE_MB = "max_disk_cache_size_mb"
        private const val KEY_MAX_MEMORY_CACHE_SIZE = "max_memory_cache_size"
        private const val KEY_MAX_CACHE_AGE_HOURS = "max_cache_age_hours"

        // Local Defaults (used if Remote Config is not yet fetched)
        private const val DEFAULT_MAX_CACHE_SIZE_MB = 100L
        private const val DEFAULT_MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L
        private const val DEFAULT_MAX_MEMORY_CACHE_SIZE = 50

        private const val LIBRARIES_KEY = "libraries"
        private const val CONTINUE_WATCHING_KEY = "continue_watching"
        private const val NEXT_UP_KEY = "next_up"
        private const val RECENTLY_ADDED_MOVIES_KEY = "recently_added_movies"
        private const val RECENTLY_ADDED_EPISODES_KEY = "recently_added_episodes"
        private const val FAVORITES_KEY = "favorites"
    }

    private val maxDiskCacheSizeMb: Long
        get() = remoteConfig.getLong(KEY_MAX_DISK_CACHE_SIZE_MB).takeIf { it > 0 } ?: DEFAULT_MAX_CACHE_SIZE_MB

    private val maxMemoryCacheSize: Int
        get() = remoteConfig.getLong(KEY_MAX_MEMORY_CACHE_SIZE).toInt().takeIf { it > 0 } ?: DEFAULT_MAX_MEMORY_CACHE_SIZE

    private val maxCacheAgeMs: Long
        get() = remoteConfig.getLong(KEY_MAX_CACHE_AGE_HOURS).takeIf { it > 0 }?.let { it * 60 * 60 * 1000L } ?: DEFAULT_MAX_CACHE_AGE_MS

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Thread-safe in-memory cache for quick access with lock-free concurrent LRU eviction
    private val memoryCache = ConcurrentHashMap<String, CacheEntry<*>>()
    private val memoryKeys = ConcurrentLinkedQueue<String>()

    // Cache statistics
    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    // Use lazy initialization for thread-safe cache directory access
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun ensureCacheDir(): File = cacheDir

    init {
        // Clean up old cache entries on initialization
        // Using ApplicationScope for app-wide cache initialization that should complete independently
        applicationScope.launch(dispatchers.io) {
            try {
                // Delay cleanup to avoid I/O contention during cold startup
                kotlinx.coroutines.delay(10_000L)
                // Trigger lazy initialization of cacheDir
                ensureCacheDir()
                cleanupOldEntries()
                updateCacheStats()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                SecureLogger.e(TAG, "Failed to initialize cache", e)
            }
        }
    }

    /**
     * Caches a list of BaseItemDto objects with TTL.
     * Performs I/O operations on background thread.
     */
    suspend fun cacheItems(
        key: String,
        items: List<BaseItemDto>,
        ttlMs: Long? = null,
    ): Boolean {
        val trace = FirebasePerformance.startTrace("cache_items_write")
        trace.putAttribute("cache_key", key)
        trace.putMetric("item_count", items.size.toLong())

        return withContext(dispatchers.io) {
            try {
                val effectiveTtl = ttlMs ?: maxCacheAgeMs
                val cacheData = CacheData(
                    items = items,
                    timestamp = System.currentTimeMillis(),
                    ttlMs = effectiveTtl,
                )

                val cacheEntry = CacheEntry(
                    data = cacheData,
                    expiresAt = System.currentTimeMillis() + effectiveTtl,
                )

                // Store in memory cache with concurrent LRU eviction
                memoryCache[key] = cacheEntry
                memoryKeys.remove(key)
                memoryKeys.add(key)
                while (memoryKeys.size > maxMemoryCacheSize) {
                    val oldestKey = memoryKeys.poll()
                    if (oldestKey != null) {
                        memoryCache.remove(oldestKey)
                        if (BuildConfig.DEBUG) {
                            SecureLogger.d(TAG, "Evicting oldest memory cache entry: $oldestKey")
                        }
                    }
                }

                // Store on disk
                val file = File(ensureCacheDir(), "$key.json")
                file.writeText(json.encodeToString(CacheData.serializer(), cacheData))

                if (BuildConfig.DEBUG) {
                    SecureLogger.d(TAG, "Cached ${items.size} items with key: $key")
                }

                scheduleCacheStatsUpdate()
                trace.stop()
                true
            } catch (e: Exception) {
                trace.stop()
                if (e is CancellationException) throw e
                SecureLogger.e(TAG, "Failed to cache items for key: $key", e)
                false
            }
        }
    }

    /**
     * Retrieves cached items if they exist and are still valid.
     * Performs I/O operations on background thread.
     */
    suspend fun getCachedItems(key: String): List<BaseItemDto>? {
        val trace = FirebasePerformance.startTrace("cache_items_read")
        trace.putAttribute("cache_key", key)

        return withContext(dispatchers.io) {
            try {
                // Check memory cache first (thread-safe, lock-free)
                memoryCache[key]?.let { entry ->
                    if (entry.isValid()) {
                        @Suppress("UNCHECKED_CAST")
                        val cacheData = entry.data as? CacheData
                        if (cacheData != null) {
                            if (BuildConfig.DEBUG) {
                                SecureLogger.d(TAG, "Memory cache hit for key: $key")
                            }
                            // Refresh key usage in LRU queue
                            memoryKeys.remove(key)
                            memoryKeys.add(key)
                            trace.putAttribute("hit_type", "memory")
                            trace.stop()
                            return@withContext cacheData.items
                        }
                    } else {
                        // Remove expired entry
                        memoryCache.remove(key)
                        memoryKeys.remove(key)
                    }
                }

                // Check disk cache
                val file = File(ensureCacheDir(), "$key.json")
                if (file.exists()) {
                    val content = file.readText()
                    if (content.isBlank()) {
                        trace.putAttribute("hit_type", "miss")
                        trace.stop()
                        return@withContext null
                    }

                    val cacheData = json.decodeFromString<CacheData>(content)
                    val isValid = (System.currentTimeMillis() - cacheData.timestamp) < cacheData.ttlMs

                    if (isValid) {
                        // Add back to memory cache with concurrent LRU eviction
                        val cacheEntry = CacheEntry(
                            data = cacheData,
                            expiresAt = cacheData.timestamp + cacheData.ttlMs,
                        )
                        memoryCache[key] = cacheEntry
                        memoryKeys.remove(key)
                        memoryKeys.add(key)
                        while (memoryKeys.size > maxMemoryCacheSize) {
                            val oldestKey = memoryKeys.poll()
                            if (oldestKey != null) {
                                memoryCache.remove(oldestKey)
                                if (BuildConfig.DEBUG) {
                                    SecureLogger.d(TAG, "Evicting oldest memory cache entry: $oldestKey")
                                }
                            }
                        }

                        if (BuildConfig.DEBUG) {
                            SecureLogger.d(TAG, "Disk cache hit for key: $key")
                        }
                        trace.putAttribute("hit_type", "disk")
                        trace.stop()
                        return@withContext cacheData.items
                    } else {
                        // Delete expired file
                        file.delete()
                        if (BuildConfig.DEBUG) {
                            SecureLogger.d(TAG, "Deleted expired cache file for key: $key")
                        }
                    }
                }

                trace.putAttribute("hit_type", "miss")
                trace.stop()
                null
            } catch (e: Exception) {
                trace.stop()
                if (e is CancellationException) throw e
                SecureLogger.w(TAG, "Failed to retrieve cached items for key: $key", e)
                null
            }
        }
    }

    /**
     * Checks if cached data exists and is valid for a given key.
     * Performs I/O operations on background thread.
     */
    suspend fun isCached(key: String): Boolean = withContext(dispatchers.io) {
        try {
            // Check memory cache (thread-safe, lock-free)
            memoryCache[key]?.let { entry ->
                if (entry.isValid()) {
                    memoryKeys.remove(key)
                    memoryKeys.add(key)
                    return@withContext true
                } else {
                    memoryCache.remove(key)
                    memoryKeys.remove(key)
                }
            }

            // Check disk cache
            val file = File(ensureCacheDir(), "$key.json")
            if (file.exists()) {
                val content = file.readText()
                if (content.isBlank()) return@withContext false
                
                val cacheData = json.decodeFromString<CacheData>(content)
                val isValid = (System.currentTimeMillis() - cacheData.timestamp) < cacheData.ttlMs
                return@withContext isValid
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SecureLogger.w(TAG, "Failed to check cache status for key: $key", e)
        }

        false
    }

    /**
     * Invalidates cache for a specific key.
     */
    suspend fun invalidateCache(key: String) = withContext(dispatchers.io) {
        memoryCache.remove(key)
        memoryKeys.remove(key)
        val file = File(ensureCacheDir(), "$key.json")
        if (file.exists()) {
            file.delete()
            if (BuildConfig.DEBUG) {
                SecureLogger.d(TAG, "Invalidated cache for key: $key")
            }
        }
        scheduleCacheStatsUpdate()
    }

    /**
     * Clears all cached data.
     */
    suspend fun clearAllCache() = withContext(dispatchers.io) {
        memoryCache.clear()
        memoryKeys.clear()

        ensureCacheDir().listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".json")) {
                file.delete()
            }
        }

        if (BuildConfig.DEBUG) {
            SecureLogger.d(TAG, "Cleared all cache")
        }
        scheduleCacheStatsUpdate()
    }

    /**
     * Gets the size of cached data in bytes.
     */
    suspend fun getCacheSizeBytes(): Long = withContext(dispatchers.io) {
        try {
            ensureCacheDir().listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Cleans up expired cache entries.
     * Performs I/O operations on background thread.
     */
    private suspend fun cleanupOldEntries() {
        withContext(dispatchers.io) {
            try {
                val currentTime = System.currentTimeMillis()

                // Clean memory cache
                val expiredKeys = memoryCache.entries
                    .filter { !it.value.isValid() }
                    .map { it.key }

                expiredKeys.forEach { key ->
                    memoryCache.remove(key)
                    memoryKeys.remove(key)
                }

                // Clean disk cache
                ensureCacheDir().listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".json")) {
                        try {
                            val cacheData = json.decodeFromString<CacheData>(file.readText())
                            val isExpired = (currentTime - cacheData.timestamp) >= cacheData.ttlMs

                            if (isExpired) {
                                file.delete()
                                if (BuildConfig.DEBUG) {
                                    SecureLogger.d(TAG, "Deleted expired cache file: ${file.name}")
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        }
                    }
                }

                // Check if we need to free up space
                val cacheSize = getCacheSizeBytes()
                val maxCacheSizeBytes = maxDiskCacheSizeMb * 1024 * 1024

                if (cacheSize > maxCacheSizeBytes) {
                    evictOldestEntries(maxCacheSizeBytes)
                }

                if (BuildConfig.DEBUG) {
                    SecureLogger.d(TAG, "Cache cleanup completed. Removed ${expiredKeys.size} expired entries")
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error during cache cleanup", e)
            }
        }
    }

    /**
     * Evicts oldest cache entries to stay within size limit.
     */
    private suspend fun evictOldestEntries(maxSizeBytes: Long) {
        try {
            val files = ensureCacheDir().listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedBy { it.lastModified() } // Oldest first
                ?: return

            var currentSize = files.sumOf { it.length() }
            var deletedCount = 0

            for (file in files) {
                if (currentSize <= maxSizeBytes) break

                currentSize -= file.length()
                file.delete()
                deletedCount++

                // Remove from memory cache too
                val key = file.nameWithoutExtension
                memoryCache.remove(key)
                memoryKeys.remove(key)
            }

            if (BuildConfig.DEBUG && deletedCount > 0) {
                SecureLogger.d(TAG, "Evicted $deletedCount cache entries to stay within size limit")
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Updates cache statistics.
     */
    private suspend fun updateCacheStats() {
        try {
            val diskEntries = ensureCacheDir().listFiles()
                ?.count { it.isFile && it.name.endsWith(".json") } ?: 0

            val memoryEntries = memoryCache.size
            val totalSizeBytes = getCacheSizeBytes()
            val totalSizeMB = totalSizeBytes / (1024.0 * 1024.0)

            _cacheStats.update {
                CacheStats(
                    totalEntries = diskEntries,
                    memoryEntries = memoryEntries,
                    totalSizeBytes = totalSizeBytes,
                    totalSizeMB = totalSizeMB,
                )
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun scheduleCacheStatsUpdate() {
        applicationScope.launch(dispatchers.io) {
            try {
                updateCacheStats()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLogger.w(TAG, "Failed to update cache stats", e)
            }
        }
    }

    /**
     * Convenient methods for common cache operations.
     */

    suspend fun cacheRecentlyAddedMovies(items: List<BaseItemDto>) {
        cacheItems(RECENTLY_ADDED_MOVIES_KEY, items, ttlMs = 30 * 60 * 1000L) // 30 minutes
    }

    suspend fun getCachedRecentlyAddedMovies(): List<BaseItemDto>? {
        return getCachedItems(RECENTLY_ADDED_MOVIES_KEY)
    }

    suspend fun cacheRecentlyAddedEpisodes(items: List<BaseItemDto>) {
        cacheItems(RECENTLY_ADDED_EPISODES_KEY, items, ttlMs = 30 * 60 * 1000L) // 30 minutes
    }

    suspend fun getCachedRecentlyAddedEpisodes(): List<BaseItemDto>? {
        return getCachedItems(RECENTLY_ADDED_EPISODES_KEY)
    }

    suspend fun cacheContinueWatching(items: List<BaseItemDto>) {
        cacheItems(CONTINUE_WATCHING_KEY, items, ttlMs = 15 * 60 * 1000L) // 15 minutes
    }

    suspend fun getCachedContinueWatching(): List<BaseItemDto>? {
        return getCachedItems(CONTINUE_WATCHING_KEY)
    }

    suspend fun cacheNextUp(items: List<BaseItemDto>) {
        cacheItems(NEXT_UP_KEY, items, ttlMs = 15 * 60 * 1000L) // 15 minutes
    }

    suspend fun getCachedNextUp(): List<BaseItemDto>? {
        return getCachedItems(NEXT_UP_KEY)
    }

    suspend fun cacheLibraries(items: List<BaseItemDto>) {
        cacheItems(LIBRARIES_KEY, items, ttlMs = 60 * 60 * 1000L) // 1 hour
    }

    suspend fun getCachedLibraries(): List<BaseItemDto>? {
        return getCachedItems(LIBRARIES_KEY)
    }

    suspend fun cacheFavorites(items: List<BaseItemDto>) {
        cacheItems(FAVORITES_KEY, items, ttlMs = 10 * 60 * 1000L) // 10 minutes
    }

    suspend fun getCachedFavorites(): List<BaseItemDto>? {
        return getCachedItems(FAVORITES_KEY)
    }

    /**
     * Invalidates all caches whose content is invalidated by a watch-state change.
     * Call this after marking an item watched or unwatched to prevent stale data
     * from being served to the home screen and Next Up rows.
     */
    suspend fun invalidateWatchStateCaches() {
        invalidateCache(CONTINUE_WATCHING_KEY)
        invalidateCache(NEXT_UP_KEY)
    }
}

/**
 * Data classes for cache implementation.
 */

@Serializable
private data class CacheData(
    val items: List<BaseItemDto>,
    val timestamp: Long,
    val ttlMs: Long,
)

private data class CacheEntry<T>(
    val data: T,
    val expiresAt: Long,
) {
    fun isValid(): Boolean = System.currentTimeMillis() < expiresAt
}

data class CacheStats(
    val totalEntries: Int = 0,
    val memoryEntries: Int = 0,
    val totalSizeBytes: Long = 0L,
    val totalSizeMB: Double = 0.0,
)
