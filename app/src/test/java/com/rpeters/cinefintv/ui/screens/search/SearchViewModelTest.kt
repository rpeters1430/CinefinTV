package com.rpeters.cinefintv.ui.screens.search

import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeHomeRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updateQuery_whenBlank_clearsResultsWithoutCallingRepository() = runTest {
        val fakeRepo = FakeHomeRepositories()
        coEvery { fakeRepo.coordinator.search.searchItems(any(), any(), any()) } returns ApiResult.Success(emptyList())

        val viewModel = SearchViewModel(fakeRepo.coordinator)
        advanceUntilIdle()

        viewModel.updateQuery("    ")
        advanceTimeBy(400)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals(0, state.results.size)
        coVerify(exactly = 0) { fakeRepo.coordinator.search.searchItems(any(), any(), any()) }
    }
}
