package com.rpeters.cinefintv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.rpeters.cinefintv.data.PlaybackPositionStore
import com.rpeters.cinefintv.data.playback.EnhancedPlaybackManager
import com.rpeters.cinefintv.data.playback.PlaybackResult
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID
import javax.inject.Inject

data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val streamIndex: Int?,
)

data class PlayerUiState(
    val itemId: String = "",
    val title: String = "Player",
    val logoUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val streamUrl: String? = null,
    val savedPlaybackPositionMs: Long = 0L,
    val isEpisodicContent: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioTrack: TrackOption? = null,
    val selectedSubtitleTrack: TrackOption? = null,
    val playbackSpeed: Float = 1.0f,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
    private val jellyfinRepository: JellyfinRepository,
    private val enhancedPlaybackManager: EnhancedPlaybackManager,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    @param:ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {
    private val playbackSessionId: String = UUID.randomUUID().toString()
    val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            itemId = itemId,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private var activeMediaSourceId: String? = null
    private var activePlaySessionId: String? = null
    private var activePlayMethod: PlayMethod = PlayMethod.DIRECT_PLAY
    private var currentItem: BaseItemDto? = null
    private var playbackStartReported = false

    init {
        viewModelScope.launch {
            playbackPreferencesRepository.preferences.collectLatest { prefs ->
                _uiState.value = _uiState.value.copy(autoPlayNextEpisode = prefs.autoPlayNextEpisode)
            }
        }
        load()
    }

    fun load() {
        if (itemId.isBlank()) {
            _uiState.value = PlayerUiState(
                itemId = itemId,
                isLoading = false,
                errorMessage = "No playable item was provided.",
            )
            return
        }

        viewModelScope.launch {
            val localPlaybackPositionMs = PlaybackPositionStore.getPlaybackPosition(appContext, itemId)
            val detailResult = repositories.media.getItemDetails(itemId)
            val item = (detailResult as? ApiResult.Success)?.data
            currentItem = item
            val serverPlaybackPositionMs = item?.userData?.playbackPositionTicks
                ?.takeIf { it > 0L }
                ?.div(TICKS_PER_MILLISECOND)
                ?: 0L
            val savedPlaybackPositionMs = maxOf(localPlaybackPositionMs, serverPlaybackPositionMs)
            val title = item?.getDisplayTitle() ?: "Now Playing"
            val playbackInfo = runCatching { jellyfinRepository.getPlaybackInfo(itemId) }.getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
            val audioTracks = mediaSource.toAudioTrackOptions()
            val subtitleTracks = mediaSource.toSubtitleTrackOptions()
            activeMediaSourceId = mediaSource?.id
            activePlaySessionId = playbackInfo?.playSessionId

            val streamUrl = resolvePlaybackUrl(
                item = item,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            ) ?: repositories.stream.getStreamUrl(itemId)

            if (streamUrl == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to create a stream for this item.",
                )
                return@launch
            }

            // Prefer the parent series logo for episodes, otherwise use the item's own logo.
            val logoUrl = if (item != null) {
                item.seriesId?.let { seriesId ->
                    val seriesResult = repositories.media.getSeriesDetails(seriesId.toString())
                    (seriesResult as? ApiResult.Success)?.data?.let { series ->
                        repositories.stream.getLogoUrl(series)
                    }
                } ?: repositories.stream.getLogoUrl(item)
            } else null

            val isEpisodicContent = item?.type == BaseItemKind.EPISODE
            var seasonNumber: Int? = null
            var episodeNumber: Int? = null
            
            if (item != null && isEpisodicContent) {
                seasonNumber = item.parentIndexNumber
                episodeNumber = item.indexNumber
            }

            var nextEpisodeId: String? = null
            var nextEpisodeTitle: String? = null

            if (isEpisodicContent) {
                val nextResult = repositories.media.getNextEpisode(itemId)
                if (nextResult is ApiResult.Success) {
                    val nextEpisode = nextResult.data
                    if (nextEpisode != null) {
                        nextEpisodeId = nextEpisode.id.toString()
                        nextEpisodeTitle = nextEpisode.getDisplayTitle()
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                title = title,
                logoUrl = logoUrl,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                streamUrl = streamUrl,
                savedPlaybackPositionMs = savedPlaybackPositionMs,
                isEpisodicContent = isEpisodicContent,
                nextEpisodeId = nextEpisodeId,
                nextEpisodeTitle = nextEpisodeTitle,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                isLoading = false,
            )
        }
    }

    fun setupPlayer(context: Context): ExoPlayer {
        _player?.let { return it }
        
        val factory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient))
        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(factory)
            .build()
            .apply {
                val streamUrl = uiState.value.streamUrl
                if (streamUrl != null) {
                    setMediaItem(MediaItem.fromUri(streamUrl))
                    val resumePositionMs = uiState.value.savedPlaybackPositionMs.coerceAtLeast(0L)
                    if (resumePositionMs > 0L) {
                        seekTo(resumePositionMs)
                    }
                    reportPlaybackStart(resumePositionMs)
                    prepare()
                    playWhenReady = true
                }
            }
        _player = newPlayer
        return newPlayer
    }

    fun selectAudioTrack(track: TrackOption, positionMs: Long, playWhenReady: Boolean) {
        _uiState.value = _uiState.value.copy(selectedAudioTrack = track)
        reloadStream(positionMs = positionMs, playWhenReady = playWhenReady)
    }

    fun selectSubtitleTrack(track: TrackOption?, positionMs: Long, playWhenReady: Boolean) {
        _uiState.value = _uiState.value.copy(selectedSubtitleTrack = track)
        reloadStream(positionMs = positionMs, playWhenReady = playWhenReady)
    }

    private fun reloadStream(positionMs: Long, playWhenReady: Boolean) {
        val currentItemId = uiState.value.itemId
        if (currentItemId.isBlank()) return

        viewModelScope.launch {
            val streamUrl = resolvePlaybackUrl(
                item = currentItem,
                audioStreamIndex = uiState.value.selectedAudioTrack?.streamIndex,
                subtitleStreamIndex = uiState.value.selectedSubtitleTrack?.streamIndex,
            ) ?: repositories.stream.getTranscodedStreamUrl(
                itemId = currentItemId,
                mediaSourceId = activeMediaSourceId,
                playSessionId = activePlaySessionId,
                audioStreamIndex = uiState.value.selectedAudioTrack?.streamIndex,
                subtitleStreamIndex = uiState.value.selectedSubtitleTrack?.streamIndex,
            ) ?: repositories.stream.getStreamUrl(currentItemId)
                ?: return@launch

            _uiState.value = _uiState.value.copy(streamUrl = streamUrl)
            _player?.apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                if (positionMs > 0L) {
                    seekTo(positionMs)
                }
                reportPlaybackStart(positionMs)
                prepare()
                this.playWhenReady = playWhenReady
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _player?.release()
        _player = null
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        viewModelScope.launch {
            playbackPreferencesRepository.setAutoPlayNextEpisode(enabled)
            _uiState.value = _uiState.value.copy(autoPlayNextEpisode = enabled)
        }
    }

    fun onAudioTrackSelected(track: TrackOption?) {
        _uiState.value = _uiState.value.copy(selectedAudioTrack = track)
    }

    fun onSubtitleTrackSelected(track: TrackOption?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleTrack = track)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        _player?.setPlaybackSpeed(speed)
    }

    fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        val message = when (error.errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network error. Please check your connection."
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Codec error. This device may not support this media format."
            else -> "Playback failed: ${error.localizedMessage}"
        }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = message,
        )
    }

    suspend fun getNextEpisodeId(): String? {
        if (!uiState.value.isEpisodicContent || !uiState.value.autoPlayNextEpisode) return null
        return when (val result = repositories.media.getNextEpisode(itemId)) {
            is ApiResult.Success -> result.data?.id?.toString()
            else -> null
        }
    }

    fun savePlaybackPosition(
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean = true,
        shouldSyncToServer: Boolean = true,
    ) {
        if (itemId.isBlank()) return

        viewModelScope.launch {
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * COMPLETION_THRESHOLD_PERCENT)
            val persistedPosition = if (isCompleted) 0L else positionMs.coerceAtLeast(0L)
            PlaybackPositionStore.savePlaybackPosition(appContext, itemId, persistedPosition)

            if (shouldSyncToServer) {
                val positionTicks = if (persistedPosition <= 0L) null else persistedPosition * TICKS_PER_MILLISECOND
                val sessionId = activePlaySessionId?.takeIf { it.isNotBlank() } ?: playbackSessionId
                if (isCompleted) {
                    repositories.user.reportPlaybackStopped(
                        itemId = itemId,
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        mediaSourceId = activeMediaSourceId,
                    )
                } else {
                    repositories.user.reportPlaybackProgress(
                        itemId = itemId,
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        mediaSourceId = activeMediaSourceId,
                        playMethod = activePlayMethod,
                        isPaused = isPaused,
                    )
                }
            }
        }
    }

    private suspend fun resolvePlaybackUrl(
        item: BaseItemDto?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ): String? {
        item ?: return null

        return when (
            val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(
                item = item,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
            )
        ) {
            is PlaybackResult.DirectPlay -> {
                activePlayMethod = PlayMethod.DIRECT_PLAY
                val nextSessionId = playbackResult.playSessionId ?: activePlaySessionId ?: playbackSessionId
                if (nextSessionId != activePlaySessionId) {
                    playbackStartReported = false
                }
                activePlaySessionId = nextSessionId
                playbackResult.url
            }
            is PlaybackResult.Transcoding -> {
                activePlayMethod = if (playbackResult.isDirectStream) {
                    PlayMethod.DIRECT_STREAM
                } else {
                    PlayMethod.TRANSCODE
                }
                val nextSessionId = playbackResult.playSessionId ?: activePlaySessionId ?: playbackSessionId
                if (nextSessionId != activePlaySessionId) {
                    playbackStartReported = false
                }
                activePlaySessionId = nextSessionId
                playbackResult.url
            }
            is PlaybackResult.Error -> null
        }
    }

    private fun reportPlaybackStart(positionMs: Long) {
        if (playbackStartReported || itemId.isBlank()) return

        playbackStartReported = true
        val sessionId = activePlaySessionId?.takeIf { it.isNotBlank() } ?: playbackSessionId
        val positionTicks = positionMs.takeIf { it > 0L }?.times(TICKS_PER_MILLISECOND)

        viewModelScope.launch {
            repositories.user.reportPlaybackStart(
                itemId = itemId,
                sessionId = sessionId,
                positionTicks = positionTicks,
                mediaSourceId = activeMediaSourceId,
                playMethod = activePlayMethod,
                isPaused = false,
            )
        }
    }

    companion object {
        private const val COMPLETION_THRESHOLD_PERCENT = 0.95
        private const val TICKS_PER_MILLISECOND = 10_000L
    }
}

