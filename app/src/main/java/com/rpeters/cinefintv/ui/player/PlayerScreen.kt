package com.rpeters.cinefintv.ui.player

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Preparing player...",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        uiState.errorMessage != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Playback unavailable",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = viewModel::load) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }

        else -> {
            val player = rememberExoPlayer(uiState.streamUrl.orEmpty(), viewModel.okHttpClient)

            // Playback state — polled every 500ms
            var isPlaying by remember { mutableStateOf(true) }
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            LaunchedEffect(player) {
                while (true) {
                    isPlaying = player.isPlaying
                    position = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                    delay(500L)
                }
            }

            // Controls visibility — auto-hide after 3 seconds
            var controlsVisible by remember { mutableStateOf(true) }
            var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(lastInteraction) {
                delay(3_000L)
                controlsVisible = false
            }
            fun onInteract() {
                controlsVisible = true
                lastInteraction = System.currentTimeMillis()
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // PlayerView (full screen, no built-in controller)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            useController = false
                            this.player = player
                        }
                    },
                    update = { pv -> pv.player = player },
                )

                // Top bar: Back button + title
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopStart),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { onInteract(); onBack() }) { Text("Back") }
                        Text(uiState.title, style = MaterialTheme.typography.titleLarge)
                    }
                }

                // Center controls: -10s, play/pause, +10s
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                onInteract()
                                player.seekTo((position - 10_000L).coerceAtLeast(0L))
                            },
                        ) {
                            Text("-10s")
                        }
                        Button(
                            onClick = {
                                onInteract()
                                if (isPlaying) player.pause() else player.play()
                            },
                        ) {
                            Text(if (isPlaying) "\u23F8" else "\u25B6")
                        }
                        OutlinedButton(
                            onClick = {
                                onInteract()
                                player.seekTo((position + 10_000L).coerceAtMost(duration))
                            },
                        ) {
                            Text("+10s")
                        }
                    }
                }

                // Bottom: progress bar + time
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomStart),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                    ) {
                        if (duration > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(
                                            fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                                        )
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${formatMs(position)} / ${formatMs(duration)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberExoPlayer(streamUrl: String, okHttpClient: OkHttpClient): ExoPlayer {
    val context = LocalContext.current
    val player = remember(streamUrl) {
        val factory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient))
        ExoPlayer.Builder(context).setMediaSourceFactory(factory).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
