package com.rpeters.cinefintv.ui.screens.home

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.common.TestDispatcherProvider
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeHomeRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val updateBus = MediaUpdateBus()

    private fun activateAuth(fakeRepositories: FakeHomeRepositories): JellyfinServer {
        return mockk<JellyfinServer>(relaxed = true).also { server ->
            fakeRepositories.authRepository.seedCurrentServer(server)
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.yieldUntilContent(viewModel: HomeViewModel) {
        // Always advance once so silent refreshes complete even when the UI never re-enters Loading.
        repeat(500) { 
            yield()
            runCurrent()
            advanceUntilIdle()
            if (viewModel.uiState.value !is HomeUiState.Loading) return
        }
    }

    private fun stubRecentlyAddedByType(
        fakeRepositories: FakeHomeRepositories,
        movies: ApiResult<List<BaseItemDto>> = ApiResult.Success(emptyList()),
        episodes: ApiResult<List<BaseItemDto>> = ApiResult.Success(emptyList()),
        videos: ApiResult<List<BaseItemDto>> = ApiResult.Success(emptyList()),
        music: ApiResult<List<BaseItemDto>> = ApiResult.Success(emptyList()),
    ) {
        coEvery { fakeRepositories.media.getRecentlyAddedByType(any(), any()) } answers {
            when (firstArg<BaseItemKind>()) {
                BaseItemKind.MOVIE -> movies
                BaseItemKind.EPISODE -> episodes
                BaseItemKind.VIDEO -> videos
                BaseItemKind.AUDIO -> music
                else -> ApiResult.Success(emptyList())
            }
        }
    }

    @Test
    fun refresh_buildsSectionsFromAvailableResults() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockResumableMovie("Movie 1")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(listOf(movie))
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        stubRecentlyAddedByType(
            fakeRepositories,
            movies = ApiResult.Success(emptyList()),
            episodes = ApiResult.Error("episodes failed"),
        )
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value
        assertTrue("Expected Content state, but was ${state.javaClass.simpleName}", state is HomeUiState.Content)
        val content = state as HomeUiState.Content
        assertEquals(1, content.sections.size)
        assertEquals(HomeSectionId.CONTINUE_WATCHING, content.sections.first().id)
        assertEquals("Movie 1", content.sections.first().items.first().title)
    }

    @Test
    fun refresh_whenAllSectionsEmptyOrError_setsFallbackError() = runTest {
        val fakeRepositories = FakeHomeRepositories()

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Error("backend unavailable")
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(any(), any())
        } returns ApiResult.Success(emptyList())

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, but was ${state.javaClass.simpleName}", state is HomeUiState.Error)
        assertEquals("backend unavailable", (state as HomeUiState.Error).message)
    }

    @Test
    fun refresh_recentlyAddedMovies_populatesFeaturedItems() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie1 = mockBaseItemDto("Featured Movie 1")
        val movie2 = mockBaseItemDto("Featured Movie 2")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        stubRecentlyAddedByType(
            fakeRepositories,
            movies = ApiResult.Success(listOf(movie1, movie2)),
        )
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value
        assertTrue("Expected Content state, but was ${state.javaClass.simpleName}", state is HomeUiState.Content)
        val content = state as HomeUiState.Content
        assertEquals(2, content.featuredItems.size)
        assertEquals("Featured Movie 1", content.featuredItems.first().title)
    }

    @Test
    fun refreshWatchStatus_updatesOnlyContinueWatchingSection() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockResumableMovie("Movie 1")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(listOf(movie))
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(any(), any())
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        assertTrue(viewModel.uiState.value is HomeUiState.Content)

        viewModel.refreshWatchStatus()
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Content)
    }

    @Test
    fun refreshWatchStatus_whenStateIsNotContent_doesNothing() = runTest {
        val fakeRepositories = FakeHomeRepositories()

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Error("failed")
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(any(), any())
        } returns ApiResult.Success(emptyList())

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        // Should be in Error state
        assertTrue(viewModel.uiState.value is HomeUiState.Error)

        viewModel.refreshWatchStatus()
        yieldUntilContent(viewModel)

        // Should still be in Error state
        assertTrue(viewModel.uiState.value is HomeUiState.Error)
    }

    @Test
    fun refresh_recentlyAddedVideoSection_usesCollectionsTitle() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val video = mockBaseItemDto("Clip 1", BaseItemKind.VIDEO)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        stubRecentlyAddedByType(
            fakeRepositories,
            videos = ApiResult.Success(listOf(video)),
        )
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value
        assertTrue("Expected Content state, but was ${state.javaClass.simpleName}", state is HomeUiState.Content)
        val content = state as HomeUiState.Content
        val section = content.sections.find { it.id == HomeSectionId.RECENT_COLLECTIONS }
        assertTrue("RECENT_COLLECTIONS section not found", section != null)
        assertEquals("Clip 1", section!!.items.single().title)
    }

    @Test
    fun refresh_nextEpisodes_usesGlobalNextUpNotContinueWatchingEpisodesOnly() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockResumableMovie("Movie 1")
        val nextEpisode1 = mockBaseItemDto("Show A - S1E2", BaseItemKind.EPISODE, season = 1, episode = 2)
        val nextEpisode2 = mockBaseItemDto("Show B - S2E4", BaseItemKind.EPISODE, season = 2, episode = 4)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(listOf(movie))
        coEvery {
            fakeRepositories.media.getNextUp(limit = 12)
        } returns ApiResult.Success(listOf(nextEpisode1, nextEpisode2))
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(any(), any())
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value as HomeUiState.Content
        val nextEpisodes = state.sections.first { it.id == HomeSectionId.NEXT_EPISODES }
        assertEquals(2, nextEpisodes.items.size)
        assertEquals("Show A - S1E2", nextEpisodes.items[0].title)
    }

    @Test
    fun refresh_nextEpisodes_filtersOutUnstartedShows() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        // Unstarted show (S1E1, no progress)
        val unstartedEpisode = mockBaseItemDto("Unstarted Show", BaseItemKind.EPISODE, season = 1, episode = 1)
        
        // Started show (S1E1, but we'll mock progress)
        val startedFirstEpisode = mockBaseItemDto("Started Show", BaseItemKind.EPISODE, season = 1, episode = 1)
        val userData = mockk<org.jellyfin.sdk.model.api.UserItemDataDto>()
        every { userData.playedPercentage } returns 15.0
        every { userData.played } returns false
        every { userData.playbackPositionTicks } returns 1000L
        every { startedFirstEpisode.userData } returns userData

        // Show with progress (S2E1)
        val showWithProgress = mockBaseItemDto("Show with Progress", BaseItemKind.EPISODE, season = 2, episode = 1)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getNextUp(limit = 12)
        } returns ApiResult.Success(listOf(unstartedEpisode, startedFirstEpisode, showWithProgress))
        coEvery { fakeRepositories.media.getRecentlyAddedByType(any(), any()) } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val state = viewModel.uiState.value as HomeUiState.Content
        val nextEpisodes = state.sections.first { it.id == HomeSectionId.NEXT_EPISODES }
        
        // Should only have 2 items (unstarted one is filtered)
        assertEquals(2, nextEpisodes.items.size)
        assertTrue(nextEpisodes.items.any { it.title == "Started Show" })
        assertTrue(nextEpisodes.items.any { it.title == "Show with Progress" })
    }

    @Test
    fun refresh_silent_updatesRecentlyAddedWithoutLeavingContentState() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val oldMovie = mockBaseItemDto("Old Movie")
        val newMovie = mockBaseItemDto("New Movie")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        var featuredMovieRequests = 0
        coEvery { fakeRepositories.media.getRecentlyAddedByType(any(), any()) } answers {
            when (firstArg<BaseItemKind>()) {
                BaseItemKind.MOVIE -> {
                    featuredMovieRequests += 1
                    ApiResult.Success(if (featuredMovieRequests == 1) listOf(oldMovie) else listOf(newMovie))
                }
                else -> ApiResult.Success(emptyList())
            }
        }
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val initialState = viewModel.uiState.value as HomeUiState.Content
        assertEquals("Old Movie", initialState.featuredItems.first().title)

        viewModel.refresh(silent = true)
        yieldUntilContent(viewModel)

        val refreshedState = viewModel.uiState.value as HomeUiState.Content
        assertEquals("New Movie", refreshedState.featuredItems.first().title)
    }

    @Test
    fun refresh_silent_whenContentIsUnchanged_keepsExistingUiStateInstance() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val movie = mockResumableMovie("Movie 1")

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(listOf(movie))
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(any(), any())
        } returns ApiResult.Success(emptyList())
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val initialState = viewModel.uiState.value
        viewModel.refresh(silent = true)
        yieldUntilContent(viewModel)

        assertSame(initialState, viewModel.uiState.value)
    }

    @Test
    fun refresh_silent_whenOnlyFeaturedChanges_reusesUnchangedSectionInstances() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val oldMovie = mockBaseItemDto("Old Movie")
        val newMovie = mockBaseItemDto("New Movie")
        val video = mockBaseItemDto("Clip 1", BaseItemKind.VIDEO)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getContinueWatching(limit = 24) } returns ApiResult.Success(emptyList())
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        var movieRequests = 0
        coEvery { fakeRepositories.media.getRecentlyAddedByType(any(), any()) } answers {
            when (firstArg<BaseItemKind>()) {
                BaseItemKind.MOVIE -> {
                    movieRequests += 1
                    ApiResult.Success(if (movieRequests == 1) listOf(oldMovie) else listOf(newMovie))
                }
                BaseItemKind.VIDEO -> ApiResult.Success(listOf(video))
                else -> ApiResult.Success(emptyList())
            }
        }
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val initialState = viewModel.uiState.value as HomeUiState.Content
        val initialCollectionsSection = initialState.sections.find { it.id == HomeSectionId.RECENT_COLLECTIONS }
        assertTrue("RECENT_COLLECTIONS not found in initial state", initialCollectionsSection != null)

        viewModel.refresh(silent = true)
        yieldUntilContent(viewModel)

        val refreshedState = viewModel.uiState.value as HomeUiState.Content
        val refreshedCollectionsSection = refreshedState.sections.find { it.id == HomeSectionId.RECENT_COLLECTIONS }
        assertTrue("RECENT_COLLECTIONS not found in refreshed state", refreshedCollectionsSection != null)

        assertSame(initialCollectionsSection, refreshedCollectionsSection)
        assertEquals("New Movie", refreshedState.featuredItems.first().title)
    }

    @Test
    fun refreshWatchStatus_reusesUnchangedNonWatchSections() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val initialMovie = mockResumableMovie("Movie 1")
        val refreshedMovie = mockResumableMovie("Movie 2")
        val video = mockBaseItemDto("Clip 1", BaseItemKind.VIDEO)

        coEvery { fakeRepositories.media.getUserLibraries() } returns ApiResult.Success(emptyList())
        coEvery {
            fakeRepositories.media.getContinueWatching(limit = 24)
        } returnsMany listOf(
            ApiResult.Success(listOf(initialMovie)),
            ApiResult.Success(listOf(refreshedMovie)),
        )
        coEvery { fakeRepositories.media.getNextUp(limit = 12) } returns ApiResult.Success(emptyList())
        stubRecentlyAddedByType(
            fakeRepositories,
            videos = ApiResult.Success(listOf(video)),
        )
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns null

        val viewModel = HomeViewModel(fakeRepositories.coordinator, updateBus, TestDispatcherProvider(mainDispatcherRule.dispatcher))
        activateAuth(fakeRepositories)
        yieldUntilContent(viewModel)

        val initialState = viewModel.uiState.value as HomeUiState.Content
        val initialCollectionsSection = initialState.sections.find { it.id == HomeSectionId.RECENT_COLLECTIONS }
        assertTrue("RECENT_COLLECTIONS not found in initial state", initialCollectionsSection != null)

        viewModel.refreshWatchStatus()
        yieldUntilContent(viewModel)

        val refreshedState = viewModel.uiState.value as HomeUiState.Content
        val refreshedCollectionsSection = refreshedState.sections.find { it.id == HomeSectionId.RECENT_COLLECTIONS }
        assertTrue("RECENT_COLLECTIONS not found in refreshed state", refreshedCollectionsSection != null)
        val continueWatchingSection = refreshedState.sections.find { it.id == HomeSectionId.CONTINUE_WATCHING }
        assertTrue("CONTINUE_WATCHING not found in refreshed state", continueWatchingSection != null)

        assertSame(initialCollectionsSection, refreshedCollectionsSection)
        assertEquals("Movie 2", continueWatchingSection!!.items.first().title)
    }

    private fun mockBaseItemDto(
        name: String,
        type: BaseItemKind = BaseItemKind.MOVIE,
        season: Int? = null,
        episode: Int? = null,
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
        every { item.parentIndexNumber } returns season
        every { item.indexNumber } returns episode
        every { item.parentId } returns null
        return item
    }

    private fun mockResumableMovie(name: String): BaseItemDto {
        val movie = mockBaseItemDto(name)
        val userData = mockk<org.jellyfin.sdk.model.api.UserItemDataDto>()
        // Mock data fields that canResume() uses (it needs percentage between 5 and 92)
        every { userData.playedPercentage } returns 45.0
        every { userData.played } returns false
        every { userData.playbackPositionTicks } returns 1000L
        every { movie.userData } returns userData
        every { movie.runTimeTicks } returns 2000L
        return movie
    }
}
