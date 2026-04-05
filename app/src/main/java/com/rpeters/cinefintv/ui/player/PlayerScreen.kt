package com.rpeters.cinefintv.ui.player

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinDialogActions
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferences
import com.rpeters.cinefintv.data.preferences.SubtitleBackground
import com.rpeters.cinefintv.data.preferences.SubtitleFont
import com.rpeters.cinefintv.data.preferences.SubtitleTextColor
import com.rpeters.cinefintv.ui.player.PlayerConstants.CONTROLS_HIDE_DELAY_MS
import com.rpeters.cinefintv.ui.player.PlayerConstants.NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS
import com.rpeters.cinefintv.ui.player.PlayerConstants.PROGRESS_UPDATE_INTERVAL_MS
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class PlayerRenderState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferedFraction: Float = 0f,
)

private fun PlayerView.applySubtitleAppearance(preferences: SubtitleAppearancePreferences) {
    subtitleView?.apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, preferences.textSize.sizeSp)
        setStyle(
            CaptionStyleCompat(
                textColorFor(preferences.textColor),
                backgroundColorFor(preferences.background),
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                AndroidColor.BLACK,
                typefaceFor(preferences.font),
            )
        )
    }
}

private fun backgroundColorFor(background: SubtitleBackground): Int = when (background) {
    SubtitleBackground.NONE -> AndroidColor.TRANSPARENT
    SubtitleBackground.BLACK -> AndroidColor.BLACK
    SubtitleBackground.SEMI_TRANSPARENT -> AndroidColor.argb(160, 0, 0, 0)
}

private fun textColorFor(textColor: SubtitleTextColor): Int = when (textColor) {
    SubtitleTextColor.WHITE -> AndroidColor.WHITE
    SubtitleTextColor.OFF_WHITE -> AndroidColor.rgb(240, 240, 230)
    SubtitleTextColor.YELLOW -> AndroidColor.rgb(255, 235, 120)
    SubtitleTextColor.CYAN -> AndroidColor.rgb(150, 235, 255)
    SubtitleTextColor.GREEN -> AndroidColor.rgb(170, 255, 170)
}

private fun typefaceFor(font: SubtitleFont): Typeface? = when (font) {
    SubtitleFont.DEFAULT -> null
    SubtitleFont.SANS_SERIF -> Typeface.SANS_SERIF
    SubtitleFont.SERIF -> Typeface.SERIF
    SubtitleFont.MONOSPACE -> Typeface.MONOSPACE
    SubtitleFont.ROBOTO -> Typeface.create("sans-serif", Typeface.NORMAL)
    SubtitleFont.ROBOTO_FLEX -> Typeface.create("sans-serif", Typeface.NORMAL)
    SubtitleFont.ROBOTO_SERIF -> Typeface.create("serif", Typeface.NORMAL)
    SubtitleFont.ROBOTO_MONO -> Typeface.create("monospace", Typeface.NORMAL)
}

