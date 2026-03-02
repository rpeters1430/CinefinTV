package com.rpeters.cinefintv.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadPreferences(
    val wifiOnly: Boolean,
    val defaultQualityId: String,
    val autoCleanEnabled: Boolean,
    val autoCleanWatchedRetentionDays: Int,
    val autoCleanMinFreeSpaceGb: Int,
) {
    companion object {
        val DEFAULT = DownloadPreferences(
            wifiOnly = true,
            defaultQualityId = "original",
            autoCleanEnabled = false,
            autoCleanWatchedRetentionDays = 14,
            autoCleanMinFreeSpaceGb = 5,
        )
    }
}

@Singleton
class DownloadPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<DownloadPreferences> = dataStore.data.map { prefs ->
        DownloadPreferences(
            wifiOnly = prefs[PreferencesKeys.WIFI_ONLY] ?: DownloadPreferences.DEFAULT.wifiOnly,
            defaultQualityId = prefs[PreferencesKeys.DEFAULT_QUALITY_ID]
                ?.takeIf { it.isNotBlank() }
                ?: DownloadPreferences.DEFAULT.defaultQualityId,
            autoCleanEnabled = prefs[PreferencesKeys.AUTO_CLEAN_ENABLED] ?: DownloadPreferences.DEFAULT.autoCleanEnabled,
            autoCleanWatchedRetentionDays = prefs[PreferencesKeys.AUTO_CLEAN_WATCHED_RETENTION_DAYS]
                ?: DownloadPreferences.DEFAULT.autoCleanWatchedRetentionDays,
            autoCleanMinFreeSpaceGb = prefs[PreferencesKeys.AUTO_CLEAN_MIN_FREE_SPACE_GB]
                ?: DownloadPreferences.DEFAULT.autoCleanMinFreeSpaceGb,
        )
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.WIFI_ONLY] = enabled
        }
    }

    suspend fun setDefaultQualityId(qualityId: String) {
        dataStore.edit { prefs ->
            val normalized = qualityId.takeIf { it.isNotBlank() } ?: DownloadPreferences.DEFAULT.defaultQualityId
            prefs[PreferencesKeys.DEFAULT_QUALITY_ID] = normalized
        }
    }

    suspend fun setAutoCleanEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_CLEAN_ENABLED] = enabled
        }
    }

    suspend fun setAutoCleanWatchedRetentionDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_CLEAN_WATCHED_RETENTION_DAYS] = days.coerceIn(1, 365)
        }
    }

    suspend fun setAutoCleanMinFreeSpaceGb(gb: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AUTO_CLEAN_MIN_FREE_SPACE_GB] = gb.coerceIn(1, 128)
        }
    }

    private object PreferencesKeys {
        val WIFI_ONLY = booleanPreferencesKey("downloads_wifi_only")
        val DEFAULT_QUALITY_ID = stringPreferencesKey("downloads_default_quality_id")
        val AUTO_CLEAN_ENABLED = booleanPreferencesKey("downloads_auto_clean_enabled")
        val AUTO_CLEAN_WATCHED_RETENTION_DAYS = androidx.datastore.preferences.core.intPreferencesKey("downloads_auto_clean_watched_retention_days")
        val AUTO_CLEAN_MIN_FREE_SPACE_GB = androidx.datastore.preferences.core.intPreferencesKey("downloads_auto_clean_min_free_space_gb")
    }
}
