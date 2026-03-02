package com.rpeters.cinefintv.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for optimizing performance and preventing main thread blocking.
 */
object PerformanceOptimizer {
    private const val TAG = "PerformanceOptimizer"

    /**
     * Execute a potentially heavy operation on a background thread,
     * with automatic fallback to main thread if needed.
     */
    suspend fun <T> executeOffMainThread(
        operation: suspend () -> T,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): T {
        return if (isMainThread()) {
            withContext(dispatcher) {
                operation()
            }
        } else {
            operation()
        }
    }

    /**
     * Check if we're currently on the main thread.
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    /**
     * Post a task to run on the main thread.
     */
    fun runOnMainThread(task: () -> Unit) {
        if (isMainThread()) {
            task()
        } else {
            Handler(Looper.getMainLooper()).post(task)
        }
    }

    /**
     * Warn if an operation is being performed on the main thread when it shouldn't be.
     */
    fun warnIfMainThread(operation: String) {
        if (isMainThread() && com.rpeters.cinefintv.BuildConfig.DEBUG) {
            Log.w(TAG, "Performance warning: $operation is being performed on main thread")
        }
    }

    /**
     * Execute file I/O operation safely off the main thread.
     */
    suspend fun <T> executeFileIO(operation: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            operation()
        }
    }

    /**
     * Execute network operation safely off the main thread.
     */
    suspend fun <T> executeNetworkOperation(operation: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            operation()
        }
    }

    /**
     * Execute CPU-intensive operation on appropriate thread.
     */
    suspend fun <T> executeCPUIntensive(operation: suspend () -> T): T {
        return withContext(Dispatchers.Default) {
            operation()
        }
    }
}
