package com.rpeters.cinefintv.core.util

import android.os.Build
import android.os.Debug
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis

/**
 * Phase 4: Performance Monitoring System
 *
 * Real-time performance analytics and automatic optimization
 * Monitors app performance and provides insights for optimization
 */
@Singleton
class PerformanceMonitor @Inject constructor() {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val SAMPLE_INTERVAL_MS = 5000L // 5 seconds
        private const val MAX_PERFORMANCE_SAMPLES = 100
        private const val MAX_OPERATION_SAMPLES = 50 // Limit operation timing samples
        private const val MONITORING_ENABLED_ONLY_IN_DEBUG = true // Only run in debug builds
        private const val BYTES_IN_MEGABYTE = 1024 * 1024

        /**
         * Memory information data class
         */
        data class MemoryInfo(
            val usedMemoryMB: Long,
            val totalMemoryMB: Long,
            val freeMemoryMB: Long,
            val maxMemoryMB: Long,
            val usagePercentage: Float,
        )

        data class PerformanceMetrics(
            val memory: MemoryInfo,
            val renderTimeMs: Long,
            val frameDrops: Int,
            val timestamp: Long = System.currentTimeMillis(),
        )

        /**
         * Get current memory usage information (static utility method)
         */
        @JvmStatic
        fun getMemoryInfo(): MemoryInfo {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory

            val usedMemoryMB = usedMemory / BYTES_IN_MEGABYTE
            val totalMemoryMB = totalMemory / BYTES_IN_MEGABYTE
            val freeMemoryMB = freeMemory / BYTES_IN_MEGABYTE
            val maxMemoryMB = maxMemory / BYTES_IN_MEGABYTE
            val usagePercentage = (usedMemory.toFloat() / maxMemory.toFloat()) * 100f

            return MemoryInfo(
                usedMemoryMB = usedMemoryMB,
                totalMemoryMB = totalMemoryMB,
                freeMemoryMB = freeMemoryMB,
                maxMemoryMB = maxMemoryMB,
                usagePercentage = usagePercentage,
            )
        }

        /**
         * Log memory usage with optional custom message
         */
        @JvmStatic
        fun logMemoryUsage(message: String = "Memory Usage") {
            val memoryInfo = getMemoryInfo()
            if (BuildConfig.DEBUG) {
                SecureLogger.v(
                    TAG,
                    "$message - Used: ${memoryInfo.usedMemoryMB}MB (${String.format("%.1f", memoryInfo.usagePercentage)}%), " +
                        "Free: ${memoryInfo.freeMemoryMB}MB, Max: ${memoryInfo.maxMemoryMB}MB",
                )
            }
        }

        /**
         * Check if memory usage is high and suggest garbage collection
         */
        @JvmStatic
        fun checkMemoryPressure(): Boolean {
            val memoryInfo = getMemoryInfo()
            val isHighUsage = memoryInfo.usagePercentage > 80f

            if (isHighUsage && BuildConfig.DEBUG) {
                SecureLogger.w(TAG, "High memory usage detected: ${String.format("%.1f", memoryInfo.usagePercentage)}%")
            }

            return isHighUsage
        }

        /**
         * Force garbage collection and log memory before/after (use sparingly)
         * Note: Calling Thread.sleep in production is not recommended
         */
        @JvmStatic
        suspend fun forceGarbageCollection(reason: String) {
            val beforeMemory = getMemoryInfo()
            if (BuildConfig.DEBUG) {
                SecureLogger.v(TAG, "GC Request ($reason) - Before: ${beforeMemory.usedMemoryMB}MB")
            }

            System.gc()

            // Use coroutine delay instead of Thread.sleep
            delay(100)

            val afterMemory = getMemoryInfo()
            val freedMB = beforeMemory.usedMemoryMB - afterMemory.usedMemoryMB
            if (BuildConfig.DEBUG) {
                SecureLogger.v(TAG, "GC Request ($reason) - After: ${afterMemory.usedMemoryMB}MB (Freed: ${freedMB}MB)")
            }
        }

