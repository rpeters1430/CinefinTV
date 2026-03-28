package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeTvShowDetailRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TvShowDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeSavedStateHandle(seriesId: String) =
        SavedStateHandle(mapOf("itemId" to seriesId))

    private fun makeSeriesDto(id: String = UUID.randomUUID().toString()): BaseItemDto {
        return mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { mediaSources } returns null
            every { seriesId } returns null
            every { people } returns null
            every { genres } returns emptyList()
            every { studios } returns null
            every { childCount } returns 2
        }
    }

    private fun makeSeasonDto(
        id: String = UUID.randomUUID().toString(),
        unplayedCount: Int = 0,
        episodeCount: Int = 5,
    ): BaseItemDto {
        val userData = mockk<UserItemDataDto>(relaxed = true) {
            every { unplayedItemCount } returns unplayedCount
        }
        return mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { this@mockk.userData } returns userData
            every { childCount } returns episodeCount
            every { mediaSources } returns null
        }
    }

    @Test
    fun refreshWatchStatus_whenContent_stateRemainsContent() = runTest {
        val fakeRepos = FakeTvShowDetailRepositories()
        val seriesId = UUID.randomUUID().toString()
        val seriesDto = makeSeriesDto(id = seriesId)
        val seasonDto = makeSeasonDto(unplayedCount = 3)

        coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
        coEvery { fakeRepos.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(seasonDto))
        coEvery { fakeRepos.media.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepos.media.getNextUpForSeries(seriesId) } returns ApiResult.Error("none")
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getWideCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any()) } returns null

        val vm = TvShowDetailViewModel(fakeRepos.coordinator, updateBus, makeSavedStateHandle(seriesId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is TvShowDetailUiState.Content)
        val initialState = vm.uiState.value as TvShowDetailUiState.Content
        assertEquals(3, initialState.seasons.first().unwatchedCount)

        // Simulate all episodes watched
        val watchedSeasonDto = makeSeasonDto(unplayedCount = 0)
        coEvery { fakeRepos.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(watchedSeasonDto))

        vm.refreshWatchStatus()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is TvShowDetailUiState.Content)
        assertEquals(0, (state as TvShowDetailUiState.Content).seasons.first().unwatchedCount)
    }

    @Test
    fun refreshWatchStatus_whenNotContent_doesNothing() = runTest {
        val fakeRepos = FakeTvShowDetailRepositories()
        val seriesId = UUID.randomUUID().toString()

        coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Error("not found")
        coEvery { fakeRepos.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepos.media.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepos.media.getNextUpForSeries(seriesId) } returns ApiResult.Error("none")

        val vm = TvShowDetailViewModel(fakeRepos.coordinator, updateBus, makeSavedStateHandle(seriesId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is TvShowDetailUiState.Error)

        vm.refreshWatchStatus()
        advanceUntilIdle()

        assertTrue(vm.uiState.value is TvShowDetailUiState.Error)
    }

    @Test
    fun refreshWatchStatus_onSeasonsError_doesNotFlickerToLoading() = runTest {
        val fakeRepos = FakeTvShowDetailRepositories()
        val seriesId = UUID.randomUUID().toString()
        val seriesDto = makeSeriesDto(id = seriesId)
        val seasonDto = makeSeasonDto(unplayedCount = 2)

        coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
        coEvery { fakeRepos.media.getSeasonsForSeries(seriesId) } returns ApiResult.Success(listOf(seasonDto))
        coEvery { fakeRepos.media.getSimilarSeries(seriesId) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepos.media.getNextUpForSeries(seriesId) } returns ApiResult.Error("none")
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getWideCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any()) } returns null

        val vm = TvShowDetailViewModel(fakeRepos.coordinator, updateBus, makeSavedStateHandle(seriesId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is TvShowDetailUiState.Content)

        // Simulate seasons fetch failure during refresh
        coEvery { fakeRepos.media.getSeasonsForSeries(seriesId) } returns ApiResult.Error("network error")

        vm.refreshWatchStatus()
        advanceUntilIdle()

        // Should remain Content (stale data, no flicker)
        assertFalse(vm.uiState.value is TvShowDetailUiState.Loading)
        assertTrue(vm.uiState.value is TvShowDetailUiState.Content)
    }
}
    private val updateBus = MediaUpdateBus()
