package com.rpeters.cinefintv.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

/**
 * Throttles concurrent operations to prevent main thread blocking
 * and improve overall app performance.
 */
object ConcurrencyThrottler {
    private const val MAX_CONCURRENT_OPERATIONS = 3
    private const val THROTTLE_DELAY_MS = 50L

    // Semaphore to limit concurrent operations
    private val semaphore = Semaphore(MAX_CONCURRENT_OPERATIONS)

    // Supervised scope for throttled operations
    private val throttleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Execute an operation with concurrency throttling
     */
    suspend fun <T> throttle(
        operation: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        semaphore.acquire()
        try {
            // Small delay to prevent overwhelming the system
            delay(THROTTLE_DELAY_MS)
            operation()
        } finally {
            semaphore.release()
        }
    }

    /**
     * Execute multiple operations with throttling and proper spacing
     */
    suspend fun <T> throttleAll(
        operations: List<suspend () -> T>,
    ): List<T> = withContext(Dispatchers.IO) {
        operations.mapIndexed { index, operation ->
            // Add progressive delay to spread out operations
            delay(index * THROTTLE_DELAY_MS)
            throttle { operation() }
        }
    }

    /**
     * Launch operations in the background with throttling
     */
    fun <T> launchThrottled(
        operation: suspend CoroutineScope.() -> T,
    ): Job {
        return throttleScope.launch {
            throttle { operation() }
        }
    }
}
