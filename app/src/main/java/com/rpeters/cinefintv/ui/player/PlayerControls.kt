package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    uiState: PlayerUiState,
    player: ExoPlayer,
    playPauseFocusRequester: FocusRequester,
    onInteract: () -> Unit,
    onSettingsClick: (SettingsSection, Rect) -> Unit,
    onBack: () -> Unit,
) {
    val defaultBounds = Rect.Zero
    val (subtitleButtonBounds, setSubtitleButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val (audioButtonBounds, setAudioButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val (speedButtonBounds, setSpeedButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val (moreButtonBounds, setMoreButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val backFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = isVisible,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    OutlinedButton(
                        onClick = { onInteract(); onBack() },
                        modifier = Modifier
                            .focusRequester(backFocusRequester)
                            .focusProperties {
                                down = subtitleFocusRequester
                            },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Black.copy(alpha = 0.24f),
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.06f),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit player",
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Back")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.logoUrl != null) {
                            AsyncImage(
                                model = uiState.logoUrl,
                                contentDescription = "Logo",
                                modifier = Modifier
                                    .height(60.dp)
                                    .width(IntrinsicSize.Max),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.title.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }

                        Spacer(Modifier.width(20.dp))
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
                }

                Surface(
                    shape = CircleShape,
                    colors = SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isPlaying) "Playing" else "Paused",
                            style = MaterialTheme.typography.labelLarge,
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
                            text = "-${formatMs((duration - position).coerceAtLeast(0L))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatMs(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SeekBarControl(
                        position = position,
                        duration = duration,
                        player = player,
                        focusRequester = seekBarFocusRequester,
                        down = playPauseFocusRequester,
                        onInteract = onInteract,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvActionButton(
                                label = "Subtitles",
                                icon = {
                                    Icon(
                                        Icons.Default.ClosedCaption,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.SUBTITLES, subtitleButtonBounds)
                                },
                                modifier = Modifier
                                    .focusRequester(subtitleFocusRequester)
                                    .focusProperties {
                                        right = audioFocusRequester
                                        left = backFocusRequester
                                        up = backFocusRequester
                                        down = seekBarFocusRequester
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        setSubtitleButtonBounds(coordinates.boundsInRoot())
                                    },
                            )

                            Spacer(Modifier.width(10.dp))

                            TvActionButton(
                                label = "Audio",
                                icon = {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.AUDIO, audioButtonBounds)
                                },
                                modifier = Modifier
                                    .focusRequester(audioFocusRequester)
                                    .focusProperties {
                                        left = subtitleFocusRequester
                                        right = rewindFocusRequester
                                        up = backFocusRequester
                                        down = seekBarFocusRequester
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        setAudioButtonBounds(coordinates.boundsInRoot())
                                    },
                            )
                        }

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TransportButton(
                                label = "Back 10",
                                icon = Icons.Default.Replay10,
                                buttonSize = 58.dp,
                                iconSize = 28.dp,
                                focusRequester = rewindFocusRequester,
                                left = audioFocusRequester,
                                right = playPauseFocusRequester,
                                up = seekBarFocusRequester,
                                down = rewindFocusRequester,
                                onClick = {
                                    onInteract()
                                    player.seekTo((position - 10_000).coerceAtLeast(0))
                                },
                            )

                            Spacer(Modifier.width(16.dp))

                            Button(
                                onClick = {
                                    onInteract()
                                    if (isPlaying) player.pause() else player.play()
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .focusRequester(playPauseFocusRequester)
                                    .focusProperties {
                                        left = rewindFocusRequester
                                        right = forwardFocusRequester
                                        up = seekBarFocusRequester
                                        down = playPauseFocusRequester
                                    },
                                shape = ButtonDefaults.shape(CircleShape),
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black,
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1.14f),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = androidx.compose.foundation.BorderStroke(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                                        )
                                    )
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        modifier = Modifier
                                            .size(36.dp)
                                            .offset(x = if (isPlaying) 0.dp else 1.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            TransportButton(
                                label = "Skip 10",
                                icon = Icons.Default.Forward10,
                                buttonSize = 58.dp,
                                iconSize = 28.dp,
                                focusRequester = forwardFocusRequester,
                                left = playPauseFocusRequester,
                                right = speedFocusRequester,
                                up = seekBarFocusRequester,
                                down = forwardFocusRequester,
                                onClick = {
                                    onInteract()
                                    player.seekTo((position + 10_000).coerceAtMost(duration))
                                },
                            )
                        }

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvActionButton(
                                label = if (uiState.playbackSpeed == 1.0f) "Speed 1x" else "Speed ${uiState.playbackSpeed}x",
                                icon = {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.SPEED, speedButtonBounds)
                                },
                                modifier = Modifier
                                    .focusRequester(speedFocusRequester)
                                    .focusProperties {
                                        left = forwardFocusRequester
                                        right = settingsFocusRequester
                                        up = backFocusRequester
                                        down = seekBarFocusRequester
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        setSpeedButtonBounds(coordinates.boundsInRoot())
                                    },
                            )

                            Spacer(Modifier.width(10.dp))

                            TvActionButton(
                                label = if (uiState.isEpisodicContent) "Playback" else "Settings",
                                icon = {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    onInteract()
                                    onSettingsClick(SettingsSection.ALL, moreButtonBounds)
                                },
                                modifier = Modifier
                                    .focusRequester(settingsFocusRequester)
                                    .focusProperties {
                                        left = speedFocusRequester
                                        up = backFocusRequester
                                        down = seekBarFocusRequester
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        setMoreButtonBounds(coordinates.boundsInRoot())
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodeCountdown(
    countdownRemaining: Long,
    nextEpisodeTitle: String?,
    onPlayNext: () -> Unit
) {
    AnimatedVisibility(
        visible = countdownRemaining > 0,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        androidx.tv.material3.Card(
            onClick = onPlayNext,
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
                    text = nextEpisodeTitle ?: "Coming up next",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Starting in $countdownRemaining seconds...",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onPlayNext,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Play Now")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 140.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.24f),
            contentColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            )
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekBarControl(
    position: Long,
    duration: Long,
    player: ExoPlayer,
    focusRequester: FocusRequester,
    down: FocusRequester,
    onInteract: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf(0) }
    val trackColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "SeekBarTrackColor",
    )

    LaunchedEffect(seekDirection, duration) {
        if (seekDirection == 0 || duration <= 0L) return@LaunchedEffect

        while (seekDirection != 0) {
            val targetPosition = (player.currentPosition + (seekDirection * SEEK_BAR_SCRUB_STEP_MS))
                .coerceIn(0L, duration)
            player.seekTo(targetPosition)
            onInteract()
            kotlinx.coroutines.delay(SEEK_BAR_REPEAT_INTERVAL_MS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                this.down = down
            }
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (!isFocused) {
                    seekDirection = 0
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                when {
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                        seekDirection = -1
                        true
                    }
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                        seekDirection = 1
                        true
                    }
                    keyEvent.type == KeyEventType.KeyUp &&
                        (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) -> {
                        seekDirection = 0
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.Center)
                .background(trackColor, MaterialTheme.shapes.small)
        ) {
            val progressFraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            )

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction.coerceAtLeast(0.02f))
                )
                {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TransportButton(
    label: String,
    icon: ImageVector,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    focusRequester: FocusRequester,
    left: FocusRequester,
    right: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val labelColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "TransportLabelColor",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .size(buttonSize)
                .focusRequester(focusRequester)
                .focusProperties {
                    this.left = left
                    this.right = right
                    this.up = up
                    this.down = down
                }
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus },
            shape = ButtonDefaults.shape(CircleShape),
            colors = ButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.22f),
                contentColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            scale = ButtonDefaults.scale(focusedScale = 1.1f),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
    }
}

private const val SEEK_BAR_SCRUB_STEP_MS = 10_000L
private const val SEEK_BAR_REPEAT_INTERVAL_MS = 140L
