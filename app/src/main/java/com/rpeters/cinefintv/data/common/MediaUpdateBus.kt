package com.rpeters.cinefintv.data.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A centralized event bus for broadcasting media item updates across the application.
 * This allows screens to react to changes like playback progress, favorite toggles, or deletions.
 */
@Singleton
class MediaUpdateBus @Inject constructor() {
    private val _events = MutableSharedFlow<MediaUpdateEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<MediaUpdateEvent> = _events.asSharedFlow()

    /**
     * Broadcast an update for a specific media item.
     */
    suspend fun post(event: MediaUpdateEvent) {
        _events.emit(event)
    }

    /**
     * Helper to broadcast a refresh event for a specific item ID.
     */
    suspend fun refreshItem(itemId: String) {
        post(MediaUpdateEvent.RefreshItem(itemId))
    }

    /**
     * Helper to broadcast a refresh for everything (e.g. after a large library change).
     */
    suspend fun refreshAll() {
        post(MediaUpdateEvent.RefreshAll)
    }
}

sealed class MediaUpdateEvent {
    /**
     * Request to refresh a specific item's data.
     */
    data class RefreshItem(val itemId: String) : MediaUpdateEvent()

    /**
     * Request to refresh all displayed media data.
     */
    data object RefreshAll : MediaUpdateEvent()
}