private fun MediaSourceInfo?.toAudioTrackOptions(): List<TrackOption> =
    this?.mediaStreams
        .orEmpty()
        .filter { it.type == MediaStreamType.AUDIO }
        .mapIndexed { index, stream ->
            TrackOption(
                id = "audio-${stream.index}",
                label = stream.toTrackLabel("Audio", index + 1),
                language = stream.language,
                streamIndex = stream.index,
            )
        }

private fun MediaSourceInfo?.toSubtitleTrackOptions(): List<TrackOption> =
    this?.mediaStreams
        .orEmpty()
        .filter { it.type == MediaStreamType.SUBTITLE }
        .mapIndexed { index, stream ->
            TrackOption(
                id = "sub-${stream.index}",
                label = stream.toTrackLabel("Subtitle", index + 1),
                language = stream.language,
                streamIndex = stream.index,
            )
        }

private fun MediaStream.toTrackLabel(prefix: String, fallbackNumber: Int): String {
    val parts = listOfNotNull(
        title?.takeIf { it.isNotBlank() },
        displayTitle?.takeIf { it.isNotBlank() },
        language?.takeIf { it.isNotBlank() },
        codec?.takeIf { it.isNotBlank() }?.uppercase(),
    ).distinct()

    return if (parts.isNotEmpty()) {
        parts.joinToString(" • ")
    } else {
        "$prefix $fallbackNumber"
    }
}
