package com.rpeters.cinefintv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackPositionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_positions")

object PlaybackPositionStore {
    suspend fun getPlaybackPosition(context: Context, itemId: String): Long {
        if (itemId.isBlank()) return 0L
        val key = longPreferencesKey(itemId)
        return context.playbackPositionDataStore.data
            .map { prefs -> prefs[key] ?: 0L }
            .first()
    }

    suspend fun savePlaybackPosition(context: Context, itemId: String, positionMs: Long) {
        if (itemId.isBlank()) return
        val key = longPreferencesKey(itemId)
        context.playbackPositionDataStore.edit { prefs ->
            prefs[key] = positionMs
        }
    }
}