        /**
         * Measure execution time of a block of code.
         * Note: Non-inline to avoid public-API inline function restrictions
         */
        @JvmStatic
        fun <T> measureExecutionTime(tag: String, block: () -> T): T {
            val start = System.nanoTime()
            val result = block()
            val executionTime = (System.nanoTime() - start) / 1_000_000
            if (BuildConfig.DEBUG) {
                SecureLogger.v(TAG, "$tag executed in ${executionTime}ms")
            }
            return result
        }

        /**
         * Get native heap information (API 23+).
         */
        @JvmStatic
        fun getNativeHeapInfo(): String? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val nativeHeapSize = Debug.getNativeHeapSize() / BYTES_IN_MEGABYTE
                val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / BYTES_IN_MEGABYTE
                val nativeHeapFree = Debug.getNativeHeapFreeSize() / BYTES_IN_MEGABYTE
                "Native Heap - Size: ${nativeHeapSize}MB, Allocated: ${nativeHeapAllocated}MB, Free: ${nativeHeapFree}MB"
            } else {
                null
            }
        }

        /**
         * Performance metrics collection helper.
         */
        @JvmStatic
        fun collectPerformanceMetrics(renderTimeMs: Long = 0, frameDrops: Int = 0): PerformanceMetrics {
            return PerformanceMetrics(
                memory = getMemoryInfo(),
                renderTimeMs = renderTimeMs,
                frameDrops = frameDrops,
            )
        }
    }

    // Performance metrics
    private val _frameRate = MutableStateFlow(60f)
    val frameRate: StateFlow<Float> = _frameRate.asStateFlow()

    private val _memoryUsage = MutableStateFlow(0L)
    val memoryUsage: StateFlow<Long> = _memoryUsage.asStateFlow()

    private val _networkLatency = MutableStateFlow(0L)
    val networkLatency: StateFlow<Long> = _networkLatency.asStateFlow()

    private val _performanceScore = MutableStateFlow(100f)
    val performanceScore: StateFlow<Float> = _performanceScore.asStateFlow()

    // Device capabilities
    private val _deviceTier = MutableStateFlow(DeviceTier.HIGH_END)
    val deviceTier: StateFlow<DeviceTier> = _deviceTier.asStateFlow()

    // Performance history
    private val performanceSamples = mutableListOf<PerformanceSample>()
    private val operationTimes = mutableMapOf<String, MutableList<Long>>()
    private val operationTimesLock = Any()

    init {
        determineDeviceTier()
    }

    /**
     * Device performance tiers for adaptive UI
     */
    enum class DeviceTier {
        LOW_END, // < 4GB RAM, older CPU
        MID_RANGE, // 4-8GB RAM, decent CPU
        HIGH_END, // > 8GB RAM, powerful CPU
    }

    /**
     * Performance sample data class
     */
    data class PerformanceSample(
        val timestamp: Long,
        val frameRate: Float,
        val memoryUsage: Long,
        val cpuUsage: Float,
        val networkLatency: Long,
    )

    /**
     * Measure operation performance
     */
    suspend fun <T> measureOperation(
        operationName: String,
        operation: suspend () -> T,
    ): T {
        val result: T
        val executionTime = measureTimeMillis {
            result = operation()
        }

        recordOperationTime(operationName, executionTime)

        if (BuildConfig.DEBUG) {
            SecureLogger.d(TAG, "Operation '$operationName' took ${executionTime}ms")
        }

        return result
    }

    /**
     * Record operation execution time
     */
    fun recordOperationTime(operationName: String, timeMs: Long) {
        synchronized(operationTimesLock) {
            val times = operationTimes.getOrPut(operationName) { mutableListOf() }
            times.add(timeMs)

            // Keep only recent samples to prevent memory leaks
            if (times.size > MAX_OPERATION_SAMPLES) {
                times.removeAt(0)
            }

            // Clean up operation times map if it gets too large
            if (operationTimes.size > 20) {
                // Remove least recently used operations
                val sortedByUsage = operationTimes.entries.sortedBy { it.value.size }
                sortedByUsage.firstOrNull()?.key?.let { operationTimes.remove(it) }
            }
        }
    }

    /**
     * Get average operation time
     */
    fun getAverageOperationTime(operationName: String): Long {
        val times = synchronized(operationTimesLock) {
            operationTimes[operationName]?.toList()
        } ?: return 0L
        return if (times.isNotEmpty()) times.average().toLong() else 0L
    }

    /**
     * Start continuous performance monitoring (only in debug builds)
     */
    suspend fun startMonitoring() {
        if (!MONITORING_ENABLED_ONLY_IN_DEBUG || BuildConfig.DEBUG) {
            while (coroutineContext.isActive) {
                updatePerformanceMetrics()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        // Memory usage
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        _memoryUsage.value = usedMemory

        // Calculate performance score
        calculatePerformanceScore()

        // Store sample
        val sample = PerformanceSample(
            timestamp = System.currentTimeMillis(),
            frameRate = _frameRate.value,
            memoryUsage = usedMemory,
            cpuUsage = 0f, // Would need native code to get real CPU usage
            networkLatency = _networkLatency.value,
        )

        addPerformanceSample(sample)

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                TAG,
                "Performance update - Memory: ${usedMemory / BYTES_IN_MEGABYTE}MB, Score: ${_performanceScore.value}",
            )
        }
    }

    /**
     * Add performance sample
     */
    private fun addPerformanceSample(sample: PerformanceSample) {
        performanceSamples.add(sample)

        // Keep only recent samples
        if (performanceSamples.size > MAX_PERFORMANCE_SAMPLES) {
            performanceSamples.removeAt(0)
        }
    }

    /**
     * Calculate overall performance score (0-100)
     */
    private fun calculatePerformanceScore() {
        var score = 100f

        // Memory pressure penalty
        val runtime = Runtime.getRuntime()
        val memoryUsageRatio = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / runtime.maxMemory()
        if (memoryUsageRatio > 0.8f) {
            score -= (memoryUsageRatio - 0.8f) * 100f // Penalty for high memory usage
        }

        // Frame rate penalty
        if (_frameRate.value < 30f) {
            score -= (30f - _frameRate.value) * 2f
        }

        // Network latency penalty
        if (_networkLatency.value > 1000L) {
            score -= (_networkLatency.value - 1000L) / 100f
        }

        _performanceScore.value = score.coerceIn(0f, 100f)
    }

    /**
     * Determine device performance tier
     */
    private fun determineDeviceTier() {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / BYTES_IN_MEGABYTE // MB

        val tier = when {
            maxMemory > 6144 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> DeviceTier.HIGH_END
            maxMemory > 3072 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> DeviceTier.MID_RANGE
            else -> DeviceTier.LOW_END
        }

        _deviceTier.value = tier

        if (BuildConfig.DEBUG) {
            SecureLogger.d(TAG, "Device tier determined: $tier (${maxMemory}MB RAM)")
        }
    }

    /**
     * Get performance recommendations
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val score = _performanceScore.value

        if (score < 70f) {
            recommendations.add("Consider reducing image quality for better performance")
        }

        if (_memoryUsage.value > Runtime.getRuntime().maxMemory() * 0.8) {
            recommendations.add("High memory usage detected - consider clearing cache")
        }

        if (_networkLatency.value > 2000L) {
            recommendations.add("Slow network detected - enable offline mode")
        }

        val avgLoadTime = getAverageOperationTime("loadLibraries")
        if (avgLoadTime > 3000L) {
            recommendations.add("Library loading is slow - consider pagination")
        }

        return recommendations
    }

    /**
     * Get optimal settings based on device tier
     */
    fun getOptimalSettings(): Map<String, Any> {
        return when (_deviceTier.value) {
            DeviceTier.LOW_END -> mapOf(
                "imageQuality" to "low",
                "maxConcurrentLoads" to 2,
                "enableAnimations" to false,
                "prefetchEnabled" to false,
                "maxCacheSize" to 50 * 1024 * 1024, // 50MB
            )
            DeviceTier.MID_RANGE -> mapOf(
                "imageQuality" to "medium",
                "maxConcurrentLoads" to 4,
                "enableAnimations" to true,
                "prefetchEnabled" to true,
                "maxCacheSize" to 100 * 1024 * 1024, // 100MB
            )
            DeviceTier.HIGH_END -> mapOf(
                "imageQuality" to "high",
                "maxConcurrentLoads" to 8,
                "enableAnimations" to true,
                "prefetchEnabled" to true,
                "maxCacheSize" to 200 * 1024 * 1024, // 200MB
            )
        }
    }

    /**
     * Record network latency
     */
    fun recordNetworkLatency(latencyMs: Long) {
        _networkLatency.value = latencyMs
    }

    /**
     * Record frame rate (would be called by UI measuring system)
     */
    fun recordFrameRate(fps: Float) {
        _frameRate.value = fps
    }

    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): Map<String, Any> {
        return mapOf(
            "currentScore" to _performanceScore.value,
            "deviceTier" to _deviceTier.value.name,
            "memoryUsageMB" to _memoryUsage.value / BYTES_IN_MEGABYTE,
            "frameRate" to _frameRate.value,
            "networkLatency" to _networkLatency.value,
            "totalSamples" to performanceSamples.size,
            "recommendations" to getPerformanceRecommendations(),
        )
    }
}

