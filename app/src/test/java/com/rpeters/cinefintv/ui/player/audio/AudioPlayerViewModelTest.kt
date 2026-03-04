package com.rpeters.cinefintv.ui.player.audio

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakePlayerRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appContext: Context = mockk(relaxed = true)

    @Test
    fun init_withMissingItemId_setsFriendlyError() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        val controller = FakeAudioPlaybackController(
            currentMediaItem = null,
            mediaItemCount = 0,
            currentMediaItemIndex = 0,
            currentPosition = 0L,
            duration = 0L,
            isPlaying = false,
        )
        val connector = FakeAudioControllerConnector(controller)
        val positionRepository = FakeAudioPlaybackPositionRepository(savedPositionMs = 12_000L)
        val mediaItemFactory = FakeAudioMediaItemFactory()

        val viewModel = AudioPlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "", "queue" to "")),
            appContext = appContext,
            repositories = fakeRepositories.coordinator,
            controllerConnector = connector,
            playbackPositionRepository = positionRepository,
            mediaItemFactory = mediaItemFactory,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No music track was provided.", state.errorMessage)
        assertEquals(0, controller.playCalls)
        clearViewModel(viewModel)
    }

    @Test
    fun playAndSeekControls_delegateToController() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        val controller = FakeAudioPlaybackController(
            currentMediaItem = buildMediaItem(
                trackId = TRACK_ONE_ID,
                title = "Track One",
                artist = "Artist",
                album = "Album",
                durationMs = 120_000L,
            ),
            mediaItemCount = 0,
            currentMediaItemIndex = 0,
            currentPosition = 10_000L,
            duration = 120_000L,
            isPlaying = false,
        )
        val connector = FakeAudioControllerConnector(controller)
        val positionRepository = FakeAudioPlaybackPositionRepository()
        val mediaItemFactory = FakeAudioMediaItemFactory()

        every { fakeRepositories.stream.getDirectStreamUrl(any(), any()) } answers {
            "https://stream/${firstArg<String>()}.mp3"
        }
        coEvery { fakeRepositories.media.getItemDetails(any()) } returns ApiResult.Error("unavailable")

        val viewModel = AudioPlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to TRACK_ONE_ID, "queue" to TRACK_ONE_ID)),
            appContext = appContext,
            repositories = fakeRepositories.coordinator,
            controllerConnector = connector,
            playbackPositionRepository = positionRepository,
            mediaItemFactory = mediaItemFactory,
        )
        advanceUntilIdle()

        controller.resetCallCounters()

        viewModel.seekForward()
        viewModel.seekBackward()
        viewModel.skipToNext()
        viewModel.skipToPrevious()
        advanceUntilIdle()

        assertEquals(1, controller.seekForwardCalls)
        assertEquals(1, controller.seekBackCalls)
        assertEquals(1, controller.seekNextCalls)
        assertEquals(1, controller.seekPreviousCalls)
        clearViewModel(viewModel)
    }

    @Test
    fun init_withQueue_loadsMediaItemsAndStartsPlayback() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        val controller = FakeAudioPlaybackController(
            currentMediaItem = null,
            mediaItemCount = 0,
            currentMediaItemIndex = 0,
            currentPosition = 0L,
            duration = 0L,
            isPlaying = false,
        )
        val connector = FakeAudioControllerConnector(controller)
        val positionRepository = FakeAudioPlaybackPositionRepository(savedPositionMs = 8_000L)
        val mediaItemFactory = FakeAudioMediaItemFactory()

        every { fakeRepositories.stream.getDirectStreamUrl(any(), any()) } answers {
            "https://stream/${firstArg<String>()}.mp3"
        }
        coEvery { fakeRepositories.media.getItemDetails(any()) } returns ApiResult.Error("unavailable")

        val viewModel = AudioPlayerViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "itemId" to TRACK_TWO_ID,
                    "queue" to "$TRACK_ONE_ID,$TRACK_TWO_ID",
                ),
            ),
            appContext = appContext,
            repositories = fakeRepositories.coordinator,
            controllerConnector = connector,
            playbackPositionRepository = positionRepository,
            mediaItemFactory = mediaItemFactory,
        )
        advanceUntilIdle()

        assertEquals(2, controller.lastSetMediaItems.size)
        assertEquals(TRACK_ONE_ID, controller.lastSetMediaItems[0].mediaId)
        assertEquals(TRACK_TWO_ID, controller.lastSetMediaItems[1].mediaId)
        assertEquals(1, controller.lastStartIndex)
        assertEquals(8_000L, controller.lastStartPositionMs)
        assertEquals(1, controller.prepareCalls)
        assertEquals(1, controller.playCalls)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        clearViewModel(viewModel)
    }

    private fun buildMediaItem(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(trackId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setExtras(android.os.Bundle().apply { putLong("duration_ms", durationMs) })
                    .build(),
            )
            .build()
    }

    companion object {
        private val TRACK_ONE_ID = UUID.randomUUID().toString()
        private val TRACK_TWO_ID = UUID.randomUUID().toString()
    }

    private fun clearViewModel(viewModel: AudioPlayerViewModel) {
        val method = AudioPlayerViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}

private class FakeAudioControllerConnector(
    private val controller: AudioPlaybackController,
) : AudioControllerConnector {
    override suspend fun connect(context: Context): AudioPlaybackController = controller
}

private class FakeAudioPlaybackController(
    override var currentMediaItem: MediaItem?,
    override var mediaItemCount: Int,
    override var currentMediaItemIndex: Int,
    override var currentPosition: Long,
    override var duration: Long,
    override var isPlaying: Boolean,
) : AudioPlaybackController {
    var lastSetMediaItems: List<MediaItem> = emptyList()
    var lastStartIndex: Int = -1
    var lastStartPositionMs: Long = -1L
    var prepareCalls: Int = 0
    var playCalls: Int = 0
    var pauseCalls: Int = 0
    var seekForwardCalls: Int = 0
    var seekBackCalls: Int = 0
    var seekNextCalls: Int = 0
    var seekPreviousCalls: Int = 0

    override fun addListener(listener: Player.Listener) = Unit
    override fun removeListener(listener: Player.Listener) = Unit
    override fun release() = Unit

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        lastSetMediaItems = mediaItems
        lastStartIndex = startIndex
        lastStartPositionMs = startPositionMs
    }

    override fun prepare() {
        prepareCalls += 1
    }

    override fun play() {
        playCalls += 1
        isPlaying = true
    }

    override fun pause() {
        pauseCalls += 1
        isPlaying = false
    }

    override fun seekForward() {
        seekForwardCalls += 1
    }

    override fun seekBack() {
        seekBackCalls += 1
    }

    override fun seekToNextMediaItem() {
        seekNextCalls += 1
    }

    override fun seekToPreviousMediaItem() {
        seekPreviousCalls += 1
    }

    fun resetCallCounters() {
        prepareCalls = 0
        playCalls = 0
        pauseCalls = 0
        seekForwardCalls = 0
        seekBackCalls = 0
        seekNextCalls = 0
        seekPreviousCalls = 0
    }
}

private class FakeAudioPlaybackPositionRepository(
    private val savedPositionMs: Long = 0L,
) : AudioPlaybackPositionRepository {
    override suspend fun getPlaybackPosition(itemId: String): Long = savedPositionMs

    override suspend fun savePlaybackPosition(itemId: String, positionMs: Long) = Unit
}

private class FakeAudioMediaItemFactory : AudioMediaItemFactory {
    override fun create(
        trackId: String,
        streamUrl: String,
        metadata: MediaMetadata,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(trackId)
            .setMediaMetadata(metadata)
            .build()
    }
}
