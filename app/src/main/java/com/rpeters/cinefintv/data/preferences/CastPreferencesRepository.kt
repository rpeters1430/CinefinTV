package com.rpeters.cinefintv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// DataStore delegate for cast preferences
private val Context.castDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cast_preferences",
)

/**
 * Represents persisted cast session information.
 * Used to restore cast sessions across app restarts.
 */
data class CastPreferences(
    val lastDeviceName: String? = null,
    val lastSessionId: String? = null,
    val lastCastTimestamp: Long? = null,
    val autoReconnect: Boolean = true,
    val rememberLastDevice: Boolean = true,
) {
    companion object {
        val DEFAULT = CastPreferences()
    }
}

/**
 * Repository for managing cast preferences using DataStore.
 * Persists cast session information across app restarts to enable
 * automatic reconnection to previously used cast devices.
 */
@Singleton
class CastPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.castDataStore

    /**
     * Flow of current cast preferences.
     */
    val castPreferencesFlow: Flow<CastPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading cast preferences, using defaults", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading cast preferences", exception)
                throw exception
            }
        }
        .map { preferences ->
            CastPreferences(
                lastDeviceName = preferences[PreferencesKeys.LAST_DEVICE_NAME],
                lastSessionId = preferences[PreferencesKeys.LAST_SESSION_ID],
                lastCastTimestamp = preferences[PreferencesKeys.LAST_CAST_TIMESTAMP],
                autoReconnect = preferences[PreferencesKeys.AUTO_RECONNECT] ?: true,
                rememberLastDevice = preferences[PreferencesKeys.REMEMBER_LAST_DEVICE] ?: true,
            )
        }

    /**
     * Save the last used cast device and session information.
     * This enables automatic reconnection after app restart.
     */
    suspend fun saveLastCastSession(
        deviceName: String?,
        sessionId: String?,
    ) {
        dataStore.edit { preferences ->
            if (deviceName != null) {
                preferences[PreferencesKeys.LAST_DEVICE_NAME] = deviceName
            } else {
                preferences.remove(PreferencesKeys.LAST_DEVICE_NAME)
            }

            if (sessionId != null) {
                preferences[PreferencesKeys.LAST_SESSION_ID] = sessionId
            } else {
                preferences.remove(PreferencesKeys.LAST_SESSION_ID)
            }

            preferences[PreferencesKeys.LAST_CAST_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Clear saved cast session information.
     * Call this when a user explicitly disconnects or when session ends.
     */
    suspend fun clearLastCastSession() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_DEVICE_NAME)
            preferences.remove(PreferencesKeys.LAST_SESSION_ID)
            preferences.remove(PreferencesKeys.LAST_CAST_TIMESTAMP)
        }
    }

    /**
     * Update auto-reconnect setting.
     * When enabled, the app will attempt to reconnect to the last cast device on startup.
     */
    suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RECONNECT] = enabled
        }
    }

    /**
     * Update remember last device setting.
     * When enabled, the app will remember the last cast device used.
     */
    suspend fun setRememberLastDevice(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMEMBER_LAST_DEVICE] = enabled
        }
    }

    /**
     * Reset all cast preferences to defaults.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private object PreferencesKeys {
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val LAST_SESSION_ID = stringPreferencesKey("last_session_id")
        val LAST_CAST_TIMESTAMP = longPreferencesKey("last_cast_timestamp")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val REMEMBER_LAST_DEVICE = booleanPreferencesKey("remember_last_device")
    }

    companion object {
        private const val TAG = "CastPreferencesRepository"

        // Maximum age for a saved cast session (24 hours)
        const val MAX_SESSION_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
