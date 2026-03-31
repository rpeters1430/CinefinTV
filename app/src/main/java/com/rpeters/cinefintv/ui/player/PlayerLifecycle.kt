package com.rpeters.cinefintv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.rpeters.cinefintv.ui.player.PlayerConstants.POSITION_SAVE_INTERVAL_MS
import kotlinx.coroutines.isActive
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
    val view = LocalView.current
    val latestUiState by androidx.compose.runtime.rememberUpdatedState(uiState)
    val latestNextEpisodeRequest by androidx.compose.runtime.rememberUpdatedState(onNextEpisodeRequest)

    // Keep screen on while playing
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Position saving interval
    LaunchedEffect(player, isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        while (isActive) {
            viewModel.savePlaybackPosition(
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0L),
                isPaused = false,
            )
            delay(POSITION_SAVE_INTERVAL_MS)
        }
    }

    // Main player listener
    DisposableEffect(player, uiState.itemId) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.savePlaybackPosition(
                        positionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0L),
                        isPaused = true,
                    )

                    val nextTarget = nextPlaybackCompletionTarget(latestUiState)
                    if (!nextTarget.isNullOrBlank()) {
                        player.pause()
                        latestNextEpisodeRequest(nextTarget)
                    } else {
                        // For non-episodic content or when auto-play is off, navigate back
                        latestNextEpisodeRequest("")
                    }
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

internal fun nextPlaybackCompletionTarget(uiState: PlayerUiState): String? {
    return when {
        !uiState.isEpisodicContent -> null
        !uiState.autoPlayNextEpisode -> null
        uiState.nextEpisodeId.isNullOrBlank() -> null
        else -> uiState.nextEpisodeId
    }
}
