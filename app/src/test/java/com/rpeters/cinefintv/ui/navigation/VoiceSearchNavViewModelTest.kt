package com.rpeters.cinefintv.ui.navigation

import com.rpeters.cinefintv.VoiceSearchCoordinator
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSearchNavViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pendingQuery_exposesCoordinatorFlow() = runTest {
        val coordinator = mockk<VoiceSearchCoordinator>()
        val pendingQueryFlow = MutableSharedFlow<String>(replay = 1)
        every { coordinator.pendingQuery } returns pendingQueryFlow

        val viewModel = VoiceSearchNavViewModel(coordinator)
        advanceUntilIdle()

        pendingQueryFlow.emit("test search query")
        advanceUntilIdle()

        assertEquals("test search query", viewModel.pendingQuery.first())
    }
}
