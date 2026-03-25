@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.GraphicEq
import androidx.tv.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.flow.first

data class DetailMetaItem(
    val icon: ImageVector,
    val value: String,
)

data class DetailLabeledMetaItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

enum class MetaFactStyle { Card, Inline }

suspend fun focusDetailScreenAtTop(
    listState: LazyListState,
    initialFocusRequester: FocusRequester,
) {
    listState.scrollToItem(0)
    snapshotFlow {
        val layoutInfo = listState.layoutInfo
        layoutInfo.viewportEndOffset > layoutInfo.viewportStartOffset &&
            layoutInfo.visibleItemsInfo.any { it.index == 0 }
    }.first { it }
    withFrameNanos { }
    runCatching { initialFocusRequester.requestFocus() }
    // requestFocus may trigger BringIntoView which scrolls the list off the top of the hero.
    // Wait one frame for that scroll to settle, then snap back to the anchor.
    withFrameNanos { }
    listState.scrollToItem(0)
}

@Composable
fun MetaFactItem(
    icon: ImageVector,
    label: String,
    value: String,
    style: MetaFactStyle = MetaFactStyle.Card,
    modifier: Modifier = Modifier,
) {
    when (style) {
        MetaFactStyle.Card -> {
            Column(
                modifier = modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MetaFactStyle.Inline -> {
            Row(
                modifier = modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
fun DetailPosterArt(
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Surface(
        modifier = modifier.graphicsLayer {
            shadowElevation = 28.dp.toPx()
        },
        shape = RoundedCornerShape(spacing.cornerContainer),
        colors = SurfaceDefaults.colors(containerColor = expressiveColors.accentSurface.copy(alpha = 0.92f)),
        tonalElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = expressiveColors.borderSubtle.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(spacing.cornerContainer),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .size(420, 630)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DetailChipRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val filtered = labels.filter { it.isNotBlank() }
    if (filtered.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        filtered.forEachIndexed { index, label ->
            CinefinChip(
                label = label,
                strong = index == 0,
            )
        }
    }
}

@Composable
fun DetailProgressLabel(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    if (pct <= 0 || pct >= 100) return

    Text(
        text = "$pct% watched",
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun DetailMetaLine(
    items: List<DetailMetaItem>,
    modifier: Modifier = Modifier,
) {
    val filtered = items.mapNotNull { item ->
        item.value.trim().takeIf { it.isNotBlank() }?.let { item.copy(value = it) }
    }
    if (filtered.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        filtered.forEach { item ->
            DetailMetaItemView(item = item)
        }
    }
}

@Composable
fun DetailMetaLabelLine(
    items: List<DetailLabeledMetaItem>,
    modifier: Modifier = Modifier,
) {
    val filtered = items.mapNotNull { item ->
        item.value.trim().takeIf { it.isNotBlank() }?.let { item.copy(value = it) }
    }
    if (filtered.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        filtered.forEach { item ->
            MetaFactItem(
                icon = item.icon,
                label = item.label,
                value = item.value,
                style = MetaFactStyle.Inline,
            )
        }
    }
}

@Composable
fun DetailFactsColumn(
    items: List<DetailLabeledMetaItem>,
    modifier: Modifier = Modifier,
) {
    val filtered = items.mapNotNull { item ->
        item.value.trim().takeIf { it.isNotBlank() }?.let { item.copy(value = it) }
    }
    if (filtered.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filtered.forEach { item ->
            MetaFactItem(
                icon = item.icon,
                label = item.label,
                value = item.value,
                style = MetaFactStyle.Card,
            )
        }
    }
}

@Composable
private fun DetailMetaItemView(
    item: DetailMetaItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = item.value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun DetailActionRow(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    primaryFocusRequester: FocusRequester? = null,
    primaryDownFocusRequester: FocusRequester? = null,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val secondaryFocusRequester = remember { FocusRequester() }
    val hasSecondaryAction = !secondaryLabel.isNullOrBlank() && onSecondaryClick != null

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .then(
                    if (primaryFocusRequester != null) {
                        Modifier.focusRequester(primaryFocusRequester)
                    } else {
                        Modifier
                    }
                )
                .focusProperties {
                    if (primaryDownFocusRequester != null) {
                        down = primaryDownFocusRequester
                    }
                    if (hasSecondaryAction) {
                        right = secondaryFocusRequester
                    }
                }
                .defaultMinSize(minWidth = 200.dp, minHeight = 50.dp),
            scale = ButtonDefaults.scale(focusedScale = 1.03f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                focusedContainerColor = Color.White,
                focusedContentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(primaryLabel)
        }
        if (hasSecondaryAction) {
            OutlinedButton(
                onClick = onSecondaryClick,
                modifier = Modifier
                    .focusRequester(secondaryFocusRequester)
                    .defaultMinSize(minWidth = 200.dp, minHeight = 50.dp)
                    .focusProperties {
                        left = primaryFocusRequester ?: FocusRequester.Default
                        if (primaryDownFocusRequester != null) {
                            down = primaryDownFocusRequester
                        }
                    },
                scale = ButtonDefaults.scale(focusedScale = 1.03f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = expressiveColors.focusGlow.copy(alpha = 0.24f),
                    focusedContentColor = Color.White,
                ),
                border = ButtonDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = expressiveColors.borderSubtle.copy(alpha = 0.65f),
                        ),
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = expressiveColors.focusRing,
                        ),
                    ),
                ),
            ) {
                Text(secondaryLabel)
            }
        }
    }
}

/**
 * Section with a heading label and arbitrary [content] below it.
 * Used for Cast, Similar, Seasons, Chapters rows.
 */
@Composable
fun DetailContentSection(
    title: String,
    eyebrow: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    var isHeaderFocused by remember(title) { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(top = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 56.dp)
                .onFocusChanged { state ->
                    isHeaderFocused = state.hasFocus || state.isFocused
                }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    expressiveColors.chromeSurface.copy(alpha = 0.78f),
                                    expressiveColors.chromeSurface.copy(alpha = 0.36f),
                                    Color.Transparent,
                                )
                            ),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = expressiveColors.borderSubtle.copy(alpha = if (isHeaderFocused) 0.7f else 0.32f),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        eyebrow?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isHeaderFocused) Color.White else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            icon?.let {
                                Icon(
                                    imageVector = it,
                                    contentDescription = null,
                                    tint = if (isHeaderFocused) expressiveColors.focusRing else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .height(4.dp)
                        .width((this@BoxWithConstraints.maxWidth * 0.22f).coerceAtLeast(92.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    if (isHeaderFocused) expressiveColors.focusRing else expressiveColors.titleAccent,
                                    expressiveColors.titleAccent.copy(alpha = 0.4f),
                                    Color.Transparent,
                                )
                            ),
                            shape = RoundedCornerShape(999.dp),
                        )
                )
            }
        }
        content()
    }
}