/**
 * Composable for monitoring UI performance via a shared monitor instance.
 */
@Composable
fun PerformanceTracker(
    operationName: String,
    performanceMonitor: PerformanceMonitor,
    content: @Composable () -> Unit,
) {
    var startTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        startTime = System.currentTimeMillis()
    }

    content()

    LaunchedEffect(Unit) {
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        if (duration > 0) {
            // Record composition time
            performanceMonitor.recordOperationTime(operationName, duration)
        }
    }
}

/**
 * Composable for monitoring performance during composition.
 */
@Composable
fun PerformanceMetricsTracker(
    enabled: Boolean = true,
    intervalMs: Long = 10000, // 10 seconds
    onMetricsCollected: (PerformanceMonitor.Companion.PerformanceMetrics) -> Unit = {},
) {
    var lastCollectionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect

        while (true) {
            delay(intervalMs)

            val currentTime = System.currentTimeMillis()
            val renderTime = currentTime - lastCollectionTime
            lastCollectionTime = currentTime

            val metrics = PerformanceMonitor.collectPerformanceMetrics(renderTime)
            onMetricsCollected(metrics)

            // Log metrics only if verbose logging is enabled
            if (BuildConfig.DEBUG) {
                SecureLogger.v(
                    "PerformanceMetricsTracker",
                    "Memory: ${metrics.memory.usedMemoryMB}MB (${String.format("%.1f", metrics.memory.usagePercentage)}%), " +
                        "Render: ${metrics.renderTimeMs}ms",
                )
            }

            // Auto-GC if memory usage is very high
            if (metrics.memory.usagePercentage > 90f) {
                PerformanceMonitor.forceGarbageCollection("High memory pressure")
            }
        }
    }
}

/**
 * Wrapper for performance-sensitive operations.
 */
@Composable
fun <T> performanceOptimized(
    key: Any?,
    enabled: Boolean = true,
    computation: @Composable () -> T,
): T {
    return if (enabled) {
        remember(key) {
            computation
        }.invoke()
    } else {
        computation()
    }
}

/**
 * Extension function for measuring block execution time.
 */
suspend inline fun <T> measureSuspendTime(tag: String, crossinline block: suspend () -> T): T {
    val start = System.nanoTime()
    val result = block()
    val executionTime = (System.nanoTime() - start) / 1_000_000
    if (BuildConfig.DEBUG) {
        SecureLogger.v("PerformanceMonitor", "$tag executed in ${executionTime}ms")
    }
    return result
}
