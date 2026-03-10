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
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
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
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID
import javax.inject.Inject

data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
)

data class PlayerUiState(
    val itemId: String = "",
    val title: String = "Player",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val streamUrl: String? = null,
    val savedPlaybackPositionMs: Long = 0L,
    val isEpisodicContent: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
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
            val savedPlaybackPositionMs = PlaybackPositionStore.getPlaybackPosition(appContext, itemId)
            val streamUrl = repositories.stream.getStreamUrl(itemId)
            if (streamUrl == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to create a stream for this item.",
                )
                return@launch
            }

            val detailResult = repositories.media.getItemDetails(itemId)
            val item = (detailResult as? ApiResult.Success)?.data
            val title = item?.getDisplayTitle() ?: "Now Playing"
            
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
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                streamUrl = streamUrl,
                savedPlaybackPositionMs = savedPlaybackPositionMs,
                isEpisodicContent = isEpisodicContent,
                nextEpisodeId = nextEpisodeId,
                nextEpisodeTitle = nextEpisodeTitle,
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
                    prepare()
                    playWhenReady = true
                }
            }
        _player = newPlayer
        return newPlayer
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
                if (isCompleted) {
                    repositories.user.reportPlaybackStopped(
                        itemId = itemId,
                        sessionId = playbackSessionId,
                        positionTicks = positionTicks,
                    )
                } else {
                    repositories.user.reportPlaybackProgress(
                        itemId = itemId,
                        sessionId = playbackSessionId,
                        positionTicks = positionTicks,
                        isPaused = isPaused,
                    )
                }
            }
        }
    }

    companion object {
        private const val COMPLETION_THRESHOLD_PERCENT = 0.95
        private const val TICKS_PER_MILLISECOND = 10_000L
    }
}
