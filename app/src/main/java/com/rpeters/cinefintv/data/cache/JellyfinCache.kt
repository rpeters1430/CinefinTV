package com.rpeters.cinefintv.data.cache

import android.content.Context
import android.util.Log
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent caching system for Jellyfin content to support offline functionality.
 * Part of Phase 2: Enhanced Error Handling & User Experience improvements.
 */
@Singleton
class JellyfinCache @Inject constructor(
    private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    companion object {
        private const val TAG = "JellyfinCache"
        private const val CACHE_DIR = "jellyfin_cache"
        private const val MAX_CACHE_SIZE_MB = 100L // 100 MB cache limit
        private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_MEMORY_CACHE_SIZE = 50 // Maximum number of entries in memory cache
        private const val RECENTLY_ADDED_KEY = "recently_added"
        private const val LIBRARIES_KEY = "libraries"
        private const val FAVORITES_KEY = "favorites"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // In-memory cache for quick access with LRU eviction
    private val memoryCache = object : LinkedHashMap<String, CacheEntry<*>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<*>>?): Boolean {
            val shouldRemove = size > MAX_MEMORY_CACHE_SIZE
            if (shouldRemove && BuildConfig.DEBUG) {
                Log.d(TAG, "Evicting oldest memory cache entry: ${eldest?.key}")
            }
            return shouldRemove
        }
    }

    // Cache statistics
    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    // Use lateinit to avoid file I/O during lazy initialization
    // Directory creation happens on background thread during init
    private lateinit var cacheDir: File

    private fun ensureCacheDir(): File {
        if (!::cacheDir.isInitialized) {
            cacheDir = File(context.cacheDir, CACHE_DIR)
        }
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    init {
        // Clean up old cache entries on initialization
        // Using ApplicationScope for app-wide cache initialization that should complete independently
        // This is a singleton called at app startup and must complete even if the caller is destroyed
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Initialize cache directory on background thread
                cacheDir = File(context.cacheDir, CACHE_DIR).apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                cleanupOldEntries()
                updateCacheStats()
            } catch (e: CancellationException) {
                throw e
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
        ttlMs: Long = MAX_CACHE_AGE_MS,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val cacheData = CacheData(
                    items = items,
                    timestamp = System.currentTimeMillis(),
                    ttlMs = ttlMs,
                )

                val cacheEntry = CacheEntry(
                    data = cacheData,
                    expiresAt = System.currentTimeMillis() + ttlMs,
                )

                // Store in memory cache (synchronized for thread safety)
                synchronized(memoryCache) {
                    memoryCache[key] = cacheEntry
                }

                // Store on disk (ensure cache directory exists first)
                val file = File(ensureCacheDir(), "$key.json")
                file.writeText(json.encodeToString(CacheData.serializer(), cacheData))

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Cached ${items.size} items with key: $key")
                }

                updateCacheStats()
                true
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Retrieves cached items if they exist and are still valid.
     * Performs I/O operations on background thread.
     */
    suspend fun getCachedItems(key: String): List<BaseItemDto>? {
        return withContext(Dispatchers.IO) {
            try {
                // Check memory cache first (synchronized for thread safety)
                synchronized(memoryCache) {
                    memoryCache[key]?.let { entry ->
                        if (entry.isValid()) {
                            @Suppress("UNCHECKED_CAST")
                            val cacheData = entry.data as? CacheData
                            if (cacheData != null) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Memory cache hit for key: $key")
                                }
                                return@withContext cacheData.items
                            }
                        } else {
                            // Remove expired entry
                            memoryCache.remove(key)
                        }
                    }
                }

                // Check disk cache (ensure cache directory exists first)
                val file = File(ensureCacheDir(), "$key.json")
                if (file.exists()) {
                    val cacheData = json.decodeFromString<CacheData>(file.readText())
                    val isValid = (System.currentTimeMillis() - cacheData.timestamp) < cacheData.ttlMs

                    if (isValid) {
                        // Add back to memory cache (synchronized for thread safety)
                        val cacheEntry = CacheEntry(
                            data = cacheData,
                            expiresAt = cacheData.timestamp + cacheData.ttlMs,
                        )
                        synchronized(memoryCache) {
                            memoryCache[key] = cacheEntry
                        }

                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Disk cache hit for key: $key")
                        }
                        return@withContext cacheData.items
                    } else {
                        // Delete expired file
                        file.delete()
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Deleted expired cache file for key: $key")
                        }
                    }
                }

                null
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Checks if cached data exists and is valid for a given key.
     * Performs I/O operations on background thread.
     */
    suspend fun isCached(key: String): Boolean = withContext(Dispatchers.IO) {
        // Check memory cache
        memoryCache[key]?.let { entry ->
            if (entry.isValid()) {
                return@withContext true
            } else {
                memoryCache.remove(key)
            }
        }

        // Check disk cache (ensure cache directory exists first)
        val file = File(ensureCacheDir(), "$key.json")
        if (file.exists()) {
            try {
                val cacheData = json.decodeFromString<CacheData>(file.readText())
                val isValid = (System.currentTimeMillis() - cacheData.timestamp) < cacheData.ttlMs
                return@withContext isValid
            } catch (e: CancellationException) {
                throw e
            }
        }

        false
    }

    /**
     * Invalidates cache for a specific key.
     */
    suspend fun invalidateCache(key: String) = withContext(Dispatchers.IO) {
        synchronized(memoryCache) {
            memoryCache.remove(key)
        }
        val file = File(ensureCacheDir(), "$key.json")
        if (file.exists()) {
            file.delete()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Invalidated cache for key: $key")
            }
        }
        updateCacheStats()
    }

    /**
     * Clears all cached data.
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        synchronized(memoryCache) {
            memoryCache.clear()
        }

        ensureCacheDir().listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".json")) {
                file.delete()
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Cleared all cache")
        }
        updateCacheStats()
    }

    /**
     * Gets the size of cached data in bytes.
     */
    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()

                // Clean memory cache (synchronized for thread safety)
                val expiredKeys = synchronized(memoryCache) {
                    memoryCache.entries
                        .filter { !it.value.isValid() }
                        .map { it.key }
                }

                synchronized(memoryCache) {
                    expiredKeys.forEach { key ->
                        memoryCache.remove(key)
                    }
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
                                    Log.d(TAG, "Deleted expired cache file: ${file.name}")
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        }
                    }
                }

                // Check if we need to free up space
                val cacheSize = getCacheSizeBytes()
                val maxCacheSizeBytes = MAX_CACHE_SIZE_MB * 1024 * 1024

                if (cacheSize > maxCacheSizeBytes) {
                    evictOldestEntries(maxCacheSizeBytes)
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Cache cleanup completed. Removed ${expiredKeys.size} expired entries")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during cache cleanup", e)
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

                // Remove from memory cache too (synchronized for thread safety)
                val key = file.nameWithoutExtension
                synchronized(memoryCache) {
                    memoryCache.remove(key)
                }
            }

            if (BuildConfig.DEBUG && deletedCount > 0) {
                Log.d(TAG, "Evicted $deletedCount cache entries to stay within size limit")
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

            val memoryEntries = synchronized(memoryCache) {
                memoryCache.size
            }
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

    /**
     * Convenient methods for common cache operations.
     */

    suspend fun cacheRecentlyAdded(items: List<BaseItemDto>) {
        cacheItems(RECENTLY_ADDED_KEY, items, ttlMs = 30 * 60 * 1000L) // 30 minutes
    }

    suspend fun getCachedRecentlyAdded(): List<BaseItemDto>? {
        return getCachedItems(RECENTLY_ADDED_KEY)
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
