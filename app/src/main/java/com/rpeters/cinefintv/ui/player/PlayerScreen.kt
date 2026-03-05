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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Preparing player...",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }

        uiState.errorMessage != null -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Playback unavailable",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
        }

        else -> {
            val player = rememberExoPlayer(uiState.streamUrl.orEmpty(), viewModel.okHttpClient)
            val lifecycleOwner = LocalLifecycleOwner.current
            val coroutineScope = rememberCoroutineScope()
            var hasAppliedInitialSeek by remember(uiState.itemId) { mutableStateOf(false) }
            var isTrackPanelVisible by remember { mutableStateOf(false) }
            var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
            var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }

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

            DisposableEffect(player, uiState.savedPlaybackPositionMs) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (
                            playbackState == Player.STATE_READY &&
                            !hasAppliedInitialSeek &&
                            uiState.savedPlaybackPositionMs > 0L
                        ) {
                            player.seekTo(uiState.savedPlaybackPositionMs)
                            hasAppliedInitialSeek = true
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

                            coroutineScope.launch {
                                viewModel.getNextEpisodeId()?.let { onOpenItem(it) }
                            }
                        }
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        audioTracks = tracks.groups
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

                        subtitleTracks = tracks.groups
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

            // Controls visibility — auto-hide after 3 seconds
            var controlsVisible by remember { mutableStateOf(true) }
            var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(lastInteraction) {
                delay(3_000L)
                if (!isTrackPanelVisible) {
                    controlsVisible = false
                }
            }
            fun onInteract() {
                controlsVisible = true
                lastInteraction = System.currentTimeMillis()
            }

            var countdownRemaining by remember { mutableLongStateOf(-1L) }
            val countdownThresholdMs = 15_000L // 15 seconds

            LaunchedEffect(position, duration) {
                if (duration > 0 && uiState.isEpisodicContent && uiState.autoPlayNextEpisode && uiState.nextEpisodeId != null) {
                    val remaining = duration - position
                    if (remaining in 1L..countdownThresholdMs) {
                        countdownRemaining = remaining / 1000
                    } else {
                        countdownRemaining = -1L
                    }
                } else {
                    countdownRemaining = -1L
                }
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

                // Controls Overlay
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    ) {
                        // Top Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                                .align(Alignment.TopStart),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(onClick = { onInteract(); onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Text(uiState.title, style = MaterialTheme.typography.headlineSmall)
                        }

                        // Center Controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { onInteract(); player.seekTo((position - 10_000).coerceAtLeast(0)) }) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s")
                            }

                            Button(
                                onClick = { onInteract(); if (isPlaying) player.pause() else player.play() },
                                scale = ButtonDefaults.scale(focusedScale = 1.1f)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            OutlinedButton(onClick = { onInteract(); player.seekTo((position + 10_000).coerceAtMost(duration)) }) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
                            }
                        }

                        // Bottom Controls
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Progress Bar
                            if (duration > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatMs(position)} / ${formatMs(duration)}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (uiState.isEpisodicContent) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Auto-play next", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                                                Switch(
                                                    checked = uiState.autoPlayNextEpisode,
                                                    onCheckedChange = {
                                                        onInteract()
                                                        viewModel.setAutoPlayNextEpisode(it)
                                                    },
                                                )
                                            }
                                        }

                                        OutlinedButton(onClick = { onInteract(); isTrackPanelVisible = !isTrackPanelVisible }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Media Settings")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Tracks")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Next Episode Countdown Overlay
                AnimatedVisibility(
                    visible = countdownRemaining > 0,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    androidx.tv.material3.Card(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.getNextEpisodeId()?.let { onOpenItem(it) }
                            }
                        },
                        modifier = Modifier.width(300.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Next Episode",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = uiState.nextEpisodeTitle ?: "Coming up next",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Starting in $countdownRemaining seconds...",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.getNextEpisodeId()?.let { onOpenItem(it) }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Play Now")
                            }
                        }
                    }
                }

                // Tracks Side Panel
                AnimatedVisibility(
                    visible = controlsVisible && isTrackPanelVisible,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(360.dp),
                        shape = RectangleShape,
                        colors = SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text("Media Settings", style = MaterialTheme.typography.headlineSmall)

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Audio Track", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                audioTracks.forEach { track ->
                                    val isSelected = uiState.selectedAudioTrack?.id == track.id
                                    if (isSelected) {
                                        Button(
                                            onClick = {
                                                onInteract()
                                                viewModel.onAudioTrackSelected(track)
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setPreferredAudioLanguage(track.language)
                                                    .build()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(track.label)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                viewModel.onAudioTrackSelected(track)
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setPreferredAudioLanguage(track.language)
                                                    .build()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(track.label)
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Subtitles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                
                                val isNoneSelected = uiState.selectedSubtitleTrack == null
                                if (isNoneSelected) {
                                    Button(
                                        onClick = {
                                            onInteract()
                                            viewModel.onSubtitleTrackSelected(null)
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("None") }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            onInteract()
                                            viewModel.onSubtitleTrackSelected(null)
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("None") }
                                }

                                subtitleTracks.forEach { track ->
                                    val isSelected = uiState.selectedSubtitleTrack?.id == track.id
                                    if (isSelected) {
                                        Button(
                                            onClick = {
                                                onInteract()
                                                viewModel.onSubtitleTrackSelected(track)
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(track.language)
                                                    .build()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(track.label)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                viewModel.onSubtitleTrackSelected(track)
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(track.language)
                                                    .build()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(track.label)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.weight(1f))
                            
                            Button(
                                onClick = { onInteract(); isTrackPanelVisible = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val POSITION_SAVE_INTERVAL_MS = 10_000L

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
