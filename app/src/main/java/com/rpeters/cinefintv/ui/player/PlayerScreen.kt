package com.rpeters.cinefintv.ui.player

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.rpeters.cinefintv.ui.player.PlayerConstants.CONTROLS_HIDE_DELAY_MS
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.player.PlayerConstants.NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when {
        uiState.isLoading || uiState.isRetrying -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.isRetrying) {
                            "Connection lost. Retrying (Attempt ${uiState.retryCount})..."
                        } else {
                            "Preparing player..."
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
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
                        color = MaterialTheme.colorScheme.onBackground
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
            val player = viewModel.setupPlayer(context)
            val coroutineScope = rememberCoroutineScope()
            var isTrackPanelVisible by remember { mutableStateOf(false) }
            var trackPanelSection by remember { mutableStateOf(SettingsSection.ALL) }
            var trackPanelAnchor by remember { mutableStateOf<Rect?>(null) }
            val playPauseFocusRequester = remember { FocusRequester() }
            val seekBarFocusRequester = remember { FocusRequester() }
            val playerFocusRequester = remember { FocusRequester() }
            val skipFocusRequester = remember { FocusRequester() }

            // Playback state — polled every 500ms
            var isPlaying by remember { mutableStateOf(true) }
            var isBuffering by remember { mutableStateOf(false) }
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            var bufferedFraction by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(player) {
                while (true) {
                    isPlaying = player.isPlaying
                    isBuffering = player.playbackState == Player.STATE_BUFFERING
                    position = player.currentPosition.coerceAtLeast(0L)
                    duration = player.duration.coerceAtLeast(0L)
                    bufferedFraction = player.bufferedPercentage / 100f
                    delay(500L)
                }
            }

            PlayerLifecycleManager(
                player = player,
                viewModel = viewModel,
                uiState = uiState,
                isPlaying = isPlaying,
                onNextEpisodeRequest = { nextId ->
                    coroutineScope.launch {
                        if (nextId.isNotBlank()) onOpenItem(nextId)
                        else {
                            // If no next episode ID but ENDED was called, just exit or handle accordingly
                        }
                    }
                }
            )

            // Controls visibility — auto-hide logic
            var controlsVisible by remember { mutableStateOf(true) }
            var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
            
            LaunchedEffect(isPlaying) {
                if (!isPlaying) {
                    controlsVisible = true
                }
            }

            LaunchedEffect(lastInteraction, isPlaying) {
                if (isPlaying && !isTrackPanelVisible) {
                    delay(CONTROLS_HIDE_DELAY_MS)
                    controlsVisible = false
                }
            }

            fun onInteract() {
                controlsVisible = true
                lastInteraction = System.currentTimeMillis()
            }

            LaunchedEffect(controlsVisible) {
                if (controlsVisible && !isTrackPanelVisible) {
                    seekBarFocusRequester.requestFocus()
                } else if (!controlsVisible) {
                    playerFocusRequester.requestFocus()
                }
            }

            var countdownRemainingMs by remember { mutableLongStateOf(-1L) }

            LaunchedEffect(position, duration) {
                if (duration > 0 && uiState.isEpisodicContent && uiState.autoPlayNextEpisode && uiState.nextEpisodeId != null) {
                    val remaining = duration - position
                    if (remaining in 1L..NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS) {
                        countdownRemainingMs = remaining
                    } else {
                        countdownRemainingMs = -1L
                    }
                } else {
                    countdownRemainingMs = -1L
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
                                Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                                    if (!controlsVisible) {
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
                // PlayerView (full screen)
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

                PlayerControls(
                    isVisible = controlsVisible,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    bufferedFraction = bufferedFraction,
                    uiState = uiState,
                    player = player,
                    playPauseFocusRequester = playPauseFocusRequester,
                    seekBarFocusRequester = seekBarFocusRequester,
                    onInteract = ::onInteract,
                    onSettingsClick = { section, anchor ->
                        trackPanelSection = section
                        trackPanelAnchor = anchor
                        isTrackPanelVisible = true
                    },
                    onBack = onBack,
                )

                // Buffering spinner — shown when player is rebuffering mid-playback
                if (isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                        )
                    }
                }

                // Skip Intro / Skip Credits button
                val introRange = uiState.introSkipRange
                val creditsRange = uiState.creditsSkipRange
                val activeSkipLabel = when {
                    introRange != null &&
                        position >= introRange.startMs &&
                        position <= (introRange.endMs ?: Long.MAX_VALUE) -> "Skip Intro"
                    creditsRange != null &&
                        position >= creditsRange.startMs &&
                        position <= (creditsRange.endMs ?: Long.MAX_VALUE) -> "Skip Credits"
                    else -> null
                }
                val activeSkipTargetMs = when (activeSkipLabel) {
                    "Skip Intro" -> introRange?.endMs?.coerceAtMost(duration) ?: duration
                    "Skip Credits" -> creditsRange?.endMs?.coerceAtMost(duration) ?: duration
                    else -> 0L
                }
                LaunchedEffect(activeSkipLabel) {
                    if (activeSkipLabel != null && !controlsVisible) {
                        runCatching { skipFocusRequester.requestFocus() }
                    }
                }
                AnimatedVisibility(
                    visible = activeSkipLabel != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 48.dp, vertical = 48.dp),
                ) {
                    Button(
                        onClick = {
                            player.seekTo(activeSkipTargetMs)
                            onInteract()
                        },
                        modifier = Modifier.focusRequester(skipFocusRequester),
                    ) {
                        Text(activeSkipLabel ?: "")
                    }
                }

                // Speed badge — persistent pill shown when speed is not 1×
                val expressiveColors = LocalCinefinExpressiveColors.current
                AnimatedVisibility(
                    visible = uiState.playbackSpeed != 1.0f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                ) {
                    val speedLabel = if (uiState.playbackSpeed == uiState.playbackSpeed.toLong().toFloat()) {
                        "${uiState.playbackSpeed.toInt()}×"
                    } else {
                        "${uiState.playbackSpeed}×"
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = expressiveColors.pillStrong,
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = speedLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                val nextTitle = uiState.nextEpisodeTitle
                if (countdownRemainingMs > 0 && nextTitle != null) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp)) {
                        NextEpisodeCard(
                            title = nextTitle,
                            thumbnailUrl = uiState.nextEpisodeThumbnailUrl,
                            remainingMs = countdownRemainingMs,
                            onPlayNow = {
                                coroutineScope.launch {
                                    viewModel.getNextEpisodeId()?.let { onOpenItem(it) }
                                }
                            }
                        )
                    }
                }

                if (uiState.shouldShowResumeDialog) {
                    ResumeDialog(
                        positionMs = uiState.savedPlaybackPositionMs,
                        onResume = { viewModel.onResumePlayback(resume = true) },
                        onStartOver = { viewModel.onResumePlayback(resume = false) }
                    )
                }

                PlayerTrackPanel(
                    isVisible = controlsVisible && isTrackPanelVisible,
                    section = trackPanelSection,
                    anchorBounds = trackPanelAnchor,
                    uiState = uiState,
                    audioTracks = uiState.audioTracks,
                    subtitleTracks = uiState.subtitleTracks,
                    onAudioTrackSelected = { viewModel.selectAudioTrack(it, position, isPlaying) },
                    onSubtitleTrackSelected = { viewModel.selectSubtitleTrack(it, position, isPlaying) },
                    onPlaybackSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                    onAutoPlayChange = { viewModel.setAutoPlayNextEpisode(it) },
                    onClose = {
                        isTrackPanelVisible = false
                        trackPanelAnchor = null
                    },
                    onInteract = ::onInteract
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onKeyEvent { 
                // Consuming all keys to prevent interaction with underlying player controls
                true 
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Resume Playback?",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            val totalSeconds = positionMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val timeStr = if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
            
            Text(
                text = "Would you like to continue from $timeStr or start from the beginning?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text("Resume")
                }
                OutlinedButton(onClick = onStartOver) {
                    Text("Start from beginning")
                }
            }
        }
    }
}
