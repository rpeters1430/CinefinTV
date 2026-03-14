package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    bufferedFraction: Float,
    uiState: PlayerUiState,
    player: ExoPlayer,
    playPauseFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onInteract: () -> Unit,
    onSettingsClick: (SettingsSection, Rect) -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LocalCinefinSpacing.current
    val defaultBounds = Rect.Zero
    val (subtitleButtonBounds, setSubtitleButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val (audioButtonBounds, setAudioButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val (speedButtonBounds, setSpeedButtonBounds) = remember { mutableStateOf(defaultBounds) }
    val (moreButtonBounds, setMoreButtonBounds) = remember { mutableStateOf(defaultBounds) }
    
    val backFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val skipBackFocusRequester = remember { FocusRequester() }
    val skipForwardFocusRequester = remember { FocusRequester() }

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
                        0.0f to Color.Black.copy(alpha = 0.7f),
                        0.25f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        ) {
            // Minimalist Top-Left Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.gutter)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                ) {
                    IconButton(
                        onClick = { onInteract(); onBack() },
                        modifier = Modifier
                            .focusRequester(backFocusRequester)
                            .focusProperties {
                                down = seekBarFocusRequester
                            },
                        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
                        colors = IconButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = Color.White,
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val episodeInfo = buildString {
                            uiState.seasonNumber?.let { append("S$it") }
                            uiState.episodeNumber?.let {
                                if (isNotEmpty()) append(" : E$it")
                                else append("E$it")
                            }
                        }
                        if (episodeInfo.isNotEmpty()) {
                            Text(
                                text = episodeInfo,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // Bottom controls — transparent, sits directly on gradient
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Seek row: [current time] [seekbar] [duration]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = formatMs(position),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    SeekBarControl(
                        position = position,
                        duration = duration,
                        bufferedFraction = bufferedFraction,
                        chapters = uiState.chapters,
                        onSeek = { player.seekTo(it) },
                        onInteract = onInteract,
                        focusRequester = seekBarFocusRequester,
                        up = backFocusRequester,
                        down = playPauseFocusRequester,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMs(duration),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }

                // Button row: [-10] [spacer] [▶] [spacer] [+10] [divider] [CC] [♪] [⚙]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Skip Back 10s
                    ActionIconButton(
                        icon = Icons.Default.Replay10,
                        onClick = { onInteract(); player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                        modifier = Modifier
                            .focusRequester(skipBackFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                right = playPauseFocusRequester
                            }
                    )

                    Spacer(Modifier.weight(1f))

                    // Play/Pause
                    PlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = {
                            onInteract()
                            if (isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(playPauseFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                left = skipBackFocusRequester
                                right = skipForwardFocusRequester
                            }
                    )

                    Spacer(Modifier.weight(1f))

                    // Skip Forward 10s
                    ActionIconButton(
                        icon = Icons.Default.Forward10,
                        onClick = { onInteract(); player.seekTo((player.currentPosition + 10_000L).coerceIn(0L, duration)) },
                        modifier = Modifier
                            .focusRequester(skipForwardFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                left = playPauseFocusRequester
                                right = subtitleFocusRequester
                            }
                    )

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color.White.copy(alpha = 0.4f))
                    )

                    // CC (Subtitles)
                    ActionIconButton(
                        icon = Icons.Default.ClosedCaption,
                        onClick = { onInteract(); onSettingsClick(SettingsSection.SUBTITLES, subtitleButtonBounds) },
                        modifier = Modifier
                            .focusRequester(subtitleFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                left = skipForwardFocusRequester
                                right = audioFocusRequester
                            }
                            .onGloballyPositioned { setSubtitleButtonBounds(it.boundsInRoot()) }
                    )

                    // ♪ (Audio tracks)
                    ActionIconButton(
                        icon = Icons.Default.GraphicEq,
                        onClick = { onInteract(); onSettingsClick(SettingsSection.AUDIO, audioButtonBounds) },
                        modifier = Modifier
                            .focusRequester(audioFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                left = subtitleFocusRequester
                                right = settingsFocusRequester
                            }
                            .onGloballyPositioned { setAudioButtonBounds(it.boundsInRoot()) }
                    )

                    // ⚙ (All settings)
                    ActionIconButton(
                        icon = Icons.Default.Settings,
                        onClick = { onInteract(); onSettingsClick(SettingsSection.ALL, moreButtonBounds) },
                        modifier = Modifier
                            .focusRequester(settingsFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                left = audioFocusRequester
                            }
                            .onGloballyPositioned { setMoreButtonBounds(it.boundsInRoot()) }
                    )
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
    val expressiveColors = LocalCinefinExpressiveColors.current
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
                    color = expressiveColors.titleAccent
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
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        scale = IconButtonDefaults.scale(focusedScale = 1.2f),
        colors = IconButtonDefaults.colors(
            containerColor = Color.White,
            contentColor = Color.Black,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White
        )
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(40.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.8f),
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
            focusedContentColor = Color.White
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekBarControl(
    position: Long,
    duration: Long,
    bufferedFraction: Float,
    chapters: List<ChapterMarker>,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
    focusRequester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf(0) }
    // Tracks the live seek position during a hold-seek; resyncs when polled position updates
    var seekPosition by remember(position) { mutableLongStateOf(position) }

    val barHeight by animateDpAsState(if (isFocused) 8.dp else 3.dp, label = "BarHeight")
    val thumbScale by animateFloatAsState(if (isFocused) 1f else 0f, label = "ThumbScale")

    LaunchedEffect(seekDirection, duration) {
        if (seekDirection == 0 || duration <= 0L) return@LaunchedEffect
        while (seekDirection != 0) {
            seekPosition = (seekPosition + seekDirection * com.rpeters.cinefintv.core.constants.Constants.PLAYER_SEEK_INCREMENT_MS)
                .coerceIn(0L, duration)
            onSeek(seekPosition)
            onInteract()
            kotlinx.coroutines.delay(100L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                this.up = up
                this.down = down
            }
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (!isFocused) seekDirection = 0
            }
            .onPreviewKeyEvent { keyEvent ->
                when {
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                        seekDirection = -1; true
                    }
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                        seekDirection = 1; true
                    }
                    keyEvent.type == KeyEventType.KeyUp &&
                        (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) -> {
                        seekDirection = 0; true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            val progressFraction =
                if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                else 0f
            val bufferedClamped = bufferedFraction.coerceIn(0f, 1f)

            // Buffered section — lighter grey between progress end and buffered position
            if (bufferedClamped > progressFraction) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bufferedClamped)
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }

            // Progress fill — CinefinRed
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Chapter marker ticks
            chapters.forEach { chapter ->
                val chapterFraction =
                    if (duration > 0L) (chapter.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    else 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * chapterFraction - 1.dp)
                        .width(2.dp)
                        .height(barHeight)
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }

            // Thumb — only visible when focused
            if (thumbScale > 0f) {
                val thumbDp = 20.dp * thumbScale
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * progressFraction - thumbDp / 2)
                        .size(thumbDp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(width = 3.dp, color = Color.White, shape = CircleShape)
                )
            }
        }

        // Timestamp bubble — floats above the thumb when focused
        if (isFocused) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val progressFraction =
                    if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    else 0f
                val bubbleWidth = 56.dp
                val thumbX = maxWidth * progressFraction
                val clampedX = thumbX.coerceIn(0.dp, maxWidth - bubbleWidth)

                Surface(
                    modifier = Modifier
                        .offset(x = clampedX, y = (-30).dp)
                        .width(bubbleWidth),
                    shape = RoundedCornerShape(4.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.85f)
                    )
                ) {
                    Text(
                        text = formatMs(position),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
