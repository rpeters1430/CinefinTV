package com.rpeters.cinefintv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.rpeters.cinefintv.ui.player.PlayerConstants.POSITION_SAVE_INTERVAL_MS
import kotlinx.coroutines.delay

@UnstableApi
@Composable
internal fun PlayerLifecycleManager(
    player: ExoPlayer,
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    isPlaying: Boolean,
    onHasAppliedInitialSeekChange: (Boolean) -> Unit,
    hasAppliedInitialSeek: Boolean,
    onAudioTracksChanged: (List<TrackOption>) -> Unit,
    onSubtitleTracksChanged: (List<TrackOption>) -> Unit,
    onNextEpisodeRequest: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Position saving interval
    LaunchedEffect(player, isPlaying) {
        while (true) {
            if (isPlaying) {
                viewModel.savePlaybackPosition(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0L),
                    isPaused = false,
                )
            }
            delay(POSITION_SAVE_INTERVAL_MS)
        }
    }

    // Main player listener
    DisposableEffect(player, uiState.itemId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (
                    playbackState == Player.STATE_READY &&
                    !hasAppliedInitialSeek &&
                    uiState.savedPlaybackPositionMs > 0L
                ) {
                    player.seekTo(uiState.savedPlaybackPositionMs)
                    onHasAppliedInitialSeekChange(true)
                }

                if (
                    playbackState == Player.STATE_ENDED &&
                    uiState.isEpisodicContent &&
                    uiState.autoPlayNextEpisode
                ) {
                    viewModel.savePlaybackPosition(
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0L),
                        isPaused = true,
                    )

                    player.pause()
                    player.seekTo(0L)

                    onNextEpisodeRequest(uiState.nextEpisodeId ?: "")
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audio = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { group ->
                        (0 until group.length).map { index ->
                            val format = group.getTrackFormat(index)
                            TrackOption(
                                id = "audio-${group.mediaTrackGroup.id}-$index",
                                label = format.label ?: format.language ?: "Audio ${index + 1}",
                                language = format.language,
                            )
                        }
                    }
                onAudioTracksChanged(audio)

                val subtitles = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .flatMap { group ->
                        (0 until group.length).map { index ->
                            val format = group.getTrackFormat(index)
                            TrackOption(
                                id = "sub-${group.mediaTrackGroup.id}-$index",
                                label = format.label ?: format.language ?: "Subtitle ${index + 1}",
                                language = format.language,
                            )
                        }
                    }
                onSubtitleTracksChanged(subtitles)
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            viewModel.savePlaybackPosition(
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0L),
                isPaused = !player.isPlaying,
            )
        }
    }

    // Lifecycle observer
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.savePlaybackPosition(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0L),
                    isPaused = true,
                )
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
