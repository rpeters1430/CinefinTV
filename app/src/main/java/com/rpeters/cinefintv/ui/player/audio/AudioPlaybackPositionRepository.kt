package com.rpeters.cinefintv.ui.player.audio

import android.content.Context
import com.rpeters.cinefintv.data.PlaybackPositionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface AudioPlaybackPositionRepository {
    suspend fun getPlaybackPosition(itemId: String): Long
    suspend fun savePlaybackPosition(itemId: String, positionMs: Long)
}

class DefaultAudioPlaybackPositionRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : AudioPlaybackPositionRepository {
    override suspend fun getPlaybackPosition(itemId: String): Long {
        return PlaybackPositionStore.getPlaybackPosition(appContext, itemId)
    }

    override suspend fun savePlaybackPosition(itemId: String, positionMs: Long) {
        PlaybackPositionStore.savePlaybackPosition(appContext, itemId, positionMs)
    }
}
