package com.rpeters.cinefintv.data.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides coroutine dispatchers for dependency injection.
 * This allows tests to inject test dispatchers instead of production dispatchers.
 *
 * Usage in production code:
 * ```kotlin
 * withContext(dispatchers.io) {
 *     // IO work
 * }
 * ```
 *
 * Usage in tests:
 * ```kotlin
 * val testDispatcher = StandardTestDispatcher()
 * val testDispatchers = TestDispatcherProvider(testDispatcher)
 * ```
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

/**
 * Production implementation using standard Android/Kotlin dispatchers
 */
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}

/**
 * Test implementation using a single test dispatcher for all contexts
 */
class TestDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
