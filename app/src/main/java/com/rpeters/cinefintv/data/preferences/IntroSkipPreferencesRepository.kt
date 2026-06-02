package com.rpeters.cinefintv.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rpeters.cinefintv.di.PlaybackPreferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class IntroSkipPreferences(
    val autoSkipIntro: Boolean = false,
    val autoSkipCredits: Boolean = false,
) {
    companion object { val DEFAULT = IntroSkipPreferences() }
}

@Singleton
class IntroSkipPreferencesRepository @Inject constructor(
    @param:PlaybackPreferencesDataStore private val dataStore: DataStore<Preferences>,
) {

    val preferencesFlow: Flow<IntroSkipPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                SecureLogger.e(TAG, "Error reading prefs", e)
                emit(emptyPreferences())
            } else {
                SecureLogger.e(TAG, "Unexpected error reading prefs", e)
                throw e
            }
        }
        .map { prefs ->
            IntroSkipPreferences(
                autoSkipIntro = prefs[PreferencesKeys.autoSkipIntroKey] ?: false,
                autoSkipCredits = prefs[PreferencesKeys.autoSkipCreditsKey] ?: false,
            )
        }

    suspend fun setAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.autoSkipIntroKey] = enabled }
    }

    suspend fun setAutoSkipCredits(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.autoSkipCreditsKey] = enabled }
    }

    private companion object {
        const val TAG = "IntroSkipPreferencesRepository"

        object PreferencesKeys {
            val autoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
            val autoSkipCreditsKey = booleanPreferencesKey("auto_skip_credits")
        }
    }
}
