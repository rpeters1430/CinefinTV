package com.rpeters.cinefintv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.preferences.AccentColor
import com.rpeters.cinefintv.data.preferences.AudioChannelPreference
import com.rpeters.cinefintv.data.preferences.CastPreferences
import com.rpeters.cinefintv.data.preferences.CastPreferencesRepository
import com.rpeters.cinefintv.data.preferences.ContrastLevel
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
import com.rpeters.cinefintv.data.preferences.SubtitleFont
import com.rpeters.cinefintv.data.preferences.SubtitleTextSize
import com.rpeters.cinefintv.data.preferences.SubtitleTextColor
import com.rpeters.cinefintv.data.preferences.ThemeMode
import com.rpeters.cinefintv.data.preferences.ThemePreferences
import com.rpeters.cinefintv.data.preferences.ThemePreferencesRepository
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.data.preferences.VideoSeekIncrement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val appearance: ThemePreferences = ThemePreferences.DEFAULT,
    val playback: PlaybackPreferences = PlaybackPreferences.DEFAULT,
    val subtitles: SubtitleAppearancePreferences = SubtitleAppearancePreferences.DEFAULT,
    val libraryActions: LibraryActionsPreferences = LibraryActionsPreferences.DEFAULT,
    val cast: CastPreferences = CastPreferences.DEFAULT,
    val credentialSecurity: CredentialSecurityPreferences = CredentialSecurityPreferences.DEFAULT,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferencesRepository: ThemePreferencesRepository,
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
                themePreferencesRepository.themePreferencesFlow,
                playbackPreferencesRepository.preferences,
                subtitleAppearancePreferencesRepository.preferencesFlow,
                libraryActionsPreferencesRepository.preferences,
                castPreferencesRepository.castPreferencesFlow,
                credentialSecurityPreferencesRepository.preferences,
            ) { values: Array<Any> ->
                val appearance = values[0] as ThemePreferences
                val playback = values[1] as PlaybackPreferences
                val subtitles = values[2] as SubtitleAppearancePreferences
                val libraryActions = values[3] as LibraryActionsPreferences
                val cast = values[4] as CastPreferences
                val credentialSecurity = values[5] as CredentialSecurityPreferences
                SettingsUiState(
                    isLoading = false,
                    appearance = appearance,
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

    fun setThemeMode(themeMode: ThemeMode) {
        updatePreference { themePreferencesRepository.setThemeMode(themeMode) }
        _uiState.update { it.copy(appearance = it.appearance.copy(themeMode = themeMode)) }
    }

    fun setUseDynamicColors(enabled: Boolean) {
        updatePreference { themePreferencesRepository.setUseDynamicColors(enabled) }
        _uiState.update { it.copy(appearance = it.appearance.copy(useDynamicColors = enabled)) }
    }

    fun setAccentColor(accentColor: AccentColor) {
        updatePreference { themePreferencesRepository.setAccentColor(accentColor) }
        _uiState.update { it.copy(appearance = it.appearance.copy(accentColor = accentColor)) }
    }

    fun setContrastLevel(contrastLevel: ContrastLevel) {
        updatePreference { themePreferencesRepository.setContrastLevel(contrastLevel) }
        _uiState.update { it.copy(appearance = it.appearance.copy(contrastLevel = contrastLevel)) }
    }

    fun setUseThemedIcon(enabled: Boolean) {
        updatePreference { themePreferencesRepository.setUseThemedIcon(enabled) }
        _uiState.update { it.copy(appearance = it.appearance.copy(useThemedIcon = enabled)) }
    }

    fun setEnableEdgeToEdge(enabled: Boolean) {
        updatePreference { themePreferencesRepository.setEnableEdgeToEdge(enabled) }
        _uiState.update { it.copy(appearance = it.appearance.copy(enableEdgeToEdge = enabled)) }
    }

    fun setRespectReduceMotion(enabled: Boolean) {
        updatePreference { themePreferencesRepository.setRespectReduceMotion(enabled) }
        _uiState.update { it.copy(appearance = it.appearance.copy(respectReduceMotion = enabled)) }
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

    fun setVideoSeekIncrement(increment: VideoSeekIncrement) {
        updatePreference { playbackPreferencesRepository.setVideoSeekIncrement(increment) }
        _uiState.update { it.copy(playback = it.playback.copy(videoSeekIncrement = increment)) }
    }

    fun setSubtitleTextSize(textSize: SubtitleTextSize) {
        updatePreference { subtitleAppearancePreferencesRepository.setTextSize(textSize) }
        _uiState.update { it.copy(subtitles = it.subtitles.copy(textSize = textSize)) }
    }

    fun setSubtitleBackground(background: SubtitleBackground) {
        updatePreference { subtitleAppearancePreferencesRepository.setBackground(background) }
        _uiState.update { it.copy(subtitles = it.subtitles.copy(background = background)) }
    }

    fun setSubtitleFont(font: SubtitleFont) {
        updatePreference { subtitleAppearancePreferencesRepository.setFont(font) }
        _uiState.update { it.copy(subtitles = it.subtitles.copy(font = font)) }
    }

    fun setSubtitleTextColor(textColor: SubtitleTextColor) {
        updatePreference { subtitleAppearancePreferencesRepository.setTextColor(textColor) }
        _uiState.update { it.copy(subtitles = it.subtitles.copy(textColor = textColor)) }
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
