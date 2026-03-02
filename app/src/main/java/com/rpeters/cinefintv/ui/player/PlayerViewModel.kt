package com.rpeters.cinefintv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
)

data class PlayerUiState(
    val itemId: String = "",
    val title: String = "Player",
    val streamUrl: String? = null,
    val isEpisodicContent: Boolean = false,
    val selectedAudioTrack: TrackOption? = null,
    val selectedSubtitleTrack: TrackOption? = null,
    val autoPlayNextEpisode: Boolean = true,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    val okHttpClient: OkHttpClient,
) : ViewModel() {
    private var itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            itemId = itemId,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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
            val streamUrl = repositories.stream.getStreamUrl(itemId)
            if (streamUrl == null) {
                _uiState.value = PlayerUiState(
                    itemId = itemId,
                    isLoading = false,
                    errorMessage = "Unable to create a stream for this item.",
                )
                return@launch
            }

            val detailResult = repositories.media.getItemDetails(itemId)
            val title = when (detailResult) {
                is ApiResult.Success -> detailResult.data.getDisplayTitle()
                else -> "Now Playing"
            }
            val isEpisodicContent = when (detailResult) {
                is ApiResult.Success -> detailResult.data.type == BaseItemKind.EPISODE
                else -> false
            }

            _uiState.value = PlayerUiState(
                itemId = itemId,
                title = title,
                streamUrl = streamUrl,
                isEpisodicContent = isEpisodicContent,
                autoPlayNextEpisode = _uiState.value.autoPlayNextEpisode,
                isLoading = false,
            )
        }
    }

    fun onAudioTrackSelected(track: TrackOption?) {
        _uiState.value = _uiState.value.copy(selectedAudioTrack = track)
    }

    fun onSubtitleTrackSelected(track: TrackOption?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleTrack = track)
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        viewModelScope.launch {
            playbackPreferencesRepository.setAutoPlayNextEpisode(enabled)
        }
    }

    fun onPlaybackCompleted() {
        val state = _uiState.value
        if (!state.autoPlayNextEpisode || !state.isEpisodicContent) return

        viewModelScope.launch {
            val nextEpisodeResult = repositories.media.getNextEpisode(state.itemId)
            if (nextEpisodeResult is ApiResult.Success) {
                val nextId = nextEpisodeResult.data?.id?.toString().orEmpty()
                if (nextId.isNotBlank()) {
                    itemId = nextId
                    load()
                }
            }
        }
    }
}
