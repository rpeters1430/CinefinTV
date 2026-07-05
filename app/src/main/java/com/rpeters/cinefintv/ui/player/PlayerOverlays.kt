package com.rpeters.cinefintv.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.SurfaceDark
import com.rpeters.cinefintv.ui.player.PlayerConstants.NEXT_EPISODE_POLL_INTERVAL_MS
import com.rpeters.cinefintv.ui.player.PlayerConstants.SKIP_RANGE_POLL_INTERVAL_MS
import kotlinx.coroutines.delay

@Composable
internal fun BufferingIndicator(
    isBufferingProvider: () -> Boolean,
) {
    if (isBufferingProvider()) {
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodeOverlay(
    player: Player,
    uiState: PlayerUiState,
    autoFocusPlayNow: Boolean,
    onPlayNow: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNextUp by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, uiState) {
        while (true) {
            val duration = player.duration.coerceAtLeast(0L)
            val pos = player.currentPosition
            val show = shouldShowNextEpisodeCard(
                uiState = uiState,
                positionMs = pos,
                durationMs = duration,
            )
            val rem = if (duration > 0L) {
                (duration - pos).coerceAtLeast(0L)
            } else {
                0L
            }

            showNextUp = show
            remainingMs = rem

            delay(NEXT_EPISODE_POLL_INTERVAL_MS)
        }
    }

    AnimatedVisibility(
        visible = showNextUp,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it },
        modifier = modifier,
    ) {
        NextEpisodeCard(
            seriesTitle = uiState.title,
            title = uiState.nextEpisodeTitle ?: "Next Episode",
            thumbnailUrl = uiState.nextEpisodeThumbnailUrl,
            remainingMs = remainingMs,
            autoPlayEnabled = uiState.autoPlayNextEpisode,
            autoFocusPlayNow = autoFocusPlayNow,
            onActionFocusChanged = onFocusChanged,
            onPlayNow = onPlayNow,
            modifier = Modifier.testTag(PlayerTestTags.NextEpisodeCard),
        )
    }
}

