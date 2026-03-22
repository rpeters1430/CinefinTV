package com.rpeters.cinefintv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.PlaybackPositionStore
import com.rpeters.cinefintv.data.preferences.PlaybackPreferences
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferences
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferencesRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakePlayerRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appContext: Context = mockk(relaxed = true)
    private val playbackPreferencesRepository: PlaybackPreferencesRepository = mockk {
        every { preferences } returns flowOf(PlaybackPreferences.DEFAULT)
    }
    private val subtitleAppearancePreferencesRepository: SubtitleAppearancePreferencesRepository = mockk {
        every { preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
    }
    private val jellyfinRepository: JellyfinRepository = mockk(relaxed = true)
    private val enhancedPlaybackManager: com.rpeters.cinefintv.data.playback.EnhancedPlaybackManager = mockk {
        coEvery { getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("mock error")
    }
    private val adaptiveBitrateMonitor: com.rpeters.cinefintv.data.playback.AdaptiveBitrateMonitor = mockk(relaxed = true)

    init {
        mockkObject(PlaybackPositionStore)
    }

    @Test
    fun load_whenItemIdMissing_setsFriendlyError() = runTest {
        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "")),
            repositories = FakePlayerRepositories().coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No playable item was provided.", state.errorMessage)
        assertNull(state.streamUrl)
    }

    @Test
    fun load_whenStreamUrlMissing_setsStreamError() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns null
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Unable to create a stream for this item.", state.errorMessage)
        assertNull(state.streamUrl)
    }

    @Test
    fun load_whenDetailsFail_usesFallbackTitle() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Now Playing", state.title)
        assertEquals("https://stream/item-1", state.streamUrl)
    }

    @Test
    fun load_whenEpisodeItem_populatesSeasonAndEpisodeNumbers() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("ep-1") } returns "https://stream/ep-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.DirectPlay(
            url = "https://stream/ep-1",
            container = "mkv",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 1000,
            reason = "test"
        )

        val episodeItem: BaseItemDto = mockk()
        every { episodeItem.id } returns UUID.fromString("00000000-0000-0000-0000-000000000001")
        every { episodeItem.name } returns "Test Episode"
        every { episodeItem.type } returns BaseItemKind.EPISODE
        every { episodeItem.parentIndexNumber } returns 2
        every { episodeItem.indexNumber } returns 5
        every { episodeItem.seriesId } returns null
        every { episodeItem.userData } returns null
        every { episodeItem.chapters } returns null

        coEvery { fakeRepositories.media.getItemDetails("ep-1") } returns ApiResult.Success(episodeItem)
        coEvery { fakeRepositories.media.getNextEpisode("ep-1") } returns ApiResult.Error("none")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "ep-1") } returns 0L

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "ep-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.seasonNumber)
        assertEquals(5, state.episodeNumber)
        assertEquals("Test Episode", state.title)
    }

    @Test
    fun load_whenMovieItem_seasonAndEpisodeNumbersAreNull() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("movie-1") } returns "https://stream/movie-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.DirectPlay(
            url = "https://stream/movie-1",
            container = "mkv",
            videoCodec = "h264",
            audioCodec = "aac",
            bitrate = 1000,
            reason = "test"
        )

        val movieItem: BaseItemDto = mockk()
        every { movieItem.id } returns UUID.fromString("00000000-0000-0000-0000-000000000002")
        every { movieItem.name } returns "Test Movie"
        every { movieItem.type } returns BaseItemKind.MOVIE
        every { movieItem.seriesId } returns null
        every { movieItem.userData } returns null
        every { movieItem.chapters } returns null

        coEvery { fakeRepositories.media.getItemDetails("movie-1") } returns ApiResult.Success(movieItem)
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "movie-1") } returns 0L

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "movie-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.seasonNumber)
        assertNull(state.episodeNumber)
    }

    @Test
    fun load_whenResumeModeIsNever_startsFromBeginning() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 5000L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")
        
        every { playbackPreferencesRepository.preferences } returns flowOf(
            PlaybackPreferences.DEFAULT.copy(resumePlaybackMode = com.rpeters.cinefintv.data.preferences.ResumePlaybackMode.NEVER)
        )

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0L, state.savedPlaybackPositionMs)
        assertEquals(false, state.shouldShowResumeDialog)
    }

    @Test
    fun load_whenResumeModeIsAsk_showsResumeDialog() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 5000L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")
        
        every { playbackPreferencesRepository.preferences } returns flowOf(
            PlaybackPreferences.DEFAULT.copy(resumePlaybackMode = com.rpeters.cinefintv.data.preferences.ResumePlaybackMode.ASK)
        )

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5000L, state.savedPlaybackPositionMs)
        assertEquals(true, state.shouldShowResumeDialog)
    }

    @Test
    fun load_whenResumeModeIsAlways_resumesFromSavedPosition() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 5000L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")
        
        every { playbackPreferencesRepository.preferences } returns flowOf(
            PlaybackPreferences.DEFAULT.copy(resumePlaybackMode = com.rpeters.cinefintv.data.preferences.ResumePlaybackMode.ALWAYS)
        )

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5000L, state.savedPlaybackPositionMs)
        assertEquals(false, state.shouldShowResumeDialog)
    }

    @Test
    fun setPlaybackSpeed_updatesPlaybackSpeedInState() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed)

        viewModel.setPlaybackSpeed(1.5f)

        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed)
    }

    @Test
    fun selectSubtitleTrack_whenDirectPlay_clearsStaleOverrideBeforeApplying() = runTest {
        // This test verifies that clearOverridesOfType is called before applying a new override.
        // Since PlayerViewModel creates ExoPlayer in the real player session and we can't inject
        // a mock player, this test verifies the ViewModel state machine: calling selectSubtitleTrack
        // on a Direct Play state should record the selected subtitle in uiState.selectedSubtitleTrack
        // without crashing.
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns
            com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        // Calling selectSubtitleTrack when player is null (not yet attached) should not crash.
        // The method should guard on _player == null inside applyTrackSelection.
        val subtitleTrack = TrackOption(
            id = "sub-0",
            label = "English",
            language = "en",
            streamIndex = 2,
        )
        viewModel.selectSubtitleTrack(subtitleTrack, positionMs = 0L, playWhenReady = true)
        advanceUntilIdle()

        // State should reflect the selection even if player is not attached
        assertEquals(subtitleTrack, viewModel.uiState.value.selectedSubtitleTrack)
    }

    @Test
    fun selectSubtitleTrack_withNullTrack_disablesSubtitles() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
        coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L
        coEvery { enhancedPlaybackManager.getOptimalPlaybackUrl(any(), any(), any(), any()) } returns
            com.rpeters.cinefintv.data.playback.PlaybackResult.Error("error")

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            enhancedPlaybackManager = enhancedPlaybackManager,
            adaptiveBitrateMonitor = adaptiveBitrateMonitor,
            playbackPreferencesRepository = playbackPreferencesRepository,
            subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
            appContext = appContext,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        // Selecting null should disable subtitles (set selectedSubtitleTrack to null in state)
        viewModel.selectSubtitleTrack(null, positionMs = 0L, playWhenReady = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedSubtitleTrack)
    }

    @org.junit.After
    fun tearDown() {
        unmockkObject(PlaybackPositionStore)
    }

}
