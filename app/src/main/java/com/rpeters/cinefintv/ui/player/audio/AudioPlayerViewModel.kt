package com.rpeters.cinefintv.ui.player.audio

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import java.net.URLDecoder
import javax.inject.Inject

data class AudioQueueItem(
    val id: String,
    val title: String,
    val artist: String?,
    val imageUrl: String?,
    val durationMs: Long,
)

data class AudioPlayerUiState(
    val isConnecting: Boolean = true,
    val isLoadingQueue: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTrackId: String? = null,
    val currentTrackImageUrl: String? = null,
    val title: String = "Audio Player",
    val subtitle: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val queueItems: List<AudioQueueItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repositories: JellyfinRepositoryCoordinator,
    private val controllerConnector: AudioControllerConnector,
    private val playbackPositionRepository: AudioPlaybackPositionRepository,
    private val mediaItemFactory: AudioMediaItemFactory,
) : ViewModel() {

    private var itemId: String = ""
    private var queueIds: List<String> = emptyList()

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState

    private var isInitialized = false

    fun init(id: String, queue: List<String>?) {
        if (isInitialized && itemId == id) return
        isInitialized = true
        itemId = id
        queueIds = queue ?: listOf(id)
        
        connectToSession()
    }

    private var controller: AudioPlaybackController? = null
    private var pollJob: Job? = null
    private var hasLoadedInitialQueue = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshFromController()
        }
    }

    init {
        // Initialization handled by init() method called after creation
    }

    fun togglePlayPause() {
        val activeController = controller ?: return
        if (activeController.isPlaying) {
            activeController.pause()
            persistCurrentPosition()
        } else {
            activeController.play()
        }
        refreshFromPlayer(activeController)
    }

    fun seekForward() {
        controller?.seekForward()
        refreshFromController()
    }

    fun seekBackward() {
        controller?.seekBack()
        refreshFromController()
    }

    fun skipToNext() {
        controller?.seekToNextMediaItem()
        refreshFromController()
    }

    fun skipToPrevious() {
        controller?.seekToPreviousMediaItem()
        refreshFromController()
    }

    fun skipToItem(index: Int) {
        controller?.seekTo(index, 0L)
        refreshFromController()
    }

    fun seekToPosition(positionMs: Long) {
        val activeController = controller ?: return
        val currentIndex = activeController.currentMediaItemIndex.takeIf { it >= 0 } ?: return
        activeController.seekTo(currentIndex, positionMs.coerceAtLeast(0L))
        refreshFromPlayer(activeController)
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(
            isConnecting = true,
            isLoadingQueue = false,
            errorMessage = null,
        )
        connectToSession(forceReload = true)
    }

    override fun onCleared() {
        persistCurrentPosition()
        pollJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }

    private fun connectToSession(forceReload: Boolean = false) {
        viewModelScope.launch {
            if (itemId.isBlank()) {
                _uiState.value = AudioPlayerUiState(
                    isConnecting = false,
                    errorMessage = "No music track was provided.",
                )
                return@launch
            }

            if (forceReload) {
                pollJob?.cancel()
                controller?.removeListener(playerListener)
                controller?.release()
                controller = null
                hasLoadedInitialQueue = false
            }

            try {
                val mediaController = controllerConnector.connect(appContext)

                controller?.removeListener(playerListener)
                controller = mediaController
                mediaController.addListener(playerListener)

                if (!hasLoadedInitialQueue) {
                    loadQueue(mediaController)
                    hasLoadedInitialQueue = true
                } else {
                    refreshFromPlayer(mediaController)
                }

                startPolling()
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    isLoadingQueue = false,
                    errorMessage = error.message ?: "Unable to connect to audio playback.",
                )
            }
        }
    }

    private suspend fun loadQueue(mediaController: AudioPlaybackController) {
        _uiState.value = _uiState.value.copy(
            isConnecting = false,
            isLoadingQueue = true,
            errorMessage = null,
        )

        val mediaItems = mutableListOf<MediaItem>()
        val uiQueueItems = mutableListOf<AudioQueueItem>()

        // Load track metadata and URLs in parallel for better performance
        coroutineScope {
            val deferredTracks = queueIds.map { trackId ->
                async {
                    val detailResult = repositories.media.getItemDetails(trackId)
                    val track = (detailResult as? ApiResult.Success)?.data
                    val streamUrl = repositories.stream.getDirectStreamUrl(trackId, "mp3")
                        ?: repositories.stream.getStreamUrl(trackId)
                    Triple(trackId, track, streamUrl)
                }
            }

            val results = deferredTracks.awaitAll()
            results.forEach { (trackId, track, streamUrl) ->
                if (streamUrl != null) {
                    val metadata = track.toMediaMetadata()
                    val imageUrl = repositories.stream.getImageUrl(trackId)
                    val durationMs = track?.runTimeTicks?.div(TICKS_PER_MILLISECOND) ?: 0L

                    mediaItems.add(
                        mediaItemFactory.create(
                            trackId = trackId,
                            streamUrl = streamUrl,
                            metadata = metadata,
                        )
                    )

                    uiQueueItems.add(
                        AudioQueueItem(
                            id = trackId,
                            title = track?.name ?: "Unknown Track",
                            artist = track?.albumArtist ?: track?.artists?.firstOrNull(),
                            imageUrl = imageUrl,
                            durationMs = durationMs,
                        )
                    )
                }
            }
        }

        if (mediaItems.isEmpty()) {
            _uiState.value = AudioPlayerUiState(
                isConnecting = false,
                errorMessage = "Unable to create a playable queue for this album.",
            )
            return
        }

        _uiState.value = _uiState.value.copy(queueItems = uiQueueItems)

        val startIndex = mediaItems.indexOfFirst { it.mediaId == itemId }.let { if (it == -1) 0 else it }
        val startPositionMs = playbackPositionRepository.getPlaybackPosition(mediaItems[startIndex].mediaId)

        mediaController.setMediaItems(mediaItems, startIndex, startPositionMs)
        mediaController.prepare()
        mediaController.play()
        refreshFromPlayer(mediaController)
    }

    private fun refreshFromController() {
        controller?.let(::refreshFromPlayer)
    }

    private fun refreshFromPlayer(player: AudioPlaybackController) {
        val currentMediaItem = player.currentMediaItem
        val metadata = currentMediaItem?.mediaMetadata
        val currentTrackId = currentMediaItem?.mediaId?.takeIf(String::isNotBlank)
        val durationMs = player.duration.takeIf { it > 0L }
            ?: metadata?.extras?.getLong(DURATION_EXTRA_MS)
            ?: 0L
        val mediaItemCount = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex.takeIf { it >= 0 } ?: 0
        
        // Find image for current track from our pre-loaded queue items
        val currentTrackImageUrl = _uiState.value.queueItems.find { it.id == currentTrackId }?.imageUrl

        _uiState.value = _uiState.value.copy(
            isConnecting = false,
            isLoadingQueue = false,
            isPlaying = player.isPlaying,
            currentTrackId = currentTrackId,
            currentTrackImageUrl = currentTrackImageUrl,
            title = metadata?.title?.toString().takeUnless { it.isNullOrBlank() } ?: "Audio Player",
            subtitle = buildSubtitle(metadata),
            artistName = metadata?.artist?.toString(),
            albumName = metadata?.albumTitle?.toString(),
            year = metadata?.extras?.getInt(YEAR_EXTRA)?.takeIf { it > 0 },
            genre = metadata?.genre?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            queueSize = mediaItemCount,
            currentIndex = currentIndex,
            canSkipNext = mediaItemCount > 1 && currentIndex < mediaItemCount - 1,
            canSkipPrevious = mediaItemCount > 1 && currentIndex > 0,
            errorMessage = null,
        )
    }

    private fun startPolling() {
        val activeController = controller ?: return
        if (activeController.mediaItemCount == 0) return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                refreshFromController()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun persistCurrentPosition() {
        val state = _uiState.value
        val currentTrackId = state.currentTrackId ?: return
        viewModelScope.launch {
            playbackPositionRepository.savePlaybackPosition(currentTrackId, state.positionMs)
        }
    }

    private fun BaseItemDto?.toMediaMetadata(): MediaMetadata {
        val title = this?.name ?: "Unknown Track"
        val artist = this?.albumArtist ?: this?.artists?.firstOrNull()
        val albumTitle = this?.album ?: this?.albumId?.toString()
        val durationMs = this?.runTimeTicks?.div(TICKS_PER_MILLISECOND)
        val year = this?.productionYear
        val genre = this?.genres?.firstOrNull()

        return MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(albumTitle)
            .setGenre(genre)
            .setExtras(
                Bundle().apply {
                    putLong(DURATION_EXTRA_MS, durationMs ?: 0L)
                    putInt(YEAR_EXTRA, year ?: 0)
                },
            )
            .build()
    }

    private fun buildSubtitle(metadata: MediaMetadata?): String? {
        val artist = metadata?.artist?.toString().orEmpty()
        val album = metadata?.albumTitle?.toString().orEmpty()

        return listOf(artist, album)
            .filter(String::isNotBlank)
            .joinToString(" - ")
            .takeIf(String::isNotBlank)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val TICKS_PER_MILLISECOND = 10_000L
        private const val DURATION_EXTRA_MS = "duration_ms"
        private const val YEAR_EXTRA = "production_year"
    }
}
