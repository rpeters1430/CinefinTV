package com.rpeters.cinefintv.ui.screens.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serializes focus/scroll navigation work so rapid DPAD presses do not queue stale jobs.
 */
internal class FocusNavigationCoordinator(
    private val scope: CoroutineScope,
    private val throttleWindowMs: Long = 120L,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var activeJob: Job? = null
    private var lastDispatchAtMs: Long = Long.MIN_VALUE

    fun submit(block: suspend () -> Unit) {
        val now = nowMs()
        if (lastDispatchAtMs != Long.MIN_VALUE && now - lastDispatchAtMs < throttleWindowMs) {
            return
        }
        lastDispatchAtMs = now
        activeJob?.cancel()
        activeJob = scope.launch { block() }
    }

    fun cancelActiveJob() {
        activeJob?.cancel()
        activeJob = null
    }
}
