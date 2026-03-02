package com.rpeters.cinefintv.data.cache

import android.util.Log
import android.util.LruCache
import com.rpeters.cinefintv.core.util.PerformanceMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimized cache manager with memory management, automatic cleanup, and performance monitoring.
 */
@Singleton
class OptimizedCacheManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cache configurations
    private val maxMemoryMB = (Runtime.getRuntime().maxMemory() / 1024 / 1024).toInt()
    private val cacheSize = maxMemoryMB / 4 // Use 25% of available memory

    // LRU Cache for BaseItemDto objects
    private val itemCache = LruCache<String, CacheEntry<BaseItemDto>>(cacheSize * 50) // ~50 items per MB

    // Cache for lists of items
    private val listCache = LruCache<String, CacheEntry<List<BaseItemDto>>>(cacheSize / 2) // Larger objects

    // Cache for strings (URLs, metadata)
    private val stringCache = LruCache<String, CacheEntry<String>>(cacheSize * 100) // ~100 strings per MB

    // Cache metadata tracking
    private val cacheMetrics = ConcurrentHashMap<String, CacheMetrics>()

    // Cache cleanup configuration
    private val cleanupIntervalMs = 5 * 60 * 1000L // 5 minutes

    companion object {
        private const val DEFAULT_TTL = 30 * 60 * 1000L // 30 minutes default TTL
    }

    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis(),
        val ttl: Long = DEFAULT_TTL,
        val accessCount: Int = 0,
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - timestamp > ttl

        fun withAccess(): CacheEntry<T> = copy(accessCount = accessCount + 1)
    }

    data class CacheMetrics(
        var hits: Long = 0,
        var misses: Long = 0,
        var evictions: Long = 0,
        var size: Int = 0,
    ) {
        val hitRate: Float
            get() = if (hits + misses > 0) hits.toFloat() / (hits + misses) else 0f
    }

    init {
        startPeriodicCleanup()
        Log.i("OptimizedCacheManager", "Initialized with ${cacheSize}MB cache size (${maxMemoryMB}MB total memory)")
    }

    /**
     * Get item from cache with automatic metrics tracking.
     */
    fun <T> get(key: String, cache: LruCache<String, CacheEntry<T>>, cacheType: String): T? {
        val metrics = cacheMetrics.getOrPut(cacheType) { CacheMetrics() }

        val entry = cache.get(key)
        return if (entry != null && !entry.isExpired) {
            // Cache hit
            cache.put(key, entry.withAccess())
            metrics.hits++
            metrics.size = cache.size()
            entry.data
        } else {
            // Cache miss or expired
            if (entry != null) {
                cache.remove(key) // Remove expired entry
                metrics.evictions++
            }
            metrics.misses++
            metrics.size = cache.size()
            null
        }
    }

    /**
     * Put item in cache with TTL.
     */
    fun <T> put(
        key: String,
        data: T,
        cache: LruCache<String, CacheEntry<T>>,
        cacheType: String,
        ttl: Long = DEFAULT_TTL,
    ) {
        val entry = CacheEntry(data, ttl = ttl)
        cache.put(key, entry)

        val metrics = cacheMetrics.getOrPut(cacheType) { CacheMetrics() }
        metrics.size = cache.size()
    }

    // Public API methods
    fun getItem(key: String): BaseItemDto? = get(key, itemCache, "BaseItemDto")
    fun putItem(key: String, item: BaseItemDto, ttl: Long = DEFAULT_TTL) = put(key, item, itemCache, "BaseItemDto", ttl)

    fun getList(key: String): List<BaseItemDto>? = get(key, listCache, "List")
    fun putList(key: String, list: List<BaseItemDto>, ttl: Long = DEFAULT_TTL) = put(key, list, listCache, "List", ttl)

    fun getString(key: String): String? = get(key, stringCache, "String")
    fun putString(key: String, value: String, ttl: Long = DEFAULT_TTL) = put(key, value, stringCache, "String", ttl)

    /**
     * Clear specific cache type.
     */
    fun clearItems() {
        itemCache.evictAll()
        updateMetrics("BaseItemDto") {
            size = 0
            evictions++
        }
    }

    fun clearLists() {
        listCache.evictAll()
        updateMetrics("List") {
            size = 0
            evictions++
        }
    }

    fun clearStrings() {
        stringCache.evictAll()
        updateMetrics("String") {
            size = 0
            evictions++
        }
    }

    /**
     * Clear all caches.
     */
    fun clearAll() {
        clearItems()
        clearLists()
        clearStrings()
        Log.i("OptimizedCacheManager", "All caches cleared")
    }

    /**
     * Get cache statistics for monitoring.
     */
    fun getCacheStats(): Map<String, CacheMetrics> = cacheMetrics.toMap()

    /**
     * Log cache performance metrics.
     */
    fun logCacheStats() {
        cacheMetrics.forEach { (type, metrics) ->
            Log.d(
                "OptimizedCacheManager",
                "$type Cache - Size: ${metrics.size}, " +
                    "Hit Rate: ${String.format("%.1f", metrics.hitRate * 100)}%, " +
                    "Hits: ${metrics.hits}, Misses: ${metrics.misses}, " +
                    "Evictions: ${metrics.evictions}",
            )
        }

        PerformanceMonitor.logMemoryUsage("Cache Stats")
    }

    /**
     * Force cache cleanup and optimization.
     */
    fun optimizeCaches() {
        val beforeMemory = PerformanceMonitor.getMemoryInfo()

        // Remove expired entries
        cleanupExpiredEntries()

        // Check if memory pressure requires more aggressive cleanup
        if (PerformanceMonitor.checkMemoryPressure()) {
            // Remove least recently used items more aggressively
            trimCachesToSize(itemCache, itemCache.maxSize() / 2)
            trimCachesToSize(listCache, listCache.maxSize() / 2)
            trimCachesToSize(stringCache, stringCache.maxSize() / 2)

            Log.w("OptimizedCacheManager", "Aggressive cache trimming due to memory pressure")
        }

        val afterMemory = PerformanceMonitor.getMemoryInfo()
        val freedMB = beforeMemory.usedMemoryMB - afterMemory.usedMemoryMB

        Log.i("OptimizedCacheManager", "Cache optimization completed. Memory freed: ${freedMB}MB")
    }

    /**
     * Start periodic cleanup task.
     * Runs while the scope is active and stops gracefully on cancellation.
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            // Use isActive instead of while(true) for proper cancellation support
            while (isActive) {
                delay(cleanupIntervalMs)

                try {
                    cleanupExpiredEntries()
                    logCacheStats()

                    // Optimize if memory usage is high
                    if (PerformanceMonitor.checkMemoryPressure()) {
                        optimizeCaches()
                    }
                } catch (e: CancellationException) {
                    // Re-throw to propagate cancellation properly
                    Log.d("OptimizedCacheManager", "Periodic cleanup cancelled")
                    throw e
                }
            }
        }
    }

    /**
     * Shutdown the cache manager and cancel all background operations.
     * Call this when the cache is no longer needed to prevent resource leaks.
     */
    fun shutdown() {
        Log.i("OptimizedCacheManager", "Shutting down cache manager")
        scope.cancel()
        // Clear all caches
        itemCache.evictAll()
        listCache.evictAll()
        stringCache.evictAll()
        cacheMetrics.clear()
    }

    /**
     * Remove expired entries from all caches.
     */
    private fun cleanupExpiredEntries() {
        val currentTime = System.currentTimeMillis()
        var expiredCount = 0

        // Cleanup item cache
        val itemKeys = itemCache.snapshot().keys.toList()
        itemKeys.forEach { key ->
            val entry = itemCache.get(key)
            if (entry?.isExpired == true) {
                itemCache.remove(key)
                expiredCount++
            }
        }

        // Cleanup list cache
        val listKeys = listCache.snapshot().keys.toList()
        listKeys.forEach { key ->
            val entry = listCache.get(key)
            if (entry?.isExpired == true) {
                listCache.remove(key)
                expiredCount++
            }
        }

        // Cleanup string cache
        val stringKeys = stringCache.snapshot().keys.toList()
        stringKeys.forEach { key ->
            val entry = stringCache.get(key)
            if (entry?.isExpired == true) {
                stringCache.remove(key)
                expiredCount++
            }
        }

        if (expiredCount > 0) {
            Log.d("OptimizedCacheManager", "Cleaned up $expiredCount expired cache entries")
        }
    }

    /**
     * Trim cache to specific size.
     */
    private fun <T> trimCachesToSize(cache: LruCache<String, CacheEntry<T>>, targetSize: Int) {
        while (cache.size() > targetSize) {
            // LruCache will automatically remove least recently used items
            cache.trimToSize(targetSize)
        }
    }

    /**
     * Update metrics for a cache type.
     */
    private fun updateMetrics(type: String, update: CacheMetrics.() -> Unit) {
        val metrics = cacheMetrics.getOrPut(type) { CacheMetrics() }
        metrics.update()
    }
}
