package com.rpeters.cinefintv.ui.player.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

interface AudioPlaybackController {
    val currentMediaItem: MediaItem?
    val mediaItemCount: Int
    val currentMediaItemIndex: Int
    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean

    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)
    fun release()
    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long)
    fun prepare()
    fun play()
    fun pause()
    fun seekForward()
    fun seekBack()
    fun seekToNextMediaItem()
    fun seekToPreviousMediaItem()
}

class Media3AudioPlaybackController(
    private val mediaController: MediaController,
) : AudioPlaybackController {
    override val currentMediaItem: MediaItem?
        get() = mediaController.currentMediaItem
    override val mediaItemCount: Int
        get() = mediaController.mediaItemCount
    override val currentMediaItemIndex: Int
        get() = mediaController.currentMediaItemIndex
    override val currentPosition: Long
        get() = mediaController.currentPosition
    override val duration: Long
        get() = mediaController.duration
    override val isPlaying: Boolean
        get() = mediaController.isPlaying

    override fun addListener(listener: Player.Listener) = mediaController.addListener(listener)
    override fun removeListener(listener: Player.Listener) = mediaController.removeListener(listener)
    override fun release() = mediaController.release()
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) =
        mediaController.setMediaItems(mediaItems, startIndex, startPositionMs)

    override fun prepare() = mediaController.prepare()
    override fun play() = mediaController.play()
    override fun pause() = mediaController.pause()
    override fun seekForward() = mediaController.seekForward()
    override fun seekBack() = mediaController.seekBack()
    override fun seekToNextMediaItem() = mediaController.seekToNextMediaItem()
    override fun seekToPreviousMediaItem() = mediaController.seekToPreviousMediaItem()
}
