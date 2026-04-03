package com.rpeters.cinefintv.ui.screens.home

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusNavigationCoordinatorTest {

    @Test
    fun submit_throttlesRapidDispatches() = runTest {
        var now = 1_000L
        val coordinator = FocusNavigationCoordinator(
            scope = backgroundScope,
            throttleWindowMs = 120L,
            nowMs = { now },
        )
        var runCount = 0

        coordinator.submit { runCount += 1 }
        runCurrent()
        assertEquals(1, runCount)

        now += 80L
        coordinator.submit { runCount += 1 }
        runCurrent()

        assertEquals(1, runCount)
    }

    @Test
    fun submit_cancelsPreviousJob() = runTest {
        var now = 1_000L
        val coordinator = FocusNavigationCoordinator(
            scope = backgroundScope,
            throttleWindowMs = 0L,
            nowMs = { now },
        )
        val events = mutableListOf<String>()

        coordinator.submit {
            events += "start-1"
            awaitCancellation()
        }
        runCurrent()

        now += 1L
        coordinator.submit { events += "start-2" }
        runCurrent()

        assertEquals(listOf("start-1", "start-2"), events)
    }
}
