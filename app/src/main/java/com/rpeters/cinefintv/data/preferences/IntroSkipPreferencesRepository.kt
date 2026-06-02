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
    @PlaybackPreferencesDataStore private val dataStore: DataStore<Preferences>,
) {
    private val autoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
    private val autoSkipCreditsKey = booleanPreferencesKey("auto_skip_credits")

    val preferencesFlow: Flow<IntroSkipPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                SecureLogger.e("IntroSkipPreferencesRepository", "Error reading prefs", e)
                emit(emptyPreferences())
            } else throw e
        }
        .map { prefs ->
            IntroSkipPreferences(
                autoSkipIntro = prefs[autoSkipIntroKey] ?: false,
                autoSkipCredits = prefs[autoSkipCreditsKey] ?: false,
            )
        }

    suspend fun setAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { it[autoSkipIntroKey] = enabled }
    }

    suspend fun setAutoSkipCredits(enabled: Boolean) {
        dataStore.edit { it[autoSkipCreditsKey] = enabled }
    }
}
