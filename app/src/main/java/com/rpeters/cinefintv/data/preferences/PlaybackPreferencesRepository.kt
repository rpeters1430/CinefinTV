package com.rpeters.cinefintv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_preferences",
)

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
    val maxBitrateWifi: Int,
    val maxBitrateCellular: Int,
    val transcodingQuality: TranscodingQuality,
    val audioChannels: AudioChannelPreference,
    val preferredAudioLanguage: String?,
    val autoPlayNextEpisode: Boolean,
    val resumePlaybackMode: ResumePlaybackMode,
    val useExternalPlayer: Boolean,
) {
    companion object {
        val DEFAULT = PlaybackPreferences(
            maxBitrateWifi = 80_000_000, // 80 Mbps
            maxBitrateCellular = 25_000_000, // 25 Mbps
            transcodingQuality = TranscodingQuality.AUTO,
            audioChannels = AudioChannelPreference.AUTO,
            preferredAudioLanguage = null, // null = no preference
            autoPlayNextEpisode = true, // enabled by default
            resumePlaybackMode = ResumePlaybackMode.ALWAYS, // always resume by default
            useExternalPlayer = false, // disabled by default
        )
    }
}

/**
 * Repository for managing playback-related user preferences using DataStore.
 */
@Singleton
class PlaybackPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.playbackDataStore)

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
                maxBitrateWifi = prefs[PreferencesKeys.MAX_BITRATE_WIFI] ?: PlaybackPreferences.DEFAULT.maxBitrateWifi,
                maxBitrateCellular = prefs[PreferencesKeys.MAX_BITRATE_CELLULAR] ?: PlaybackPreferences.DEFAULT.maxBitrateCellular,
                transcodingQuality = runCatching {
                    TranscodingQuality.valueOf(prefs[PreferencesKeys.TRANSCODING_QUALITY] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.transcodingQuality),
                audioChannels = runCatching {
                    AudioChannelPreference.valueOf(prefs[PreferencesKeys.AUDIO_CHANNELS] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.audioChannels),
                preferredAudioLanguage = prefs[PreferencesKeys.PREFERRED_AUDIO_LANGUAGE],
                autoPlayNextEpisode = prefs[PreferencesKeys.AUTO_PLAY_NEXT_EPISODE] ?: PlaybackPreferences.DEFAULT.autoPlayNextEpisode,
                resumePlaybackMode = runCatching {
                    ResumePlaybackMode.valueOf(prefs[PreferencesKeys.RESUME_PLAYBACK_MODE] ?: "")
                }.getOrDefault(PlaybackPreferences.DEFAULT.resumePlaybackMode),
                useExternalPlayer = prefs[PreferencesKeys.USE_EXTERNAL_PLAYER] ?: PlaybackPreferences.DEFAULT.useExternalPlayer,
            )
        }

    suspend fun setMaxBitrateWifi(bitrate: Int) {
        dataStore.edit { it[PreferencesKeys.MAX_BITRATE_WIFI] = bitrate }
    }

    suspend fun setMaxBitrateCellular(bitrate: Int) {
        dataStore.edit { it[PreferencesKeys.MAX_BITRATE_CELLULAR] = bitrate }
    }

    suspend fun setTranscodingQuality(quality: TranscodingQuality) {
        dataStore.edit { it[PreferencesKeys.TRANSCODING_QUALITY] = quality.name }
    }

    suspend fun setAudioChannels(preference: AudioChannelPreference) {
        dataStore.edit { it[PreferencesKeys.AUDIO_CHANNELS] = preference.name }
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        dataStore.edit { prefs ->
            if (language != null) {
                prefs[PreferencesKeys.PREFERRED_AUDIO_LANGUAGE] = language
            } else {
                prefs.remove(PreferencesKeys.PREFERRED_AUDIO_LANGUAGE)
            }
        }
    }

    suspend fun setAutoPlayNextEpisode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTO_PLAY_NEXT_EPISODE] = enabled }
    }

    suspend fun setResumePlaybackMode(mode: ResumePlaybackMode) {
        dataStore.edit { it[PreferencesKeys.RESUME_PLAYBACK_MODE] = mode.name }
    }

    suspend fun setUseExternalPlayer(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_EXTERNAL_PLAYER] = enabled }
    }

    private object PreferencesKeys {
        val MAX_BITRATE_WIFI = intPreferencesKey("max_bitrate_wifi")
        val MAX_BITRATE_CELLULAR = intPreferencesKey("max_bitrate_cellular")
        val TRANSCODING_QUALITY = stringPreferencesKey("transcoding_quality")
        val AUDIO_CHANNELS = stringPreferencesKey("audio_channels")
        val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
        val AUTO_PLAY_NEXT_EPISODE = booleanPreferencesKey("auto_play_next_episode")
        val RESUME_PLAYBACK_MODE = stringPreferencesKey("resume_playback_mode")
        val USE_EXTERNAL_PLAYER = booleanPreferencesKey("use_external_player")
    }

    companion object {
        private const val TAG = "PlaybackPrefsRepo"
    }
}
