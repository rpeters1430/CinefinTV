package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeMovieDetailRepositories
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeSavedStateHandle(movieId: String) =
        SavedStateHandle(mapOf("itemId" to movieId))

    private fun makeMovieDto(
        id: String = UUID.randomUUID().toString(),
        played: Boolean = false,
        playbackPercentage: Double = 0.0,
    ): BaseItemDto {
        val userData = mockk<UserItemDataDto>(relaxed = true) {
            every { this@mockk.played } returns played
            every { playedPercentage } returns playbackPercentage
            every { playbackPositionTicks } returns if (playbackPercentage > 0) 100_000_000L else 0L
        }
        return mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { this@mockk.userData } returns userData
            every { mediaSources } returns null
            every { seriesId } returns null
            every { people } returns null
            every { genres } returns emptyList()
            every { studios } returns null
            every { chapters } returns null
        }
    }

    @Test
    fun refreshWatchStatus_whenContent_stateRemainsContent() = runTest {
        val fakeRepos = FakeMovieDetailRepositories()
        val movieId = UUID.randomUUID().toString()
        val dto = makeMovieDto(id = movieId, played = false)

        coEvery { fakeRepos.media.getMovieDetails(movieId) } returns ApiResult.Success(dto)
        coEvery { fakeRepos.media.getSimilarMovies(movieId) } returns ApiResult.Success(emptyList())
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any()) } returns null

        val vm = MovieDetailViewModel(fakeRepos.coordinator, updateBus, makeSavedStateHandle(movieId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MovieDetailUiState.Content)

        // Now simulate the movie being watched
        val watchedDto = makeMovieDto(id = movieId, played = true)
        coEvery { fakeRepos.media.getMovieDetails(movieId) } returns ApiResult.Success(watchedDto)

        vm.refreshWatchStatus()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MovieDetailUiState.Content)
        assertTrue((state as MovieDetailUiState.Content).movie.isWatched)
    }

    @Test
    fun refreshWatchStatus_whenNotContent_doesNothing() = runTest {
        val fakeRepos = FakeMovieDetailRepositories()
        val movieId = UUID.randomUUID().toString()

        coEvery { fakeRepos.media.getMovieDetails(movieId) } returns ApiResult.Error("not found")
        coEvery { fakeRepos.media.getSimilarMovies(movieId) } returns ApiResult.Success(emptyList())

        val vm = MovieDetailViewModel(fakeRepos.coordinator, updateBus, makeSavedStateHandle(movieId))
        advanceUntilIdle()

        // State is Error, refreshWatchStatus should no-op
        assertTrue(vm.uiState.value is MovieDetailUiState.Error)

        vm.refreshWatchStatus()
        advanceUntilIdle()

        // Still Error, no crash
        assertTrue(vm.uiState.value is MovieDetailUiState.Error)
    }

    @Test
    fun refreshWatchStatus_onError_doesNotFlickerToLoading() = runTest {
        val fakeRepos = FakeMovieDetailRepositories()
        val movieId = UUID.randomUUID().toString()
        val dto = makeMovieDto(id = movieId)

        coEvery { fakeRepos.media.getMovieDetails(movieId) } returns ApiResult.Success(dto)
        coEvery { fakeRepos.media.getSimilarMovies(movieId) } returns ApiResult.Success(emptyList())
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any()) } returns null

        val vm = MovieDetailViewModel(fakeRepos.coordinator, updateBus, makeSavedStateHandle(movieId))
        advanceUntilIdle()

        assertTrue(vm.uiState.value is MovieDetailUiState.Content)

        // Simulate network failure during refresh
        coEvery { fakeRepos.media.getMovieDetails(movieId) } returns ApiResult.Error("network error")

        vm.refreshWatchStatus()
        advanceUntilIdle()

        // Should remain Content (stale data, no flicker)
        assertFalse(vm.uiState.value is MovieDetailUiState.Loading)
        assertTrue(vm.uiState.value is MovieDetailUiState.Content)
    }
}
    private val updateBus = MediaUpdateBus()
