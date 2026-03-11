package com.rpeters.cinefintv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                viewModel.onPlayerError(error)
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
