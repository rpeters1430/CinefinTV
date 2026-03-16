package com.rpeters.cinefintv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
enum class OfflinePlaybackEventType {
    PROGRESS,
    STOPPED,
    MARK_PLAYED,
    MARK_UNPLAYED,
}

@Serializable
data class QueuedProgressUpdate(
    val id: String = UUID.randomUUID().toString(),
    val eventType: OfflinePlaybackEventType = OfflinePlaybackEventType.PROGRESS,
    val itemId: String,
    val sessionId: String,
    val positionTicks: Long?,
    val mediaSourceId: String? = null,
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

private val Context.offlineProgressDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "offline_progress_updates",
)

@Singleton
class OfflineProgressRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val MAX_QUEUED_UPDATES = 200
        private const val MAX_EVENT_AGE_MS = 30L * 24L * 60L * 60L * 1000L // 30 days
    }

    private val dataStore = context.offlineProgressDataStore
    private val QUEUED_UPDATES_KEY = stringPreferencesKey("queued_updates")
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Adds a progress update to the queue.
     * If an update for the same itemId already exists, it is replaced with the newer one
     * to ensure we only sync the latest position.
     */
    suspend fun addUpdate(update: QueuedProgressUpdate) {
        dataStore.edit { preferences ->
            val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
            val now = System.currentTimeMillis()
            val currentList = decodeQueueOrNull(currentJson)?.toMutableList() ?: mutableListOf()
            currentList.removeAll { now - it.timestamp > MAX_EVENT_AGE_MS }

            // Coalesce queue to avoid duplicate backlog:
            // - keep latest PROGRESS/STOPPED per item
            // - keep only the latest watched-state event per item
            when (update.eventType) {
                OfflinePlaybackEventType.PROGRESS,
                OfflinePlaybackEventType.STOPPED,
                -> currentList.removeAll { it.itemId == update.itemId && it.eventType == update.eventType }

                OfflinePlaybackEventType.MARK_PLAYED,
                OfflinePlaybackEventType.MARK_UNPLAYED,
                -> currentList.removeAll {
                    it.itemId == update.itemId &&
                        (it.eventType == OfflinePlaybackEventType.MARK_PLAYED ||
                            it.eventType == OfflinePlaybackEventType.MARK_UNPLAYED)
                }
            }
            currentList.add(update)

            // Keep only last MAX_QUEUED_UPDATES updates to prevent unbounded growth.
            val limitedList = currentList.takeLast(MAX_QUEUED_UPDATES)
            preferences[QUEUED_UPDATES_KEY] = json.encodeToString(limitedList)

            SecureLogger.d(
                "OfflineProgress",
                "Queued ${update.eventType} for item ${update.itemId} at ${update.positionTicks} ticks (id=${update.id})",
            )
        }
    }

    /**
     * Gets all queued updates.
     */
    suspend fun getQueuedUpdates(): List<QueuedProgressUpdate> {
        val preferences = dataStore.data.first()
        val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
        val now = System.currentTimeMillis()

        return decodeQueueOrNull(currentJson)
            ?.filter { now - it.timestamp <= MAX_EVENT_AGE_MS }
            .orEmpty()
    }

    /**
     * Removes specified updates from the queue by their IDs.
     * Use this after successful sync to clear only processed items.
     */
    suspend fun removeUpdates(updateIds: Set<String>) {
        if (updateIds.isEmpty()) return

        dataStore.edit { preferences ->
            val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
            val currentList = decodeQueueOrNull(currentJson) ?: return@edit
            val newList = currentList.filterNot { it.id in updateIds }

            if (newList.size != currentList.size) {
                preferences[QUEUED_UPDATES_KEY] = json.encodeToString(newList)
                SecureLogger.d("OfflineProgress", "Removed ${currentList.size - newList.size} synced updates from DataStore")
            }
        }
    }

    /**
     * Returns true if there are pending updates.
     */
    fun hasPendingUpdates(): Flow<Boolean> = dataStore.data.map { preferences ->
        val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
        decodeQueueOrNull(currentJson)?.isNotEmpty() == true
    }

    fun pendingCount(): Flow<Int> = dataStore.data.map { preferences ->
        val currentJson = preferences[QUEUED_UPDATES_KEY] ?: "[]"
        decodeQueueOrNull(currentJson)?.size ?: 0
    }

    suspend fun pendingCountSnapshot(): Int {
        val currentJson = dataStore.data.first()[QUEUED_UPDATES_KEY] ?: "[]"
        return decodeQueueOrNull(currentJson)?.size ?: 0
    }

    private fun decodeQueueOrNull(raw: String): List<QueuedProgressUpdate>? {
        return try {
            json.decodeFromString<List<QueuedProgressUpdate>>(raw)
        } catch (e: Exception) {
            SecureLogger.w("OfflineProgress", "Failed to decode queued updates payload, resetting queue: ${e.message}")
            null
        }
    }
}