/**
 * Shared error state for all detail screens.
 */
@Composable
fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** Centered spinner used during loading. */
@Composable
fun DetailLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * An invisible focus anchor that ensures the screen content is scrolled to the top
 * before transferring focus to the primary action.
 */
@Composable
fun DetailAnchor(
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
) {
    Spacer(
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusProperties {
                canFocus = true
                if (downFocusRequester != null) {
                    down = downFocusRequester
                }
            }
            .background(Color.Transparent)
    )
}

/**
 * Full-width horizontal episode row for season episode lists.
 * Left: 16:9 thumbnail with watch status overlay and progress bar.
 * Right: episode code + duration, title, overview.
 */
@Composable
fun EpisodeListRow(
    episode: com.rpeters.cinefintv.ui.screens.detail.EpisodeModel,
    modifier: Modifier = Modifier,
    isNext: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 52.dp, vertical = 8.dp)
            .onFocusChanged {
                val focused = it.isFocused || it.hasFocus
                if (focused != isFocused) {
                    isFocused = focused
                    if (focused) onFocus()
                }
            },
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.02f),
        border = androidx.tv.material3.CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            ),
        ),
        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(spacing.cornerCard)),
        colors = androidx.tv.material3.CardDefaults.colors(
            containerColor = if (isFocused) expressiveColors.accentSurface else expressiveColors.chromeSurface.copy(alpha = 0.62f),
            focusedContainerColor = expressiveColors.accentSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isFocused) 0.06f else 0.02f),
                            Color.Transparent,
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(184.dp)
                    .fillMaxHeight()
            ) {
                if (episode.imageUrl != null) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(episode.imageUrl)
                            .crossfade(true)
                            .size(368, 208)
                            .build(),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(expressiveColors.accentSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = episode.number?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                
                // NEXT badge
                if (isNext) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        CinefinChip(
                            label = "NEXT",
                            strong = true
                        )
                    }
                }

                // Watch status overlay
                val watchStatus = when {
                    episode.isWatched -> com.rpeters.cinefintv.ui.components.WatchStatus.WATCHED
                    (episode.playbackProgress ?: 0f) > 0f -> com.rpeters.cinefintv.ui.components.WatchStatus.IN_PROGRESS
                    else -> com.rpeters.cinefintv.ui.components.WatchStatus.NONE
                }
                if (watchStatus == com.rpeters.cinefintv.ui.components.WatchStatus.WATCHED) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(expressiveColors.watchedGreen.copy(alpha = 0.95f), RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                // Progress bar
                val progress = episode.playbackProgress ?: 0f
                if (progress > 0f && !episode.isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 18.dp, end = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                // Episode code + duration row
                val metaLine = listOfNotNull(episode.episodeCode, episode.duration).joinToString("  •  ")
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                val qualityLine = listOfNotNull(episode.videoQuality, episode.audioLabel)
                if (qualityLine.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        episode.videoQuality?.let {
                            EpisodeMetaBadge(
                                icon = Icons.Default.HighQuality,
                                label = it,
                            )
                        }
                        episode.audioLabel?.let {
                            EpisodeMetaBadge(
                                icon = Icons.Default.GraphicEq,
                                label = it,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                episode.overview?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeMetaBadge(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
