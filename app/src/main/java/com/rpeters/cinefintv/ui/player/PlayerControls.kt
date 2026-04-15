package com.rpeters.cinefintv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.toBitmap
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.theme.SurfaceDark
import com.rpeters.cinefintv.utils.formatMs
import kotlinx.coroutines.delay

private val defaultBounds = Rect.Zero

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    duration: Long,
    bufferedFraction: Float,
    uiState: PlayerUiState,
    player: ExoPlayer,
    playPauseFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    isContentShelfVisible: Boolean,
    onHideShelf: () -> Unit,
    onInteract: () -> Unit,
    onSettingsClick: (SettingsSection, Rect?) -> Unit,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val chapters = uiState.chapters
    val trickplayManifest = uiState.trickplayManifest
    val trickplayBaseUrl = uiState.trickplayBaseUrl
    val seekIncrement = uiState.videoSeekIncrement

    val (subtitleButtonBounds, setSubtitleButtonBounds) = remember { mutableStateOf<Rect>(defaultBounds) }
    val (audioButtonBounds, setAudioButtonBounds) = remember { mutableStateOf<Rect>(defaultBounds) }
    val (qualityButtonBounds, setQualityButtonBounds) = remember { mutableStateOf<Rect>(defaultBounds) }
    val (speedButtonBounds, setSpeedButtonBounds) = remember { mutableStateOf<Rect>(defaultBounds) }
    val (settingsButtonBounds, setSettingsButtonBounds) = remember { mutableStateOf<Rect>(defaultBounds) }

    val backFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val speedFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    val skipBackFocusRequester = remember { FocusRequester() }
    val skipForwardFocusRequester = remember { FocusRequester() }
    val contentRowFocusRequester = remember { FocusRequester() }
    val hasContentRow = uiState.contentRow != null && isContentShelfVisible

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(PlayerTestTags.ControlsOverlay)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft,
                            Key.DirectionRight,
                            Key.DirectionUp,
                            Key.DirectionDown -> {
                                onInteract()
                                false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .background(
                    Brush.verticalGradient(
                        0.0f to expressiveColors.playerOverlayStart,
                        0.25f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to expressiveColors.playerOverlayEnd
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
                                up = FocusRequester.Cancel
                                down = seekBarFocusRequester
                            },
                        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
                        colors = IconButtonDefaults.colors(
                            containerColor = expressiveColors.playerContentPrimary.copy(alpha = 0.1f),
                            contentColor = expressiveColors.playerContentPrimary,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = expressiveColors.playerContentPrimary,
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (uiState.logoUrl != null) {
                            AsyncImage(
                                model = uiState.logoUrl,
                                contentDescription = uiState.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(56.dp),
                            )
                        } else {
                            Text(
                                text = uiState.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = expressiveColors.playerContentPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val metadataLine = buildList {
                            uiState.seasonNumber?.let { season ->
                                uiState.episodeNumber?.let { episode ->
                                    add("Season $season · Episode $episode")
                                } ?: add("Season $season")
                            } ?: uiState.episodeNumber?.let { episode ->
                                add("Episode $episode")
                            }
                            if (uiState.isHdrPlayback) add("HDR")
                            if (uiState.transcodingQuality != com.rpeters.cinefintv.data.preferences.TranscodingQuality.AUTO) {
                                add(uiState.transcodingQuality.label)
                            }
                        }.joinToString("  •  ")
                        if (metadataLine.isNotBlank()) {
                            Text(
                                text = metadataLine,
                                style = MaterialTheme.typography.titleMedium,
                                color = expressiveColors.playerContentSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                        text = formatMs(positionProvider()),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 18.sp,
                        color = expressiveColors.playerContentPrimary.copy(alpha = 0.9f),
                    )
                    SeekBarControl(
                        positionProvider = positionProvider,
                        duration = duration,
                        bufferedFraction = bufferedFraction,
                        chapters = uiState.chapters,
                        trickplayManifest = trickplayManifest,
                        trickplayBaseUrl = trickplayBaseUrl,
                        seekIncrementMs = seekIncrement.millis,
                        onSeekCommitted = { player.seekTo(it) },
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
                        color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
                    )
                }

                // Button row: left skip, center play/pause, right utility cluster.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ActionIconButton(
                        icon = Icons.Default.FastRewind,
                        label = seekIncrement.shortLabel,
                        onClick = { onInteract(); player.seekTo((player.currentPosition - seekIncrement.millis).coerceAtLeast(0L)) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .focusRequester(skipBackFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                if (hasContentRow) down = contentRowFocusRequester
                                left = skipBackFocusRequester
                                right = playPauseFocusRequester
                            }
                    )

                    PlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = {
                            onInteract()
                            if (isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .focusRequester(playPauseFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                if (hasContentRow) down = contentRowFocusRequester
                                left = skipBackFocusRequester
                                right = skipForwardFocusRequester
                            }
                    )

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionIconButton(
                            icon = Icons.Default.FastForward,
                            label = seekIncrement.shortLabel,
                            onClick = { onInteract(); player.seekTo((player.currentPosition + seekIncrement.millis).coerceIn(0L, duration)) },
                            modifier = Modifier
                                .focusRequester(skipForwardFocusRequester)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    if (hasContentRow) down = contentRowFocusRequester
                                    left = playPauseFocusRequester
                                    right = audioFocusRequester
                                }
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(expressiveColors.playerContentPrimary.copy(alpha = 0.4f))
                        )

                        ActionIconButton(
                            icon = Icons.Default.MusicNote,
                            onClick = { onInteract(); onSettingsClick(SettingsSection.AUDIO, audioButtonBounds) },
                            modifier = Modifier
                                .focusRequester(audioFocusRequester)
                                .testTag(PlayerTestTags.AudioButton)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    if (hasContentRow) down = contentRowFocusRequester
                                    left = skipForwardFocusRequester
                                    right = subtitleFocusRequester
                                }
                                .onGloballyPositioned { setAudioButtonBounds(it.boundsInRoot()) }
                        )

                        ActionIconButton(
                            icon = Icons.Default.ClosedCaption,
                            onClick = { onInteract(); onSettingsClick(SettingsSection.SUBTITLES, subtitleButtonBounds) },
                            modifier = Modifier
                                .focusRequester(subtitleFocusRequester)
                                .testTag(PlayerTestTags.SubtitleButton)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    if (hasContentRow) down = contentRowFocusRequester
                                    left = audioFocusRequester
                                    right = qualityFocusRequester
                                }
                                .onGloballyPositioned { setSubtitleButtonBounds(it.boundsInRoot()) }
                        )

                        ActionIconButton(
                            icon = Icons.Default.HighQuality,
                            onClick = { onInteract(); onSettingsClick(SettingsSection.QUALITY, qualityButtonBounds) },
                            modifier = Modifier
                                .focusRequester(qualityFocusRequester)
                                .testTag(PlayerTestTags.QualityButton)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    if (hasContentRow) down = contentRowFocusRequester
                                    left = subtitleFocusRequester
                                    right = speedFocusRequester
                                }
                                .onGloballyPositioned { setQualityButtonBounds(it.boundsInRoot()) }
                        )

                        ActionIconButton(
                            icon = Icons.Default.PlayArrow,
                            label = if (uiState.playbackSpeed == 1.0f) "1x" else "${uiState.playbackSpeed}x",
                            onClick = { onInteract(); onSettingsClick(SettingsSection.SPEED, speedButtonBounds) },
                            modifier = Modifier
                                .focusRequester(speedFocusRequester)
                                .testTag(PlayerTestTags.SpeedButton)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    if (hasContentRow) down = contentRowFocusRequester
                                    left = qualityFocusRequester
                                    right = settingsFocusRequester
                                }
                                .onGloballyPositioned { setSpeedButtonBounds(it.boundsInRoot()) }
                        )

                        ActionIconButton(
                            icon = Icons.Default.Settings,
                            onClick = { onInteract(); onSettingsClick(SettingsSection.ALL, settingsButtonBounds) },
                            modifier = Modifier
                                .focusRequester(settingsFocusRequester)
                                .testTag(PlayerTestTags.SettingsButton)
                                .focusProperties {
                                    up = seekBarFocusRequester
                                    if (hasContentRow) down = contentRowFocusRequester
                                    left = speedFocusRequester
                                    right = settingsFocusRequester
                                }
                                .onGloballyPositioned { setSettingsButtonBounds(it.boundsInRoot()) }
                        )
                    }
                }

                val contentRow = uiState.contentRow
                AnimatedVisibility(
                    visible = contentRow != null && isContentShelfVisible,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    if (contentRow != null) {
                        PlayerContentShelf(
                            contentRow = contentRow,
                            positionProvider = positionProvider,
                            trickplayManifest = uiState.trickplayManifest,
                            trickplayBaseUrl = uiState.trickplayBaseUrl,
                            firstItemFocusRequester = contentRowFocusRequester,
                            upRequester = playPauseFocusRequester,
                            onInteract = onInteract,
                            onSeekToChapter = { player.seekTo(it) },
                            onOpenItem = onOpenItem,
                            onHideShelf = onHideShelf
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerContentShelf(
    contentRow: PlayerContentRow,
    positionProvider: () -> Long,
    trickplayManifest: TrickplayManifest?,
    trickplayBaseUrl: String?,
    firstItemFocusRequester: FocusRequester,
    upRequester: FocusRequester,
    onInteract: () -> Unit,
    onSeekToChapter: (Long) -> Unit,
    onOpenItem: (String) -> Unit,
    onHideShelf: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val label = when (contentRow) {
        is PlayerContentRow.Chapters -> "Chapters"
        is PlayerContentRow.Episodes -> "Season Episodes"
        is PlayerContentRow.Recommendations -> "More Like This"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 18.sp,
            color = expressiveColors.playerContentPrimary.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            when (contentRow) {
                is PlayerContentRow.Chapters -> {
                    itemsIndexed(contentRow.chapters) { index, chapter ->
                        val currentPosition = positionProvider()
                        val activeIndex = contentRow.chapters.indexOfLast { it.positionMs <= currentPosition }
                            .coerceAtLeast(0)
                        PlayerChapterCard(
                            chapter = chapter,
                            isActive = index == activeIndex,
                            trickplayManifest = trickplayManifest,
                            trickplayBaseUrl = trickplayBaseUrl,
                            onClick = { onInteract(); onSeekToChapter(chapter.positionMs) },
                            modifier = Modifier
                                .focusProperties { up = upRequester }
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                        onHideShelf()
                                        false
                                    } else false
                                }
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                        )
                    }
                }
                is PlayerContentRow.Episodes -> {
                    itemsIndexed(contentRow.items, key = { _, item -> item.id }) { index, item ->
                        val isCurrent = item.id == contentRow.currentItemId
                        PlayerContentItemCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            isCurrent = isCurrent,
                            onClick = { if (!isCurrent) { onInteract(); onOpenItem(item.id) } },
                            modifier = Modifier
                                .focusProperties { up = upRequester }
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                        onHideShelf()
                                        false
                                    } else false
                                }
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                        )
                    }
                }
                is PlayerContentRow.Recommendations -> {
                    itemsIndexed(contentRow.items, key = { _, item -> item.id }) { index, item ->
                        PlayerContentItemCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            isCurrent = false,
                            onClick = { onInteract(); onOpenItem(item.id) },
                            modifier = Modifier
                                .focusProperties { up = upRequester }
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                        onHideShelf()
                                        false
                                    } else false
                                }
                                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerChapterCard(
    chapter: ChapterMarker,
    isActive: Boolean,
    trickplayManifest: TrickplayManifest?,
    trickplayBaseUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Card(
        onClick = onClick,
        modifier = modifier.width(160.dp),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else SurfaceDark,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        border = if (isActive) CardDefaults.border(
            border = Border(border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)),
            focusedBorder = Border(border = BorderStroke(2.dp, expressiveColors.playerContentPrimary)),
        ) else CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.8f))),
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(expressiveColors.playerSurface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                if (trickplayManifest != null && trickplayBaseUrl != null) {
                    TrickplayPreview(
                        seekPosition = chapter.positionMs,
                        manifest = trickplayManifest,
                        baseUrl = trickplayBaseUrl,
                    )
                } else {
                    Text(
                        text = formatMs(chapter.positionMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        color = expressiveColors.playerContentPrimary.copy(alpha = 0.7f),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = chapter.name ?: "Chapter",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 18.sp,
                    color = expressiveColors.playerContentPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = formatMs(chapter.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 16.sp,
                    color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerContentItemCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    Card(
        onClick = onClick,
        modifier = modifier.width(160.dp),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else SurfaceDark,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        border = if (isCurrent) CardDefaults.border(
            border = Border(border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)),
            focusedBorder = Border(border = BorderStroke(2.dp, expressiveColors.playerContentPrimary)),
        ) else CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.8f))),
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f),
    ) {
        Column {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(SurfaceDark),
            )
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 18.sp,
                    color = expressiveColors.playerContentPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 16.sp,
                        color = expressiveColors.playerContentPrimary.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }
        }
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
        modifier = modifier.width(180.dp),
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = SurfaceDark,
        ),
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
        modifier = modifier.width(180.dp),
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = SurfaceDark,
        ),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    IconButton(
        onClick = onClick,
        modifier = modifier,
        scale = IconButtonDefaults.scale(focusedScale = 1.2f),
        colors = IconButtonDefaults.colors(
            containerColor = expressiveColors.playerContentPrimary,
            contentColor = expressiveColors.playerSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = expressiveColors.playerContentPrimary
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
    label: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    IconButton(
        onClick = onClick,
        modifier = modifier,
        scale = IconButtonDefaults.scale(focusedScale = 1.15f),
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = expressiveColors.playerContentPrimary.copy(alpha = 0.8f),
            focusedContainerColor = expressiveColors.playerContentPrimary.copy(alpha = 0.2f),
            focusedContentColor = expressiveColors.playerContentPrimary
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = expressiveColors.playerContentPrimary.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TrickplayPreview(
    seekPosition: Long,
    manifest: TrickplayManifest,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val interval = manifest.intervalMs.toLong()
    if (interval <= 0) return

    val totalFramesPerTile = manifest.tiles.firstOrNull()?.let { it.rowCount * it.columnCount } ?: 1
    val tileDuration = interval * totalFramesPerTile
    val tileIndex = (seekPosition / tileDuration).toInt().coerceIn(0, manifest.tiles.size - 1)
    val tile = manifest.tiles[tileIndex]
    val tileUrl = baseUrl + tile.image
    
    val frameIndexInTile = ((seekPosition % tileDuration) / interval).toInt().coerceIn(0, totalFramesPerTile - 1)
    val row = frameIndexInTile / tile.columnCount
    val col = frameIndexInTile % tile.columnCount

    val frameWidth = manifest.width
    val frameHeight = manifest.height
    var cachedBitmap by remember(tileUrl) {
        mutableStateOf(TrickplayTileBitmapCache.get(tileUrl))
    }

    Box(
        modifier = modifier
            .size(width = 160.dp, height = 90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(expressiveColors.playerSurface)
            .border(
                1.dp,
                expressiveColors.playerContentPrimary.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
    ) {
        val bitmap = cachedBitmap
        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val srcOffset = IntOffset(col * frameWidth, row * frameHeight)
                val srcSize = IntSize(frameWidth, frameHeight)

                drawImage(
                    image = bitmap,
                    srcOffset = srcOffset,
                    srcSize = srcSize,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        } else {
            AsyncImage(
                model = tileUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f),
                onSuccess = { state ->
                    val loadedBitmap = state.result.image.toBitmap().asImageBitmap()
                    TrickplayTileBitmapCache.put(tileUrl, loadedBitmap)
                    cachedBitmap = loadedBitmap
                },
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekBarControl(
    positionProvider: () -> Long,
    duration: Long,
    bufferedFraction: Float,
    chapters: List<ChapterMarker>,
    trickplayManifest: TrickplayManifest?,
    trickplayBaseUrl: String?,
    seekIncrementMs: Long,
    onSeekCommitted: (Long) -> Unit,
    onInteract: () -> Unit,
    focusRequester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(positionProvider()) }
    var seekInteractionVersion by remember { mutableIntStateOf(0) }
    val expressiveColors = LocalCinefinExpressiveColors.current

    fun finishSeekInteraction() {
        isSeeking = false
    }

    // Periodically update seekPosition from provider when NOT seeking to keep bar in sync
    LaunchedEffect(isSeeking) {
        if (!isSeeking) {
            while (true) {
                seekPosition = positionProvider()
                delay(200L) // Slower update for the bar itself is fine
            }
        }
    }

    LaunchedEffect(seekInteractionVersion) {
        if (seekInteractionVersion == 0) return@LaunchedEffect
        val versionAtLaunch = seekInteractionVersion
        delay(450L)
        if (seekInteractionVersion == versionAtLaunch) {
            finishSeekInteraction()
        }
    }

    val barHeight by animateDpAsState(if (isFocused) 8.dp else 3.dp, label = "BarHeight")
    val thumbScale by animateFloatAsState(if (isFocused) 1f else 0f, label = "ThumbScale")
    val seekOverlayHeight by animateDpAsState(
        targetValue = if (isFocused) 104.dp else 32.dp,
        label = "SeekOverlayHeight",
    )

    // Visual progress animation for smooth bar/thumb movement
    val progressFractionRaw = if (duration > 0L) (seekPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgressFraction by animateFloatAsState(
        targetValue = progressFractionRaw,
        animationSpec = tween(
            durationMillis = if (isSeeking) 32 else 250,
            easing = LinearEasing
        ),
        label = "SmoothProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(seekOverlayHeight)
            .testTag(PlayerTestTags.SeekBar)
            .focusRequester(focusRequester)
            .focusProperties {
                this.up = up
                this.down = down
            }
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (!isFocused) {
                    finishSeekInteraction()
                    seekPosition = positionProvider()
                }
            }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                when {
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                        if (duration <= 0L) return@onPreviewKeyEvent true
                        val basePosition = if (isSeeking) seekPosition else positionProvider()
                        val nextPosition = (basePosition - seekIncrementMs).coerceAtLeast(0L)
                        seekPosition = nextPosition
                        isSeeking = true
                        seekInteractionVersion += 1
                        onSeekCommitted(nextPosition)
                        onInteract()
                        true
                    }
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                        if (duration <= 0L) return@onPreviewKeyEvent true
                        val basePosition = if (isSeeking) seekPosition else positionProvider()
                        val nextPosition = (basePosition + seekIncrementMs).coerceIn(0L, duration)
                        seekPosition = nextPosition
                        isSeeking = true
                        seekInteractionVersion += 1
                        onSeekCommitted(nextPosition)
                        onInteract()
                        true
                    }
                    keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.Spacebar) -> {
                        finishSeekInteraction()
                        true
                    }
                    keyEvent.type == KeyEventType.KeyUp &&
                        (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) -> true
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(expressiveColors.playerContentPrimary.copy(alpha = 0.2f))
        ) {
            val bufferedClamped = bufferedFraction.coerceIn(0f, 1f)

            // Buffered section
            if (bufferedClamped > animatedProgressFraction) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bufferedClamped)
                        .background(expressiveColors.playerContentPrimary.copy(alpha = 0.35f))
                )
            }

            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgressFraction)
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
                        .background(expressiveColors.playerContentPrimary.copy(alpha = 0.6f))
                )
            }

            // Thumb
            if (thumbScale > 0f) {
                val thumbDp = 20.dp * thumbScale
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * animatedProgressFraction - thumbDp / 2)
                        .size(thumbDp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(width = 3.dp, color = expressiveColors.playerContentPrimary, shape = CircleShape)
                )
            }
        }

        // Timestamp bubble + Trickplay Preview — floats above the thumb when focused
        if (isFocused) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                val bubbleWidth = 110.dp
                val previewWidth = 160.dp
                val thumbX = maxWidth * animatedProgressFraction
                
                val bubbleClampedX = thumbX.coerceIn(0.dp, maxWidth - bubbleWidth)
                val previewClampedX = thumbX.coerceIn(0.dp, maxWidth - previewWidth)

                if (trickplayManifest != null && trickplayBaseUrl != null) {
                    TrickplayPreview(
                        seekPosition = seekPosition,
                        manifest = trickplayManifest,
                        baseUrl = trickplayBaseUrl,
                        modifier = Modifier.offset(x = previewClampedX, y = (-136).dp)
                    )
                }

                val percentage = if (duration > 0L) (seekPosition.toFloat() / duration.toFloat()) * 100f else 0f
                val percentageStr = String.format(java.util.Locale.US, "%.2f%%", percentage)
                val timeStr = formatMs(seekPosition)
                val activeChapterName = chapters
                    .lastOrNull { it.positionMs <= seekPosition }
                    ?.name
                    ?.takeIf { it.isNotBlank() }

                Surface(
                    modifier = Modifier
                        .testTag(PlayerTestTags.SeekBubble)
                        .offset(x = bubbleClampedX, y = (-44).dp)
                        .width(if (activeChapterName != null) 180.dp else bubbleWidth),
                    shape = RoundedCornerShape(6.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = expressiveColors.playerSurface.copy(alpha = 0.92f)
                    ),
                    border = androidx.tv.material3.Border(
                        border = BorderStroke(1.dp, expressiveColors.playerContentPrimary.copy(alpha = 0.2f))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "$timeStr • $percentageStr",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = expressiveColors.playerContentPrimary,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        if (activeChapterName != null) {
                            Text(
                                text = activeChapterName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 12.sp,
                                color = expressiveColors.playerContentPrimary.copy(alpha = 0.72f),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
