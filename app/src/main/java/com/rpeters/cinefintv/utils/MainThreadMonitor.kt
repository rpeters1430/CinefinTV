package com.rpeters.cinefintv.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitors main thread responsiveness and helps identify frame drops.
 * Only active in debug builds to avoid production overhead.
 */
object MainThreadMonitor {
    private const val TAG = "MainThreadMonitor"
    private const val MONITORING_INTERVAL_MS = 16L // 60 FPS target
    private const val WARNING_THRESHOLD_MS = 32L // 2 frames = warning
    private const val ERROR_THRESHOLD_MS = 48L // 3 frames = error

    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private val lastUpdateTime = AtomicLong(0)

    /**
     * Start monitoring main thread responsiveness.
     * Only works in debug builds.
     */
    fun startMonitoring() {
        if (!BuildConfig.DEBUG || isMonitoring) return

        isMonitoring = true
        Log.d(TAG, "Starting main thread monitoring")

        // Start monitoring coroutine
        CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring) {
                val startTime = SystemClock.uptimeMillis()
                lastUpdateTime.set(startTime)

                // Post a task to main thread
                handler.post {
                    val responseTime = SystemClock.uptimeMillis() - lastUpdateTime.get()

                    when {
                        responseTime >= ERROR_THRESHOLD_MS -> {
                            Log.e(TAG, "Main thread blocked for ${responseTime}ms - severe frame drops likely")
                        }
                        responseTime >= WARNING_THRESHOLD_MS -> {
                            Log.w(TAG, "Main thread delayed by ${responseTime}ms - frame drops possible")
                        }
                    }
                }

                delay(MONITORING_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop monitoring main thread.
     */
    fun stopMonitoring() {
        if (!BuildConfig.DEBUG) return

        isMonitoring = false
        Log.d(TAG, "Stopped main thread monitoring")
    }

    /**
     * Temporarily measure a specific operation's impact on main thread.
     */
    fun <T> measureMainThreadImpact(operationName: String, operation: () -> T): T {
        if (!BuildConfig.DEBUG) {
            return operation()
        }

        val startTime = SystemClock.uptimeMillis()
        val result = operation()
        val duration = SystemClock.uptimeMillis() - startTime

        when {
            duration >= ERROR_THRESHOLD_MS -> {
                Log.e(TAG, "CRITICAL: '$operationName' blocked main thread for ${duration}ms")
            }
            duration >= WARNING_THRESHOLD_MS -> {
                Log.w(TAG, "WARNING: '$operationName' took ${duration}ms on main thread")
            }
            duration >= MONITORING_INTERVAL_MS -> {
                Log.d(TAG, "'$operationName' took ${duration}ms on main thread")
            }
        }

        return result
    }

    /**
     * Log when potentially expensive operations are performed.
     */
    fun logPotentiallyExpensiveOperation(operation: String, details: String = "") {
        if (!BuildConfig.DEBUG) return

        Log.d(TAG, "Potentially expensive operation: $operation ${if (details.isNotEmpty()) "- $details" else ""}")
    }

    /**
     * Check if we're currently on the main thread.
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * Warn if an operation is running on main thread when it shouldn't.
     */
    fun warnIfMainThread(operationName: String) {
        if (!BuildConfig.DEBUG) return

        if (isMainThread()) {
            Log.w(TAG, "WARNING: '$operationName' is running on main thread - consider using background thread")
        }
    }
}
