package com.rpeters.cinefintv.ui.screens.detail

import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeSeasonDetailRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeasonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val updateBus = MediaUpdateBus()

    private fun makeSeasonDto(
        id: String = UUID.randomUUID().toString(),
        seriesId: String = UUID.randomUUID().toString(),
    ): BaseItemDto = mockk(relaxed = true) {
        every { this@mockk.id } returns UUID.fromString(id)
        every { this@mockk.seriesId } returns UUID.fromString(seriesId)
        every { mediaSources } returns null
    }

    private fun makeSeriesDto(id: String = UUID.randomUUID().toString()): BaseItemDto =
        mockk(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { mediaSources } returns null
        }

    private fun makeEpisodeDto(id: String = UUID.randomUUID().toString()): BaseItemDto =
        mockk(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { mediaSources } returns null
        }

    @Test
    fun load_populatesEpisodes() = runTest {
        val fakeRepos = FakeSeasonDetailRepositories()
        val seasonId = UUID.randomUUID().toString()
        val seriesId = UUID.randomUUID().toString()
        val seasonDto = makeSeasonDto(id = seasonId, seriesId = seriesId)
        val seriesDto = makeSeriesDto(id = seriesId)
        val episodes = listOf(makeEpisodeDto(), makeEpisodeDto())

        coEvery { fakeRepos.media.getItemDetails(seasonId) } returns ApiResult.Success(seasonDto)
        coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
        coEvery { fakeRepos.media.getEpisodesForSeason(seasonId) } returns ApiResult.Success(episodes)
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getBackdropUrlWithFallback(any(), any()) } returns null
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null

        val vm = SeasonViewModel(fakeRepos.coordinator, updateBus)
        vm.init(seasonId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SeasonUiState.Content)
        assertEquals(2, (state as SeasonUiState.Content).episodes.size)
    }

    @Test
    fun observeUpdateEvents_refreshItemForForeignId_doesNotCallNetwork() = runTest {
        val fakeRepos = FakeSeasonDetailRepositories()
        val seasonId = UUID.randomUUID().toString()
        val seriesId = UUID.randomUUID().toString()
        val foreignId = UUID.randomUUID().toString()
        val seasonDto = makeSeasonDto(id = seasonId, seriesId = seriesId)
        val seriesDto = makeSeriesDto(id = seriesId)
        val episodes = listOf(makeEpisodeDto())

        coEvery { fakeRepos.media.getItemDetails(seasonId) } returns ApiResult.Success(seasonDto)
        coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
        coEvery { fakeRepos.media.getEpisodesForSeason(seasonId) } returns ApiResult.Success(episodes)
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getBackdropUrlWithFallback(any(), any()) } returns null
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null

        val vm = SeasonViewModel(fakeRepos.coordinator, updateBus)
        vm.init(seasonId)
        advanceUntilIdle()

        io.mockk.clearMocks(fakeRepos.media, answers = false)

        // Emit RefreshItem for a DIFFERENT item
        updateBus.refreshItem(foreignId)
        advanceUntilIdle()

        // No episode fetch should have happened
        io.mockk.coVerify(exactly = 0) { fakeRepos.media.getEpisodesForSeason(any()) }
    }

    @Test
    fun refreshWatchStatus_replacesEpisodesInContentState() = runTest {
        val fakeRepos = FakeSeasonDetailRepositories()
        val seasonId = UUID.randomUUID().toString()
        val seriesId = UUID.randomUUID().toString()
        val seasonDto = makeSeasonDto(id = seasonId, seriesId = seriesId)
        val seriesDto = makeSeriesDto(id = seriesId)
        val initialEpisode = makeEpisodeDto()
        val updatedEpisodes = listOf(makeEpisodeDto(), makeEpisodeDto(), makeEpisodeDto())

        coEvery { fakeRepos.media.getItemDetails(seasonId) } returns ApiResult.Success(seasonDto)
        coEvery { fakeRepos.media.getSeriesDetails(seriesId) } returns ApiResult.Success(seriesDto)
        coEvery { fakeRepos.media.getEpisodesForSeason(seasonId) } returnsMany listOf(
            ApiResult.Success(listOf(initialEpisode)),
            ApiResult.Success(updatedEpisodes),
        )
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null
        every { fakeRepos.stream.getBackdropUrlWithFallback(any(), any()) } returns null
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null

        val vm = SeasonViewModel(fakeRepos.coordinator, updateBus)
        vm.init(seasonId)
        advanceUntilIdle()

        vm.refreshWatchStatus()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is SeasonUiState.Content)
        assertEquals(3, (state as SeasonUiState.Content).episodes.size)
    }
}