@Composable
internal fun PlayerTopBadges(
    isHdr: Boolean,
    qualityLabel: String?,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Row(
        modifier = modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (playbackSpeed != 1.0f) {
            BadgeSurface {
                Text(
                    text = "${playbackSpeed}x",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = expressiveColors.playerContentPrimary
                )
            }
        }
        if (isHdr) {
            BadgeSurface(color = expressiveColors.badgeHDR.copy(alpha = 0.9f)) {
                Text(
                    text = "HDR",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
        qualityLabel?.let { label ->
            BadgeSurface {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = expressiveColors.playerContentPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BadgeSurface(
    color: Color? = null,
    content: @Composable () -> Unit
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Surface(
        shape = RoundedCornerShape(4.dp),
        colors = SurfaceDefaults.colors(
            containerColor = color ?: expressiveColors.surfaceContainerHigh.copy(alpha = 0.7f)
        ),
        border = Border(
            border = BorderStroke(1.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.1f))
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
internal fun PlayerSkipIntroAction(
    player: Player,
    introRange: SkipRange?,
    autoSkip: Boolean,
    controlsVisible: Boolean,
    onSkip: (Long) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSkip by remember { mutableStateOf(false) }
    var skipTargetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, introRange, autoSkip) {
        var hasAutoSkipped = false
        while (true) {
            val pos = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            val inRange = isInSkipRange(pos, introRange)
            val targetMs = introRange?.endMs?.coerceAtMost(duration) ?: duration

            if (inRange && autoSkip && !hasAutoSkipped) {
                hasAutoSkipped = true
                onSkip(targetMs)
            }
            if (!inRange) hasAutoSkipped = false

            showSkip = inRange
            skipTargetMs = targetMs
            delay(SKIP_RANGE_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(showSkip, controlsVisible, autoSkip) {
        if (showSkip && !controlsVisible && !autoSkip) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = showSkip,
        enter = fadeIn() + slideInHorizontally { -it / 2 },
        exit = fadeOut() + slideOutHorizontally { -it / 2 },
        modifier = modifier,
    ) {
        SkipActionCard(
            label = "Skip Intro",
            subtitle = if (autoSkip) "Auto-skipping" else "Press to skip",
            onSkip = { onSkip(skipTargetMs) },
            buttonFocusRequester = focusRequester,
            modifier = Modifier
                .testTag(PlayerTestTags.SkipIntroAction)
                .onFocusChanged { onFocusChanged(it.hasFocus) },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
internal fun PlayerSkipCreditsAction(
    player: Player,
    creditsRange: SkipRange?,
    autoSkip: Boolean,
    controlsVisible: Boolean,
    onSkip: (Long) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSkip by remember { mutableStateOf(false) }
    var skipTargetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, creditsRange, autoSkip) {
        var hasAutoSkipped = false
        while (true) {
            val pos = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            val inRange = isInSkipRange(pos, creditsRange)
            val targetMs = creditsRange?.endMs?.coerceAtMost(duration) ?: duration

            if (inRange && autoSkip && !hasAutoSkipped) {
                hasAutoSkipped = true
                onSkip(targetMs)
            }
            if (!inRange) hasAutoSkipped = false

            showSkip = inRange
            skipTargetMs = targetMs
            delay(SKIP_RANGE_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(showSkip, controlsVisible, autoSkip) {
        if (showSkip && !controlsVisible && !autoSkip) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = showSkip,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        SkipActionCard(
            label = "Skip Credits",
            subtitle = if (autoSkip) "Auto-skipping" else "Press to skip",
            onSkip = { onSkip(skipTargetMs) },
            buttonFocusRequester = focusRequester,
            modifier = Modifier
                .testTag(PlayerTestTags.SkipCreditsAction)
                .onFocusChanged { onFocusChanged(it.hasFocus) },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SkipActionCard(
    label: String,
    subtitle: String,
    onSkip: () -> Unit,
    buttonFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Surface(
        modifier = modifier.width(200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(
            containerColor = expressiveColors.surfaceContainerHigh.copy(alpha = 0.85f),
        ),
        border = Border(
            border = BorderStroke(1.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.15f))
        ),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "SKIP",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 18.sp,
                color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = expressiveColors.playerContentPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 18.sp,
                color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
            )
            Button(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .then(if (buttonFocusRequester != null) Modifier.focusRequester(buttonFocusRequester) else Modifier)
                    .testTag(PlayerTestTags.SkipActionButton),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = expressiveColors.playerContentPrimary,
                    focusedContainerColor = expressiveColors.playerContentPrimary,
                    focusedContentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodeCard(
    seriesTitle: String? = null,
    title: String,
    thumbnailUrl: String?,
    remainingMs: Long,
    autoPlayEnabled: Boolean,
    autoFocusPlayNow: Boolean = false,
    onActionFocusChanged: (Boolean) -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFraction = ((15_000L - remainingMs) / 15_000f).coerceIn(0f, 1f)
    val playNowFocusRequester = remember { FocusRequester() }
    val expressiveColors = LocalCinefinExpressiveColors.current

    LaunchedEffect(autoFocusPlayNow) {
        if (!autoFocusPlayNow) return@LaunchedEffect
        withFrameNanos { }
        runCatching { playNowFocusRequester.requestFocus() }
    }

    Surface(
        modifier = modifier.width(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = SurfaceDefaults.colors(
            containerColor = expressiveColors.surfaceContainerHigh.copy(alpha = 0.92f),
        ),
        border = Border(
            border = BorderStroke(1.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.15f))
        ),
        tonalElevation = 8.dp
    ) {
        Column {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(SurfaceDark),
            )

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 18.sp,
                    color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = expressiveColors.playerContentPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                seriesTitle?.takeIf { it.isNotBlank() && it != title }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 16.sp,
                        color = expressiveColors.playerContentPrimary.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (autoPlayEnabled) {
                        "Starting in ${remainingMs / 1000}s..."
                    } else {
                        "Ready to play next"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 18.sp,
                    color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
                )

                if (autoPlayEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(expressiveColors.playerContentPrimary.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Button(
                    onClick = onPlayNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag(PlayerTestTags.NextEpisodeButton)
                        .focusRequester(playNowFocusRequester)
                        .onFocusChanged { onActionFocusChanged(it.hasFocus) },
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = expressiveColors.playerContentPrimary,
                        focusedContainerColor = expressiveColors.playerContentPrimary,
                        focusedContentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = if (autoPlayEnabled) "▶  Play Now" else "▶  Play Next",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
