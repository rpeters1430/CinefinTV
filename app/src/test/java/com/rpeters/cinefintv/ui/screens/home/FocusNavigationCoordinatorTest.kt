package com.rpeters.cinefintv.ui.screens.home

import com.rpeters.cinefintv.ui.navigation.FocusNavigationCoordinator
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusNavigationCoordinatorTest {

    @Test
    fun submit_debouncesRapidDispatches() = runTest {
        val coordinator = FocusNavigationCoordinator(
            scope = backgroundScope
        )
        var runCount = 0

        coordinator.submit(debounceMs = 100L) { runCount += 1 }
        runCurrent()
        assertEquals(0, runCount) // Not run yet due to debounce

        advanceTimeBy(50)
        coordinator.submit(debounceMs = 100L) { runCount += 1 }
        runCurrent()
        assertEquals(0, runCount) // Debounce reset

        advanceTimeBy(101)
        runCurrent()
        assertEquals(1, runCount) // Finally run
    }

    @Test
    fun submit_cancelsPreviousJob() = runTest {
        val coordinator = FocusNavigationCoordinator(
            scope = backgroundScope
        )
        val events = mutableListOf<String>()

        coordinator.submit {
            events += "start-1"
            try {
                awaitCancellation()
            } catch (e: Exception) {
                events += "cancelled-1"
                throw e
            }
        }
        runCurrent()

        coordinator.submit { events += "start-2" }
        runCurrent()

        assertEquals(listOf("start-1", "cancelled-1", "start-2"), events)
    }
}
