package com.rpeters.cinefintv.ui.player.audio

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeMusicRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appContext: Context = mockk(relaxed = true)

    companion object {
        private const val TRACK_ONE_ID = "00000000-0000-0000-0000-000000000001"
        private const val TRACK_TWO_ID = "00000000-0000-0000-0000-000000000002"
    }

    @Test
    fun init_withoutItemId_showsError() = runTest {
        val fakeRepositories = FakeMusicRepositories()
        val controller = mockk<AudioPlaybackController>(relaxed = true)
        val connector = FakeAudioControllerConnector(controller)
        val positionRepository = FakeAudioPlaybackPositionRepository()
        val mediaItemFactory = FakeAudioMediaItemFactory()

        val viewModel = AudioPlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "")),
            appContext = appContext,
            repositories = fakeRepositories.coordinator,
            controllerConnector = connector,
            playbackPositionRepository = positionRepository,
            mediaItemFactory = mediaItemFactory,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isConnecting)
        assertEquals("No music track was provided.", state.errorMessage)
    }

    @Test
    fun togglePlayPause_callsController() = runTest {
        val fakeRepositories = FakeMusicRepositories()
        val controller = FakeAudioPlaybackController(
            currentMediaItem = null,
            mediaItemCount = 1,
            currentMediaItemIndex = 0,
            currentPosition = 0L,
            duration = 100_000L,
            isPlaying = false,
        )
        val connector = FakeAudioControllerConnector(controller)
        val positionRepository = FakeAudioPlaybackPositionRepository()
        val mediaItemFactory = FakeAudioMediaItemFactory()

        every { fakeRepositories.stream.getDirectStreamUrl(any(), any()) } returns "https://stream/1.mp3"
        coEvery { fakeRepositories.media.getItemDetails(any()) } returns ApiResult.Success(BaseItemDto(id = UUID.randomUUID(), name = "Track", type = BaseItemKind.AUDIO))

        val viewModel = AudioPlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to TRACK_ONE_ID)),
            appContext = appContext,
            repositories = fakeRepositories.coordinator,
            controllerConnector = connector,
            playbackPositionRepository = positionRepository,
            mediaItemFactory = mediaItemFactory,
        )
        advanceUntilIdle()

        viewModel.togglePlayPause()
        assertFalse(controller.isPlaying)
        assertEquals(1, controller.pauseCalls)

        viewModel.togglePlayPause()
        assertTrue(controller.isPlaying)
        assertEquals(2, controller.playCalls)
        clearViewModel(viewModel)
    }

    @Test
    fun skipAndSeek_callsController() = runTest {
        val fakeRepositories = FakeMusicRepositories()
        val controller = FakeAudioPlaybackController(
            currentMediaItem = null,
            mediaItemCount = 2,
            currentMediaItemIndex = 0,
            currentPosition = 0L,
            duration = 100_000L,
            isPlaying = true,
        )
        val connector = FakeAudioControllerConnector(controller)
        val positionRepository = FakeAudioPlaybackPositionRepository()
        val mediaItemFactory = FakeAudioMediaItemFactory()

        every { fakeRepositories.stream.getDirectStreamUrl(any(), any()) } returns "https://stream/1.mp3"
        coEvery { fakeRepositories.media.getItemDetails(any()) } returns ApiResult.Success(BaseItemDto(id = UUID.randomUUID(), name = "Track", type = BaseItemKind.AUDIO))

        val viewModel = AudioPlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to TRACK_ONE_ID)),
            appContext = appContext,
            repositories = fakeRepositories.coordinator,
            controllerConnector = connector,
            playbackPositionRepository = positionRepository,
            mediaItemFactory = mediaItemFactory,
        )
        advanceUntilIdle()

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
        val fakeRepositories = FakeMusicRepositories()
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
            val id = it.invocation.args[0] as String
            "https://stream/$id.mp3"
        }
        coEvery { fakeRepositories.media.getItemDetails(any()) } answers {
            val id = it.invocation.args[0] as String
            ApiResult.Success(BaseItemDto(id = UUID.fromString(id), name = "Track $id", type = BaseItemKind.AUDIO))
        }

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
        clearViewModel(viewModel)
    }

    private fun clearViewModel(viewModel: AudioPlayerViewModel) {
        val method = viewModel.javaClass.getDeclaredMethod("onCleared")
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

    override fun seekTo(index: Int, positionMs: Long) {
        currentMediaItemIndex = index
        currentPosition = positionMs
    }

    override fun getMediaController(): androidx.media3.session.MediaController? = null

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