@Composable
private fun PlayerVideoSurface(
    exoPlayer: ExoPlayer,
    subtitleAppearance: SubtitleAppearancePreferences,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                useController = false
                player = exoPlayer
                isFocusable = false
                isFocusableInTouchMode = false
                applySubtitleAppearance(subtitleAppearance)
            }
        },
        update = { pv ->
            if (pv.player !== exoPlayer) {
                pv.player = exoPlayer
            }
            pv.applySubtitleAppearance(subtitleAppearance)
        },
    )
}

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
            val currentContext by rememberUpdatedState(context)
            var player by remember { mutableStateOf<ExoPlayer?>(null) }

            LaunchedEffect(currentContext) {
                player = viewModel.setupPlayer(currentContext)
            }

            if (player == null) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                val exoPlayer = player ?: return
                var renderState by remember(exoPlayer) {
                    mutableStateOf(
                        PlayerRenderState(
                            isPlaying = exoPlayer.isPlaying,
                            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                            position = exoPlayer.currentPosition.coerceAtLeast(0L),
                            duration = exoPlayer.duration.coerceAtLeast(0L),
                            bufferedFraction = exoPlayer.bufferedPercentage / 100f,
                        )
                    )
                }

                DisposableEffect(exoPlayer) {
                    fun captureState() {
                        renderState = PlayerRenderState(
                            isPlaying = exoPlayer.isPlaying,
                            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                            position = exoPlayer.currentPosition.coerceAtLeast(0L),
                            duration = exoPlayer.duration.coerceAtLeast(0L),
                            bufferedFraction = exoPlayer.bufferedPercentage / 100f,
                        )
                    }

                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            captureState()
                        }
                    }

                    captureState()
                    exoPlayer.addListener(listener)
                    onDispose {
                        exoPlayer.removeListener(listener)
                    }
                }

                // Periodic progress updates to keep seeker and time in sync
                LaunchedEffect(exoPlayer, renderState.isPlaying) {
                    if (renderState.isPlaying) {
                        while (true) {
                            renderState = PlayerRenderState(
                                isPlaying = exoPlayer.isPlaying,
                                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                                position = exoPlayer.currentPosition.coerceAtLeast(0L),
                                duration = exoPlayer.duration.coerceAtLeast(0L),
                                bufferedFraction = exoPlayer.bufferedPercentage / 100f,
                            )
                            delay(PROGRESS_UPDATE_INTERVAL_MS)
                        }
                    }
                }

                val coroutineScope = rememberCoroutineScope()

                PlayerLifecycleManager(
                    player = exoPlayer,
                    viewModel = viewModel,
                    uiState = uiState,
                    isPlaying = renderState.isPlaying,
                    onNextEpisodeRequest = { nextId ->
                        coroutineScope.launch {
                            if (nextId.isNotBlank()) {
                                onOpenItem(nextId)
                            } else {
                                onBack()
                            }
                        }
                    }
                )

                PlayerPlaybackContent(
                    exoPlayer = exoPlayer,
                    uiState = uiState,
                    renderState = renderState,
                    onBack = onBack,
                    onOpenItem = onOpenItem,
                    onResumePlayback = { resume -> viewModel.onResumePlayback(resume) },
                    onAudioTrackSelected = {
                        viewModel.selectAudioTrack(it, renderState.position, renderState.isPlaying)
                    },
                    onSubtitleTrackSelected = {
                        viewModel.selectSubtitleTrack(it, renderState.position, renderState.isPlaying)
                    },
                    onQualitySelected = {
                        viewModel.setTranscodingQuality(it, renderState.position, renderState.isPlaying)
                    },
                    onPlaybackSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                    onAutoPlayChange = { viewModel.setAutoPlayNextEpisode(it) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerPlaybackContent(
    exoPlayer: ExoPlayer,
    uiState: PlayerUiState,
    renderState: PlayerRenderState,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onResumePlayback: (Boolean) -> Unit,
    onAudioTrackSelected: (TrackOption) -> Unit,
    onSubtitleTrackSelected: (TrackOption?) -> Unit,
    onQualitySelected: (com.rpeters.cinefintv.data.preferences.TranscodingQuality) -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showVideoSurface: Boolean = true,
    initialControlsVisible: Boolean = true,
    controlsHideDelayMs: Long = CONTROLS_HIDE_DELAY_MS,
) {
    val coroutineScope = rememberCoroutineScope()
    var isTrackPanelVisible by remember { mutableStateOf(false) }
    var trackPanelSection by remember { mutableStateOf(SettingsSection.ALL) }
    var trackPanelAnchor by remember { mutableStateOf<Rect?>(null) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val playerFocusRequester = remember { FocusRequester() }
    val skipFocusRequester = remember { FocusRequester() }
    var overlayActionFocused by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(initialControlsVisible) }
    var isContentShelfVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val expressiveColors = LocalCinefinExpressiveColors.current

    LaunchedEffect(renderState.isPlaying) {
        if (!renderState.isPlaying) {
            controlsVisible = true
        }
    }

    LaunchedEffect(lastInteraction, renderState.isPlaying, isTrackPanelVisible, controlsHideDelayMs) {
        if (renderState.isPlaying && !isTrackPanelVisible) {
            delay(controlsHideDelayMs)
            controlsVisible = false
            isContentShelfVisible = false
        }
    }

    fun onInteract() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    LaunchedEffect(controlsVisible, isTrackPanelVisible) {
        if (controlsVisible && !isTrackPanelVisible) {
            seekBarFocusRequester.requestFocus()
        } else if (!controlsVisible) {
            playerFocusRequester.requestFocus()
            isContentShelfVisible = false
        }
    }

    val introRange = uiState.introSkipRange
    val creditsRange = uiState.creditsSkipRange
    val activeSkipLabel = when {
        isInSkipRange(renderState.position, introRange) -> "Skip Intro"
        isInSkipRange(renderState.position, creditsRange) -> "Skip Credits"
        else -> null
    }
    val activeSkipTargetMs = when (activeSkipLabel) {
        "Skip Intro" -> introRange?.endMs?.coerceAtMost(renderState.duration) ?: renderState.duration
        "Skip Credits" -> creditsRange?.endMs?.coerceAtMost(renderState.duration) ?: renderState.duration
        else -> 0L
    }

    LaunchedEffect(activeSkipLabel, controlsVisible) {
        if (activeSkipLabel != null && !controlsVisible) {
            withFrameNanos { }
            runCatching { skipFocusRequester.requestFocus() }
        }
    }

    val remaining = if (renderState.duration > 0L) {
        (renderState.duration - renderState.position).coerceAtLeast(0L)
    } else {
        -1L
    }
    val showNextUp = shouldShowNextEpisodeCard(
        uiState = uiState,
        positionMs = renderState.position,
        durationMs = renderState.duration,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(PlayerTestTags.PlaybackRoot)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Back, Key.Escape -> {
                            when {
                                isTrackPanelVisible -> {
                                    isTrackPanelVisible = false
                                    true
                                }
                                isContentShelfVisible -> {
                                    isContentShelfVisible = false
                                    onInteract()
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
                            if (!controlsVisible && overlayActionFocused) {
                                return@onKeyEvent false
                            }
                            val wereControlsVisible = controlsVisible
                            onInteract()
                            if (!wereControlsVisible) {
                                controlsVisible = true
                            } else {
                                if (renderState.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (!controlsVisible) {
                                onInteract()
                                true
                            } else if (!isContentShelfVisible && uiState.contentRow != null) {
                                isContentShelfVisible = true
                                onInteract()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            if (!controlsVisible) {
                                onInteract()
                                true
                            } else if (isContentShelfVisible) {
                                // We check if we should hide it manually. 
                                // But PlayerControls also calls onHideShelf via focus movement.
                                // For safety we can handle it here too if focus doesn't move.
                                false
                            } else {
                                onInteract()
                                true
                            }
                        }
                        Key.DirectionLeft, Key.DirectionRight -> {
                            if (!controlsVisible) {
                                onInteract()
                                // Seek immediately on first press rather than requiring a second press
                                if (renderState.duration > 0L && !overlayActionFocused) {
                                    val seekMs = uiState.videoSeekIncrement.millis
                                    val delta = if (keyEvent.key == Key.DirectionLeft) -seekMs else seekMs
                                    val newPos = (exoPlayer.currentPosition + delta)
                                        .coerceIn(0L, renderState.duration)
                                    exoPlayer.seekTo(newPos)
                                }
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
        if (showVideoSurface) {
            PlayerVideoSurface(
                exoPlayer = exoPlayer,
                subtitleAppearance = uiState.subtitleAppearance,
                modifier = Modifier.fillMaxSize(),
            )
        }

        PlayerControls(
            isVisible = controlsVisible,
            isPlaying = renderState.isPlaying,
            position = renderState.position,
            duration = renderState.duration,
            bufferedFraction = renderState.bufferedFraction,
            uiState = uiState,
            player = exoPlayer,
            playPauseFocusRequester = playPauseFocusRequester,
            seekBarFocusRequester = seekBarFocusRequester,
            isContentShelfVisible = isContentShelfVisible,
            onHideShelf = { isContentShelfVisible = false },
            onInteract = ::onInteract,
            onSettingsClick = { section, anchor ->
                trackPanelSection = section
                trackPanelAnchor = anchor
                isTrackPanelVisible = true
            },
            onBack = onBack,
            onOpenItem = onOpenItem,
        )

        if (renderState.isBuffering) {
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

        Column(
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.TopEnd)
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = uiState.isHdrPlayback,
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut() + slideOutHorizontally { it / 2 },
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "HDR",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.playbackSpeed != 1.0f,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val speedLabel = if (uiState.playbackSpeed == 1.0f) "1×" else "${uiState.playbackSpeed}×"
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
        }

        Column(
            modifier = Modifier
                .zIndex(2f)
                .align(Alignment.BottomEnd)
                .padding(bottom = 96.dp, end = 48.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = activeSkipLabel != null,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it },
            ) {
                SkipActionCard(
                    label = activeSkipLabel ?: "Skip",
                    subtitle = "Available now",
                    onSkip = {
                        exoPlayer.seekTo(activeSkipTargetMs)
                        onInteract()
                    },
                    buttonFocusRequester = skipFocusRequester,
                    modifier = Modifier
                        .testTag(PlayerTestTags.SkipAction)
                        .onFocusChanged { overlayActionFocused = it.hasFocus },
                )
            }

            AnimatedVisibility(
                visible = showNextUp,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it },
            ) {
                NextEpisodeCard(
                    seriesTitle = uiState.title,
                    title = uiState.nextEpisodeTitle ?: "Next Episode",
                    thumbnailUrl = uiState.nextEpisodeThumbnailUrl,
                    remainingMs = remaining.coerceAtLeast(0L),
                    autoPlayEnabled = uiState.autoPlayNextEpisode,
                    autoFocusPlayNow = showNextUp && !controlsVisible,
                    onActionFocusChanged = { overlayActionFocused = it },
                    onPlayNow = {
                        uiState.nextEpisodeId?.let { onOpenItem(it) }
                    },
                    modifier = Modifier.testTag(PlayerTestTags.NextEpisodeCard),
                )
            }
        }

        if (uiState.shouldShowResumeDialog) {
            ResumeDialog(
                positionMs = uiState.savedPlaybackPositionMs,
                onResume = { onResumePlayback(true) },
                onStartOver = { onResumePlayback(false) }
            )
        }

        PlayerTrackPanel(
            isVisible = controlsVisible && isTrackPanelVisible,
            section = trackPanelSection,
            anchorBounds = trackPanelAnchor,
            uiState = uiState,
            audioTracks = uiState.audioTracks,
            subtitleTracks = uiState.subtitleTracks,
            onAudioTrackSelected = onAudioTrackSelected,
            onSubtitleTrackSelected = onSubtitleTrackSelected,
            onSectionSelected = { trackPanelSection = it },
            onQualitySelected = onQualitySelected,
            onPlaybackSpeedSelected = onPlaybackSpeedSelected,
            onAutoPlayChange = onAutoPlayChange,
            onClose = {
                isTrackPanelVisible = false
                trackPanelAnchor = null
            },
            onInteract = ::onInteract
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    CinefinDialogSurface(
        onDismissRequest = onStartOver,
        modifier = Modifier
            .width(560.dp)
            .testTag(PlayerTestTags.ResumeDialog),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
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

            CinefinDialogActions(
                dismissLabel = "Start from beginning",
                confirmLabel = "Resume",
                onDismiss = onStartOver,
                onConfirm = onResume,
            )
        }
    }
}
