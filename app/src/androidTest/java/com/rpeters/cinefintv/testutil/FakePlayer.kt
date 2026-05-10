package com.rpeters.cinefintv.testutil

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size

/**
 * A manual fake for [Player] to avoid using MockK in instrumented tests.
 */
class FakePlayer : Player {
    var lastSeekPosition: Long = -1L
    var playCalled: Boolean = false
    var pauseCalled: Boolean = false
    private val listeners = mutableListOf<Player.Listener>()

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()
    override fun addListener(listener: Player.Listener) { listeners.add(listener) }
    override fun removeListener(listener: Player.Listener) { listeners.remove(listener) }
    override fun setMediaItems(mediaItems: List<MediaItem>) {}
    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {}
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {}
    override fun addMediaItem(mediaItem: MediaItem) {}
    override fun addMediaItem(index: Int, mediaItem: MediaItem) {}
    override fun addMediaItems(mediaItems: List<MediaItem>) {}
    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {}
    override fun moveMediaItem(fromIndex: Int, toIndex: Int) {}
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
    override fun removeMediaItem(index: Int) {}
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
    override fun clearMediaItems() {}
    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {}
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {}
    override fun isCommandAvailable(command: Int): Boolean = true
    override fun canAdvertiseSession(): Boolean = false
    override fun getAvailableCommands(): Player.Commands = Player.Commands.EMPTY
    override fun prepare() {}
    override fun getPlaybackState(): Int = Player.STATE_IDLE
    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getPlayerError(): PlaybackException? = null
    override fun play() { playCalled = true }
    override fun pause() { pauseCalled = true }
    override fun setPlayWhenReady(playWhenReady: Boolean) {}
    override fun getPlayWhenReady(): Boolean = true
    override fun setRepeatMode(repeatMode: Int) {}
    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun getShuffleModeEnabled(): Boolean = false
    override fun isLoading(): Boolean = false
    override fun seekToDefaultPosition() {}
    override fun seekToDefaultPosition(mediaItemIndex: Int) {}
    override fun seekTo(positionMs: Long) { lastSeekPosition = positionMs }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) { lastSeekPosition = positionMs }
    override fun getSeekBackIncrement(): Long = 0L
    override fun seekBack() {}
    override fun getSeekForwardIncrement(): Long = 0L
    override fun seekForward() {}
    override fun hasPreviousMediaItem(): Boolean = false
    override fun seekToPreviousMediaItem() {}
    override fun seekToPrevious() {}
    override fun getMaxSeekToPreviousPosition(): Long = 0L
    override fun hasNextMediaItem(): Boolean = false
    override fun seekToNextMediaItem() {}
    override fun seekToNext() {}
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
    override fun setPlaybackSpeed(speed: Float) {}
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun stop() {}
    override fun release() {}
    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
    override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getCurrentTimeline(): Timeline = Timeline.EMPTY
    override fun getCurrentPeriodIndex(): Int = 0
    override fun getCurrentWindowIndex(): Int = 0
    override fun getNextWindowIndex(): Int = 0
    override fun getPreviousWindowIndex(): Int = 0
    override fun isCurrentWindowDynamic(): Boolean = false
    override fun isCurrentWindowLive(): Boolean = false
    override fun isCurrentWindowSeekable(): Boolean = false
    override fun getCurrentMediaItemIndex(): Int = 0
    override fun getNextMediaItemIndex(): Int = 0
    override fun getPreviousMediaItemIndex(): Int = 0
    override fun getCurrentMediaItem(): MediaItem? = null
    override fun getMediaItemCount(): Int = 0
    override fun getMediaItemAt(index: Int): MediaItem = MediaItem.EMPTY
    override fun getDuration(): Long = 0L
    override fun getCurrentPosition(): Long = 0L
    override fun getBufferedPosition(): Long = 0L
    override fun getBufferedPercentage(): Int = 0
    override fun getTotalBufferedDuration(): Long = 0L
    override fun isCurrentMediaItemDynamic(): Boolean = false
    override fun isCurrentMediaItemLive(): Boolean = false
    override fun getCurrentLiveOffset(): Long = 0L
    override fun isCurrentMediaItemSeekable(): Boolean = false
    override fun isPlaying(): Boolean = false
    override fun isPlayingAd(): Boolean = false
    override fun getCurrentAdGroupIndex(): Int = 0
    override fun getCurrentAdIndexInAdGroup(): Int = 0
    override fun getContentDuration(): Long = 0L
    override fun getContentPosition(): Long = 0L
    override fun getContentBufferedPosition(): Long = 0L
    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun setVolume(volume: Float) {}
    override fun getVolume(): Float = 1f
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO
    override fun getDeviceInfo(): DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
    override fun getDeviceVolume(): Int = 0
    override fun isDeviceMuted(): Boolean = false
    override fun setDeviceVolume(volume: Int) {}
    override fun setDeviceVolume(volume: Int, flags: Int) {}
    override fun increaseDeviceVolume() {}
    override fun increaseDeviceVolume(flags: Int) {}
    override fun decreaseDeviceVolume() {}
    override fun decreaseDeviceVolume(flags: Int) {}
    override fun setDeviceMuted(muted: Boolean) {}
    override fun setDeviceMuted(muted: Boolean, flags: Int) {}
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
    override fun setMediaItem(mediaItem: MediaItem) {}
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {}
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {}
    override fun getCurrentManifest(): Any? = null
    override fun mute() {}
    override fun unmute() {}
    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: Surface?) {}
    override fun setVideoSurface(surface: Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {}
    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {}
    override fun setVideoTextureView(textureView: TextureView?) {}
    override fun clearVideoTextureView(textureView: TextureView?) {}
    override fun getSurfaceSize(): Size = Size.UNKNOWN
}
