package com.rpeters.cinefintv.ui.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * A centralized coordinator for D-pad focus and navigation tasks.
 * It provides debouncing for rapid scroll operations and safe focus redirection
 * to avoid common race conditions in complex Compose-based TV layouts.
 */
@Stable
class FocusNavigationCoordinator(private val scope: CoroutineScope) {
    private var scrollJob: Job? = null
    private var focusJob: Job? = null
    private var activeJob: Job? = null

    /**
     * Submits a focus or navigation task to be executed. If a new task is submitted 
     * before the previous one completes (or within the [debounceMs] window), 
     * the previous task is cancelled.
     */
    fun submit(debounceMs: Long = 0L, block: suspend () -> Unit) {
        activeJob?.cancel()
        activeJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            block()
        }
    }

    /**
     * Cancels any currently active navigation or focus job.
     */
    fun cancelActiveJob() {
        activeJob?.cancel()
        activeJob = null
        scrollJob?.cancel()
        scrollJob = null
        focusJob?.cancel()
        focusJob = null
    }

    /**
     * Debounces a scroll request to [index] and optionally focuses [focusRequester] once settled.
     * This avoids redundant scrollToItem calls during rapid D-pad navigation.
     */
    fun enqueueScrollAndFocus(
        listState: LazyListState,
        index: Int,
        focusRequester: FocusRequester? = null,
        debounceMs: Long = 32L
    ) {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            delay(debounceMs)
            
            // Wait for item to be visible if it's not
            if (!listState.isIndexVisible(index)) {
                listState.scrollToItem(index)
                // Wait for the item to actually be laid out
                snapshotFlow { listState.isIndexVisible(index) }.first { it }
            }
            
            focusRequester?.let {
                // Ensure layout has fully settled before requesting focus
                withFrameNanos { }
                withFrameNanos { }
                runCatching { it.requestFocus() }
            }
        }
    }

    /**
     * Safely requests focus with a small delay and retries to handle cases where 
     * a focusable item is not yet ready (e.g., during transition or layout pass).
     */
    fun requestFocus(
        focusRequester: FocusRequester,
        delayMs: Long = 0L,
        retries: Int = 3
    ) {
        focusJob?.cancel()
        focusJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            focusRequester.safeRequestFocus(retries)
        }
    }

    private fun LazyListState.isIndexVisible(index: Int): Boolean =
        layoutInfo.visibleItemsInfo.any { it.index == index }
}

/**
 * Centralized helper to safely request focus with retries and delay back-off.
 */
suspend fun FocusRequester.safeRequestFocus(retries: Int = 3) {
    repeat(retries + 1) { attempt ->
        // Wait for a frame to ensure the UI has updated
        withFrameNanos { }
        try {
            requestFocus()
            return
        } catch (e: Exception) {
            // Slight delay before retry if it fails
            delay(if (attempt == 0) 64L else 32L)
        }
    }
}

/**
 * CompositionLocal for [FocusNavigationCoordinator].
 */
val LocalFocusNavigationCoordinator = compositionLocalOf<FocusNavigationCoordinator?> { null }
