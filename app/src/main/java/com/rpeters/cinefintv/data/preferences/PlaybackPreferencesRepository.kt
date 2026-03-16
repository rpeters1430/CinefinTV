package com.rpeters.cinefintv.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rpeters.cinefintv.di.PlaybackPreferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transcoding quality levels.
 */
enum class TranscodingQuality(val label: String) {
    AUTO("Auto"),
    MAXIMUM("Maximum"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
}

/**
 * Audio channel preferences.
 */
enum class AudioChannelPreference(val label: String, val channels: Int?) {
    AUTO("Auto", null),
    STEREO("Stereo", 2),
    SURROUND_5_1("5.1 Surround", 6),
    SURROUND_7_1("7.1 Surround", 8),
}

/**
 * Resume playback mode preferences.
 */
enum class ResumePlaybackMode(val label: String) {
    ALWAYS("Always resume"),
    ASK("Ask me"),
    NEVER("Always start from beginning"),
}

data class PlaybackPreferences(
    val transcodingQuality: TranscodingQuality,
    val audioChannels: AudioChannelPreference,
    val autoPlayNextEpisode: Boolean,
    val resumePlaybackMode: ResumePlaybackMode,
) {
    companion object {
        val DEFAULT = PlaybackPreferences(
            transcodingQuality = TranscodingQuality.AUTO,
            audioChannels = AudioChannelPreference.AUTO,
            autoPlayNextEpisode = true, // enabled by default
            resumePlaybackMode = ResumePlaybackMode.ALWAYS, // always resume by default
        )
    }
}

/**
 * Repository for managing playback-related user preferences using DataStore.
 */
@Singleton
class PlaybackPreferencesRepository @Inject constructor(
    @PlaybackPreferencesDataStore private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<PlaybackPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading playback preferences, using defaults", exception)
                emit(emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading playback preferences", exception)
                throw exception
            }
        }
        .map { prefs ->
            PlaybackPreferences(
                transcodingQuality = runCatching {
                    TranscodingQuality.valueOf(prefs[PreferencesKeys.TRANSCODING_QUALITY] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.transcodingQuality),
                audioChannels = runCatching {
                    AudioChannelPreference.valueOf(prefs[PreferencesKeys.AUDIO_CHANNELS] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.audioChannels),
                autoPlayNextEpisode = prefs[PreferencesKeys.AUTO_PLAY_NEXT_EPISODE] ?: PlaybackPreferences.DEFAULT.autoPlayNextEpisode,
                resumePlaybackMode = runCatching {
                    ResumePlaybackMode.valueOf(prefs[PreferencesKeys.RESUME_PLAYBACK_MODE] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.resumePlaybackMode),
            )
        }

    suspend fun setTranscodingQuality(quality: TranscodingQuality) {
        dataStore.edit { it[PreferencesKeys.TRANSCODING_QUALITY] = quality.name }
    }

    suspend fun setAudioChannels(preference: AudioChannelPreference) {
        dataStore.edit { it[PreferencesKeys.AUDIO_CHANNELS] = preference.name }
    }

    suspend fun setAutoPlayNextEpisode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_PLAY_NEXT_EPISODE] = enabled }
    }

    suspend fun setResumePlaybackMode(mode: ResumePlaybackMode) {
        dataStore.edit { it[PreferencesKeys.RESUME_PLAYBACK_MODE] = mode.name }
    }

    private object PreferencesKeys {
        val TRANSCODING_QUALITY = stringPreferencesKey("transcoding_quality")
        val AUDIO_CHANNELS = stringPreferencesKey("audio_channels")
        val AUTO_PLAY_NEXT_EPISODE = booleanPreferencesKey("auto_play_next_episode")
        val RESUME_PLAYBACK_MODE = stringPreferencesKey("resume_playback_mode")
    }

    companion object {
        private const val TAG = "PlaybackPrefsRepo"
    }
}
