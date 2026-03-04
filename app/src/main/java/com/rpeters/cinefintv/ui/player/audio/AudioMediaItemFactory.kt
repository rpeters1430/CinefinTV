package com.rpeters.cinefintv.ui.player.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import javax.inject.Inject

interface AudioMediaItemFactory {
    fun create(
        trackId: String,
        streamUrl: String,
        metadata: MediaMetadata,
    ): MediaItem
}

class DefaultAudioMediaItemFactory @Inject constructor() : AudioMediaItemFactory {
    override fun create(
        trackId: String,
        streamUrl: String,
        metadata: MediaMetadata,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(trackId)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }
}
