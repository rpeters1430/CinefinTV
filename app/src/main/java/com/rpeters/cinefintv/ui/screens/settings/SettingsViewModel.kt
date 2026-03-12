package com.rpeters.cinefintv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.preferences.AudioChannelPreference
import com.rpeters.cinefintv.data.preferences.CastPreferences
import com.rpeters.cinefintv.data.preferences.CastPreferencesRepository
import com.rpeters.cinefintv.data.preferences.CredentialSecurityPreferences
import com.rpeters.cinefintv.data.preferences.CredentialSecurityPreferencesRepository
import com.rpeters.cinefintv.data.preferences.LibraryActionsPreferences
import com.rpeters.cinefintv.data.preferences.LibraryActionsPreferencesRepository
import com.rpeters.cinefintv.data.preferences.PlaybackPreferences
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferences
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferencesRepository
import com.rpeters.cinefintv.data.preferences.SubtitleBackground
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val playback: PlaybackPreferences = PlaybackPreferences.DEFAULT,
    val subtitles: SubtitleAppearancePreferences = SubtitleAppearancePreferences.DEFAULT,
    val libraryActions: LibraryActionsPreferences = LibraryActionsPreferences.DEFAULT,
    val cast: CastPreferences = CastPreferences.DEFAULT,
    val credentialSecurity: CredentialSecurityPreferences = CredentialSecurityPreferences.DEFAULT,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subtitleAppearancePreferencesRepository: SubtitleAppearancePreferencesRepository,
    private val libraryActionsPreferencesRepository: LibraryActionsPreferencesRepository,
    private val castPreferencesRepository: CastPreferencesRepository,
    private val credentialSecurityPreferencesRepository: CredentialSecurityPreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                playbackPreferencesRepository.preferences,
                subtitleAppearancePreferencesRepository.preferencesFlow,
                libraryActionsPreferencesRepository.preferences,
                castPreferencesRepository.castPreferencesFlow,
                credentialSecurityPreferencesRepository.preferences,
            ) { playback, subtitles, libraryActions, cast, credentialSecurity ->
                SettingsUiState(
                    isLoading = false,
                    playback = playback,
                    subtitles = subtitles,
                    libraryActions = libraryActions,
                    cast = cast,
                    credentialSecurity = credentialSecurity,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        updatePreference { playbackPreferencesRepository.setAutoPlayNextEpisode(enabled) }
        _uiState.update { it.copy(playback = it.playback.copy(autoPlayNextEpisode = enabled)) }
    }

    fun setResumePlaybackMode(mode: ResumePlaybackMode) {
        updatePreference { playbackPreferencesRepository.setResumePlaybackMode(mode) }
        _uiState.update { it.copy(playback = it.playback.copy(resumePlaybackMode = mode)) }
    }

    fun setTranscodingQuality(quality: TranscodingQuality) {
        updatePreference { playbackPreferencesRepository.setTranscodingQuality(quality) }
        _uiState.update { it.copy(playback = it.playback.copy(transcodingQuality = quality)) }
    }

    fun setAudioChannels(preference: AudioChannelPreference) {
        updatePreference { playbackPreferencesRepository.setAudioChannels(preference) }
        _uiState.update { it.copy(playback = it.playback.copy(audioChannels = preference)) }
    }

    fun setSubtitleTextSize(textSize: SubtitleTextSize) {
        updatePreference { subtitleAppearancePreferencesRepository.setTextSize(textSize) }
        _uiState.update { it.copy(subtitles = it.subtitles.copy(textSize = textSize)) }
    }

    fun setSubtitleBackground(background: SubtitleBackground) {
        updatePreference { subtitleAppearancePreferencesRepository.setBackground(background) }
        _uiState.update { it.copy(subtitles = it.subtitles.copy(background = background)) }
    }

    fun setLibraryManagementActions(enabled: Boolean) {
        updatePreference { libraryActionsPreferencesRepository.setEnableManagementActions(enabled) }
        _uiState.update { it.copy(libraryActions = it.libraryActions.copy(enableManagementActions = enabled)) }
    }

    fun setCastAutoReconnect(enabled: Boolean) {
        updatePreference { castPreferencesRepository.setAutoReconnect(enabled) }
        _uiState.update { it.copy(cast = it.cast.copy(autoReconnect = enabled)) }
    }

    fun setRememberLastCastDevice(enabled: Boolean) {
        updatePreference { castPreferencesRepository.setRememberLastDevice(enabled) }
        _uiState.update { it.copy(cast = it.cast.copy(rememberLastDevice = enabled)) }
    }

    fun setRequireStrongAuthForCredentials(enabled: Boolean) {
        updatePreference {
            credentialSecurityPreferencesRepository.setRequireStrongAuthForCredentials(enabled)
        }
        _uiState.update {
            it.copy(
                credentialSecurity = it.credentialSecurity.copy(
                    requireStrongAuthForCredentials = enabled,
                ),
            )
        }
    }

    private fun updatePreference(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }
}
