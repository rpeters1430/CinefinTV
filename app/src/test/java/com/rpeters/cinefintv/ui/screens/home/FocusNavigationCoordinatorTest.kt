package com.rpeters.cinefintv.ui.screens.home

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        assertThat(runCount).isEqualTo(1)

        now += 80L
        coordinator.submit { runCount += 1 }
        runCurrent()

        assertThat(runCount).isEqualTo(1)
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

        assertThat(events).containsExactly("start-1", "start-2").inOrder()
    }
}
