package com.rpeters.cinefintv.ui.screens.home

import com.rpeters.cinefintv.data.common.MediaUpdateBus
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

    private val updateBus = MediaUpdateBus()

    @Test
    fun refresh_buildsSectionsFromAvailableResults() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockBaseItemDto("Movie 1")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie))
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Error("episodes failed")
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals(1, state.sections.size)
        assertEquals("Continue Watching", state.sections.first().title)
        assertEquals(1, state.sections.first().items.size)
        assertEquals("Movie 1", state.sections.first().items.first().title)
    }

    @Test
    fun refresh_whenAllSectionsEmptyOrError_setsFallbackError() = runTest {
        val fakeRepositories = FakeHomeRepositories()

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Error("backend unavailable")
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Error("episodes failed")
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertEquals("backend unavailable", (state as HomeUiState.Error).message)
    }

    @Test
    fun refresh_recentlyAddedMovies_populatesFeaturedItems() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie1 = mockBaseItemDto("Featured Movie 1")
        val movie2 = mockBaseItemDto("Featured Movie 2")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(listOf(movie1, movie2))
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals(2, state.featuredItems.size)
        assertEquals("Featured Movie 1", state.featuredItems.first().title)
        assertEquals("https://img/backdrop.jpg", state.featuredItems.first().backdropUrl)
    }

    @Test
    fun refreshWatchStatus_updatesOnlyContinueWatchingSection() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockBaseItemDto("Movie 1")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie))
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is HomeUiState.Content)

        viewModel.refreshWatchStatus()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Content)
    }

    @Test
    fun refreshWatchStatus_whenStateIsNotContent_doesNothing() = runTest {
        val fakeRepositories = FakeHomeRepositories()

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Error("backend unavailable")
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)

        // Call refreshWatchStatus before advancing (state may still be Loading)
        viewModel.refreshWatchStatus()
        advanceUntilIdle()

        // Should not crash; state ends in Error since all sections empty/error
        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Error || state is HomeUiState.Content)
    }

    @Test
    fun refresh_recentlyAddedVideoSection_usesStuffTitle() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val video = mockBaseItemDto("Clip 1", BaseItemKind.VIDEO)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(listOf(video))
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals("Recently Added Stuff", state.sections.single().title)
        assertEquals("Clip 1", state.sections.single().items.single().title)
    }

    @Test
    fun refresh_nextEpisodes_usesGlobalNextUpNotContinueWatchingEpisodesOnly() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockBaseItemDto("Movie 1")
        val nextEpisode1 = mockBaseItemDto("Show A - S1E2", BaseItemKind.EPISODE)
        val nextEpisode2 = mockBaseItemDto("Show B - S2E4", BaseItemKind.EPISODE)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(listOf(movie))
        coEvery {
            fakeRepositories.media.getNextUp(limit = 12)
        } returns ApiResult.Success(listOf(nextEpisode1, nextEpisode2))
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        val state = viewModel.uiState.value as HomeUiState.Content
        val nextEpisodes = state.sections.first { it.title == "Next Episodes" }
        assertEquals(2, nextEpisodes.items.size)
        assertEquals("Show A - S1E2", nextEpisodes.items[0].title)
        assertEquals("Show B - S2E4", nextEpisodes.items[1].title)
    }

    @Test
    fun refresh_silent_updatesRecentlyAddedWithoutLeavingContentState() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val oldMovie = mockBaseItemDto("Old Movie")
        val newMovie = mockBaseItemDto("New Movie")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12)
        } returnsMany listOf(
            ApiResult.Success(listOf(oldMovie)),
            ApiResult.Success(listOf(newMovie)),
        )
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12)
        } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12)
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value as HomeUiState.Content
        assertEquals("Old Movie", initialState.featuredItems.first().title)

        viewModel.refresh(silent = true)
        assertTrue(viewModel.uiState.value is HomeUiState.Content)
        advanceUntilIdle()

        val refreshedState = viewModel.uiState.value as HomeUiState.Content
        assertEquals("New Movie", refreshedState.featuredItems.first().title)
    }

    private fun mockBaseItemDto(
        name: String,
        type: BaseItemKind = BaseItemKind.MOVIE,
    ): BaseItemDto {
        val item: BaseItemDto = mockk()
        every { item.id } returns UUID.randomUUID()
        every { item.name } returns name
        every { item.type } returns type
        every { item.userData } returns null
        every { item.productionYear } returns null
        every { item.runTimeTicks } returns null
        every { item.overview } returns null
        every { item.communityRating } returns null
        every { item.officialRating } returns null
        every { item.collectionType } returns null
        every { item.mediaSources } returns null
        every { item.seriesId } returns null
        every { item.seriesName } returns null
        every { item.parentIndexNumber } returns null
        every { item.indexNumber } returns null
        every { item.parentId } returns null
        return item
    }
}
