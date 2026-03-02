package com.rpeters.cinefintv.ui.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.PlaybackPositionStore
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
    val streamUrl: String? = null,
    val savedPlaybackPositionMs: Long = 0L,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
    @ApplicationContext private val appContext: Context,
    val okHttpClient: OkHttpClient,
) : ViewModel() {
    private val playbackSessionId: String = UUID.randomUUID().toString()
    private val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
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
            val savedPlaybackPositionMs = PlaybackPositionStore.getPlaybackPosition(appContext, itemId)
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
                savedPlaybackPositionMs = savedPlaybackPositionMs,
                isLoading = false,
            )
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
