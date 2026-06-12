package com.rpeters.cinefintv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// DataStore delegate for screensaver preferences
private val Context.screensaverDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "screensaver_preferences",
)

/**
 * Repository for managing Screensaver preferences using DataStore.
 */
@Singleton
open class ScreensaverPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.screensaverDataStore)

    companion object {
        private const val TAG = "ScreensaverPrefsRepo"
    }

    private object PreferencesKeys {
        val IS_ENABLED = booleanPreferencesKey("screensaver_is_enabled")
        val IDLE_TIMEOUT_MINUTES = intPreferencesKey("screensaver_idle_timeout_minutes")
    }

    /**
     * Flow of screensaver preferences.
     */
    open val screensaverPreferencesFlow: Flow<ScreensaverPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SecureLogger.w(TAG, "IOException reading screensaver preferences, using defaults", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading screensaver preferences", exception)
                throw exception
            }
        }
        .map { preferences ->
            val defaults = ScreensaverPreferences.DEFAULT
            ScreensaverPreferences(
                isEnabled = preferences[PreferencesKeys.IS_ENABLED] ?: defaults.isEnabled,
                idleTimeoutMinutes = preferences[PreferencesKeys.IDLE_TIMEOUT_MINUTES] ?: defaults.idleTimeoutMinutes,
            )
        }

    /**
     * Update screensaver enabled setting.
     */
    open suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ENABLED] = enabled
        }
    }

    /**
     * Update screensaver idle timeout in minutes.
     */
    open suspend fun setIdleTimeoutMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IDLE_TIMEOUT_MINUTES] = minutes
        }
    }
}
