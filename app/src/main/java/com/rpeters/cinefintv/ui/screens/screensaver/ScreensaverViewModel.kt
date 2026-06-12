package com.rpeters.cinefintv.ui.screens.screensaver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.preferences.ScreensaverPreferences
import com.rpeters.cinefintv.data.preferences.ScreensaverPreferencesRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.player.audio.AudioControllerConnector
import com.rpeters.cinefintv.ui.player.audio.AudioPlaybackController
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

data class NowPlayingTrack(
    val title: String,
    val artist: String?,
    val album: String?,
    val imageUrl: String?,
    val isPlaying: Boolean,
)

data class ScreensaverUiState(
    val isIdle: Boolean = false,
    val backdropUrls: List<String> = emptyList(),
    val nowPlaying: NowPlayingTrack? = null,
    val preferences: ScreensaverPreferences = ScreensaverPreferences.DEFAULT,
)

@HiltViewModel
class ScreensaverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screensaverPrefsRepository: ScreensaverPreferencesRepository,
    private val repositoryCoordinator: JellyfinRepositoryCoordinator,
    private val audioControllerConnector: AudioControllerConnector,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreensaverUiState())
    val uiState: StateFlow<ScreensaverUiState> = _uiState.asStateFlow()

    private val _lastActivityTime = MutableStateFlow(System.currentTimeMillis())
    private val _isAuthenticated = MutableStateFlow(false)
    private val _isVideoPlaying = MutableStateFlow(false)

    private var audioController: AudioPlaybackController? = null
    private var idleCheckJob: Job? = null
    private var audioUpdateJob: Job? = null
    private var slideshowJob: Job? = null

    init {
        // Observe preferences
        viewModelScope.launch {
            screensaverPrefsRepository.screensaverPreferencesFlow.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }

        // Start idle monitoring loop
        startIdleMonitoring()

        // Connect to background audio player to observe now playing
        connectAudioController()
    }

    /**
     * Call this whenever user input is detected.
     */
    fun onUserActivity() {
        _lastActivityTime.value = System.currentTimeMillis()
        if (_uiState.value.isIdle) {
            _uiState.update { it.copy(isIdle = false) }
        }
    }

    /**
     * Update screensaver eligibility based on authentication and active video playback.
     */
    fun setEligibility(isAuthenticated: Boolean, isVideoPlaying: Boolean) {
        _isAuthenticated.value = isAuthenticated
        _isVideoPlaying.value = isVideoPlaying
        if (!isAuthenticated || isVideoPlaying) {
            _uiState.update { it.copy(isIdle = false) }
        }
    }

    private fun startIdleMonitoring() {
        idleCheckJob?.cancel()
        idleCheckJob = viewModelScope.launch {
            combine(
                screensaverPrefsRepository.screensaverPreferencesFlow,
                _isAuthenticated,
                _isVideoPlaying,
                _lastActivityTime
            ) { prefs, auth, videoPlaying, lastActivity ->
                val eligible = prefs.isEnabled && auth && !videoPlaying
                Triple(eligible, prefs.idleTimeoutMinutes, lastActivity)
            }.collectLatest { (eligible, timeoutMinutes, lastActivity) ->
                if (!eligible) {
                    _uiState.update { it.copy(isIdle = false) }
                    stopSlideshow()
                    return@collectLatest
                }

                val timeoutMs = timeoutMinutes * 60 * 1000L
                while (true) {
                    val now = System.currentTimeMillis()
                    val idleTime = now - lastActivity
                    val remaining = timeoutMs - idleTime
                    if (remaining <= 0) {
                        _uiState.update { it.copy(isIdle = true) }
                        startSlideshow()
                        break
                    }
                    delay(remaining.coerceAtLeast(1000L))
                }
            }
        }
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            // Fetch backdrops if empty
            if (_uiState.value.backdropUrls.isEmpty()) {
                fetchBackdrops()
            }
            // Keep slideshow active
            while (true) {
                delay(12000L) // Wait 12 seconds per slide (leaves room for crossfade + Ken Burns)
            }
        }
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
    }

    private suspend fun fetchBackdrops() {
        try {
            val result = repositoryCoordinator.media.getRecentlyAdded(limit = 50)
            if (result is ApiResult.Success) {
                val urls = result.data.mapNotNull { item ->
                    repositoryCoordinator.stream.getBackdropUrl(item)
                }.distinct().shuffled()

                _uiState.update { it.copy(backdropUrls = urls) }
                SecureLogger.d("ScreensaverVM", "Fetched ${urls.size} screensaver backdrops")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SecureLogger.w("ScreensaverVM", "Failed to fetch backdrops", e)
        }
    }

    private fun connectAudioController() {
        viewModelScope.launch {
            try {
                val controller = audioControllerConnector.connect(context)
                audioController = controller
                startAudioObserving(controller)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                SecureLogger.d("ScreensaverVM", "No active audio service found yet.")
            }
        }
    }

    private fun startAudioObserving(controller: AudioPlaybackController) {
        audioUpdateJob?.cancel()
        audioUpdateJob = viewModelScope.launch {
            while (true) {
                val mediaItem = controller.currentMediaItem
                val isPlaying = controller.isPlaying && controller.mediaItemCount > 0
                if (mediaItem != null) {
                    val metadata = mediaItem.mediaMetadata
                    val title = metadata.title?.toString() ?: "Unknown Track"
                    val artist = metadata.artist?.toString()
                    val album = metadata.albumTitle?.toString()
                    val imageUrl = repositoryCoordinator.stream.getImageUrl(mediaItem.mediaId)

                    _uiState.update {
                        it.copy(
                            nowPlaying = NowPlayingTrack(
                                title = title,
                                artist = artist,
                                album = album,
                                imageUrl = imageUrl,
                                isPlaying = isPlaying,
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(nowPlaying = null) }
                }
                delay(2000L) // Refresh every 2 seconds
            }
        }
    }

    public override fun onCleared() {
        idleCheckJob?.cancel()
        audioUpdateJob?.cancel()
        slideshowJob?.cancel()
        audioController?.release()
        super.onCleared()
    }
}
