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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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

private enum class SettingsSection { AUDIO, SUBTITLES, QUALITY, SPEED, ALL }

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val QUALITY_OPTIONS = listOf("Auto", "1080p", "720p", "480p")

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
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
            var trackPanelSection by remember { mutableStateOf(SettingsSection.ALL) }
            var selectedQuality by remember { mutableStateOf("Auto") }
            var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
            var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
            val playPauseFocusRequester = remember { FocusRequester() }
            val playerFocusRequester = remember { FocusRequester() }

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

            LaunchedEffect(player, uiState.playbackSpeed) {
                player.setPlaybackSpeed(uiState.playbackSpeed)
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
            
            LaunchedEffect(isPlaying) {
                if (!isPlaying) {
                    controlsVisible = true
                }
            }

            LaunchedEffect(lastInteraction, isPlaying) {
                if (isPlaying && !isTrackPanelVisible) {
                    delay(3_000L)
                    controlsVisible = false
                }
            }

            fun onInteract() {
                controlsVisible = true
                lastInteraction = System.currentTimeMillis()
            }

            LaunchedEffect(controlsVisible) {
                if (controlsVisible && !isTrackPanelVisible) {
                    playPauseFocusRequester.requestFocus()
                } else if (!controlsVisible) {
                    playerFocusRequester.requestFocus()
                }
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.Back, Key.Escape -> {
                                    when {
                                        isTrackPanelVisible -> {
                                            isTrackPanelVisible = false
                                            true
                                        }
                                        controlsVisible -> {
                                            onInteract()
                                            controlsVisible = false
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                    onInteract()
                                    if (!controlsVisible) {
                                        controlsVisible = true
                                    } else {
                                        if (isPlaying) player.pause() else player.play()
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    when {
                                        isTrackPanelVisible -> {
                                            onInteract()
                                            isTrackPanelVisible = false
                                            true
                                        }
                                        !controlsVisible -> {
                                            // Show controls; do NOT seek automatically
                                            onInteract()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                                    if (!controlsVisible) {
                                        // Show controls; do NOT seek automatically
                                        onInteract()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusRequester(playerFocusRequester)
                    .focusable()
            ) {
                // PlayerView (full screen, no built-in controller)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            useController = false
                            this.player = player
                            isFocusable = false
                            isFocusableInTouchMode = false
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
                        // Top Bar: Logo + Title + Season/Episode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                                .align(Alignment.TopStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // App logo
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = uiState.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val episodeInfo = buildString {
                                    uiState.seasonNumber?.let { append("Season $it") }
                                    uiState.episodeNumber?.let {
                                        if (isNotEmpty()) append("  •  ")
                                        append("Episode $it")
                                    }
                                }
                                if (episodeInfo.isNotEmpty()) {
                                    Text(
                                        text = episodeInfo,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Bottom Controls
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Progress Bar
                            if (duration > 0) {
                                // Time label above progress bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = formatMs(position),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = formatMs(duration),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                // Seek bar
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

                                // Playback controls row
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // Centered: Rewind / Play-Pause / Forward
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                player.seekTo((position - 10_000).coerceAtLeast(0))
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Replay10,
                                                contentDescription = "Rewind 10s",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                onInteract()
                                                if (isPlaying) player.pause() else player.play()
                                            },
                                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                                            scale = ButtonDefaults.scale(focusedScale = 1.1f)
                                        ) {
                                            Icon(
                                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                player.seekTo((position + 10_000).coerceAtMost(duration))
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Forward10,
                                                contentDescription = "Forward 10s",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Right side: settings buttons + back
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (uiState.isEpisodicContent) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Auto-play",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Switch(
                                                    checked = uiState.autoPlayNextEpisode,
                                                    onCheckedChange = {
                                                        onInteract()
                                                        viewModel.setAutoPlayNextEpisode(it)
                                                    },
                                                )
                                            }
                                            Spacer(Modifier.width(4.dp))
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                trackPanelSection = SettingsSection.SUBTITLES
                                                isTrackPanelVisible = true
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Subtitles,
                                                contentDescription = "Subtitles",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("CC")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                trackPanelSection = SettingsSection.AUDIO
                                                isTrackPanelVisible = true
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.AudioFile,
                                                contentDescription = "Audio",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Audio")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                trackPanelSection = SettingsSection.QUALITY
                                                isTrackPanelVisible = true
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.HighQuality,
                                                contentDescription = "Quality",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(selectedQuality)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                trackPanelSection = SettingsSection.SPEED
                                                isTrackPanelVisible = true
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Speed,
                                                contentDescription = "Speed",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (uiState.playbackSpeed == 1.0f) "1×"
                                                else "${uiState.playbackSpeed}×"
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                onInteract()
                                                trackPanelSection = SettingsSection.ALL
                                                isTrackPanelVisible = true
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.MoreHoriz,
                                                contentDescription = "More",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        OutlinedButton(onClick = { onInteract(); onBack() }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Exit player",
                                                modifier = Modifier.size(18.dp)
                                            )
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

                // Tracks / Settings Side Panel
                AnimatedVisibility(
                    visible = controlsVisible && isTrackPanelVisible,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(380.dp),
                        shape = RectangleShape,
                        colors = SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    ) {
                        val panelTitle = when (trackPanelSection) {
                            SettingsSection.AUDIO -> "Audio Track"
                            SettingsSection.SUBTITLES -> "Subtitles"
                            SettingsSection.QUALITY -> "Quality"
                            SettingsSection.SPEED -> "Playback Speed"
                            SettingsSection.ALL -> "Media Settings"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(panelTitle, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Audio section
                                if (trackPanelSection == SettingsSection.AUDIO || trackPanelSection == SettingsSection.ALL) {
                                    if (trackPanelSection == SettingsSection.ALL) {
                                        item {
                                            Text(
                                                "Audio Track",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    items(audioTracks.size) { index ->
                                        val track = audioTracks[index]
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
                                            ) { Text(track.label) }
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
                                            ) { Text(track.label) }
                                        }
                                    }
                                }

                                // Subtitles section
                                if (trackPanelSection == SettingsSection.SUBTITLES || trackPanelSection == SettingsSection.ALL) {
                                    if (trackPanelSection == SettingsSection.ALL) {
                                        item {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Subtitles",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    // None option
                                    item {
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
                                    }
                                    items(subtitleTracks.size) { index ->
                                        val track = subtitleTracks[index]
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
                                            ) { Text(track.label) }
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
                                            ) { Text(track.label) }
                                        }
                                    }
                                }

                                // Quality section
                                if (trackPanelSection == SettingsSection.QUALITY || trackPanelSection == SettingsSection.ALL) {
                                    if (trackPanelSection == SettingsSection.ALL) {
                                        item {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Quality",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    items(QUALITY_OPTIONS.size) { index ->
                                        val quality = QUALITY_OPTIONS[index]
                                        if (selectedQuality == quality) {
                                            Button(
                                                onClick = {
                                                    onInteract()
                                                    selectedQuality = quality
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(quality) }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    onInteract()
                                                    selectedQuality = quality
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(quality) }
                                        }
                                    }
                                }

                                // Speed section
                                if (trackPanelSection == SettingsSection.SPEED || trackPanelSection == SettingsSection.ALL) {
                                    if (trackPanelSection == SettingsSection.ALL) {
                                        item {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Playback Speed",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    items(PLAYBACK_SPEEDS.size) { index ->
                                        val speed = PLAYBACK_SPEEDS[index]
                                        val label = if (speed == 1.0f) "Normal (1×)" else "${speed}×"
                                        if (uiState.playbackSpeed == speed) {
                                            Button(
                                                onClick = {
                                                    onInteract()
                                                    viewModel.setPlaybackSpeed(speed)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(label) }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    onInteract()
                                                    viewModel.setPlaybackSpeed(speed)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(label) }
                                        }
                                    }
                                }
                            }

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

@UnstableApi
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
