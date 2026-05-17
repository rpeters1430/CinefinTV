package com.rpeters.cinefintv.ui.screens.search

import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.testutil.FakeHomeRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import com.rpeters.cinefintv.VoiceSearchCoordinator
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val voiceSearchCoordinator: VoiceSearchCoordinator = mockk {
        every { pendingQuery } returns MutableSharedFlow<String>().asSharedFlow()
    }

    @Test
    fun updateQuery_whenBlank_clearsResultsWithoutCallingRepository() = runTest {
        val fakeRepo = FakeHomeRepositories()
        coEvery { fakeRepo.coordinator.search.searchItems(any(), any(), any()) } returns ApiResult.Success(emptyList())

        val viewModel = SearchViewModel(fakeRepo.coordinator, MediaUpdateBus(), voiceSearchCoordinator)
        advanceUntilIdle()

        viewModel.updateQuery("    ")
        advanceTimeBy(400)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals(0, state.results.size)
        coVerify(exactly = 0) { fakeRepo.coordinator.search.searchItems(any(), any(), any()) }
    }

    @Test
    fun updateQuery_cancelsOlderSearchBeforePublishingStaleResults() = runTest {
        val fakeRepo = FakeHomeRepositories()
        val latestResult = mockk<BaseItemDto>(relaxed = true).also { item ->
            every { item.id } returns UUID.randomUUID()
            every { item.name } returns "Batman"
            every { item.type } returns BaseItemKind.MOVIE
            every { item.collectionType } returns null
            every { item.userData } returns null
        }
        every { fakeRepo.coordinator.stream.getSearchCardImageUrl(any()) } returns "https://img/search.jpg"
        coEvery { fakeRepo.coordinator.search.searchItems(any(), any(), any()) } coAnswers {
            val query = firstArg<String>()
            if (query == "bat") {
                delay(1_000)
                ApiResult.Success(emptyList())
            } else {
                ApiResult.Success(listOf(latestResult))
            }
        }

        val viewModel = SearchViewModel(fakeRepo.coordinator, MediaUpdateBus(), voiceSearchCoordinator)
        advanceUntilIdle()

        viewModel.updateQuery("bat")
        advanceTimeBy(351)
        runCurrent()

        viewModel.updateQuery("batman")
        advanceTimeBy(351)
        advanceUntilIdle()

        assertEquals("batman", viewModel.uiState.value.query)
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("Batman", viewModel.uiState.value.results.single().title)
    }
}
