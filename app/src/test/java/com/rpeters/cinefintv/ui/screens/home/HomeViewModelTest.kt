package com.rpeters.cinefintv.ui.screens.home

import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeHomeRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_buildsSectionsFromAvailableResults() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockBaseItemDto("Movie 1")

        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie))
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.SERIES, limit = 12)
        } returns ApiResult.Error("series failed")
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getSeriesImageUrl(any()) } returns "https://img/poster.jpg"

        val viewModel = HomeViewModel(fakeRepositories.coordinator)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals("Movie 1", state.featured?.title)
        assertEquals(1, state.sections.size)
        assertEquals("Continue Watching", state.sections.first().title)
        assertEquals(1, state.sections.first().items.size)
    }

    @Test
    fun refresh_whenAllSectionsEmptyOrError_setsFallbackError() = runTest {
        val fakeRepositories = FakeHomeRepositories()

        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Error("backend unavailable")
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.SERIES, limit = 12)
        } returns ApiResult.Error("series failed")
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())

        val viewModel = HomeViewModel(fakeRepositories.coordinator)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertEquals("backend unavailable", (state as HomeUiState.Error).message)
    }

    private fun mockBaseItemDto(name: String): BaseItemDto {
        val item: BaseItemDto = mockk()
        every { item.id } returns UUID.randomUUID()
        every { item.name } returns name
        every { item.type } returns BaseItemKind.MOVIE
        every { item.userData } returns null
        every { item.productionYear } returns null
        every { item.runTimeTicks } returns null
        return item
    }
}
