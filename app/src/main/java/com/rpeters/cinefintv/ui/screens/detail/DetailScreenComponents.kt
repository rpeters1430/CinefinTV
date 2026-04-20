@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.GraphicEq
import androidx.tv.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
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
import com.rpeters.cinefintv.ui.components.shouldOpenCardMenu
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
    anchorFocusRequester: FocusRequester? = null,
) {
    listState.scrollToItem(0)

    snapshotFlow {
        val layoutInfo = listState.layoutInfo
        layoutInfo.viewportEndOffset > layoutInfo.viewportStartOffset &&
            layoutInfo.visibleItemsInfo.any { it.index == 0 }
    }.first { it }

    runCatching { initialFocusRequester.requestFocus() }

    withFrameNanos { }
    if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
        listState.scrollToItem(0)
        withFrameNanos { }
        listState.scrollToItem(0)
    }
}

@Composable
fun MetaFactItem(
    icon: ImageVector,
    label: String,
    value: String,
    style: MetaFactStyle = MetaFactStyle.Card,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current

    when (style) {
        MetaFactStyle.Card -> {
            Column(
                modifier = modifier
                    .border(
                        width = 1.dp,
                        color = expressiveColors.borderSubtle.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(spacing.cornerCard),
                    )
                    .background(
                        color = expressiveColors.detailPanelMuted,
                        shape = RoundedCornerShape(spacing.cornerCard),
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
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
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
                        color = expressiveColors.detailBadge,
                        shape = RoundedCornerShape(spacing.cornerCard),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
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
            shadowElevation = 20.dp.toPx()
        },
        shape = RoundedCornerShape(spacing.cornerContainer),
        colors = SurfaceDefaults.colors(containerColor = expressiveColors.detailPanel),
        tonalElevation = 8.dp,
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
                        .size(360, 540)
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
fun DetailShelfPanel(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter)
            .background(
                color = expressiveColors.detailPanelMuted.copy(alpha = 0.9f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .border(
                width = 1.dp,
                color = expressiveColors.borderSubtle.copy(alpha = 0.34f),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
        content = {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
        },
        )
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            shape = RoundedCornerShape(LocalCinefinSpacing.current.cornerPill),
        )
        .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        Icon(
        imageVector = item.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
        )
        Text(
        text = item.value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        )
        }
        }

        @Composable
        fun DetailActionRow(
        primaryLabel: String,
        onPrimaryClick: () -> Unit,
        modifier: Modifier = Modifier,
        secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
        primaryFocusRequester: FocusRequester? = null,
        primaryDownFocusRequester: FocusRequester? = null,
        primaryButtonModifier: Modifier = Modifier,
        onDownNavigation: (() -> Unit)? = null,
        ) {
        val expressiveColors = LocalCinefinExpressiveColors.current
        val primaryTextColor = MaterialTheme.colorScheme.onPrimary
        val secondaryFocusRequesters = remember(secondaryActions.size) {
        List(secondaryActions.size) { FocusRequester() }
        }
        val hasSecondaryAction = secondaryActions.isNotEmpty()

        FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                if (primaryDownFocusRequester != null && onDownNavigation == null) {
                    down = primaryDownFocusRequester
                }
                if (hasSecondaryAction) {
                    right = secondaryFocusRequesters.first()
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                if (
                    onDownNavigation != null &&
                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onDownNavigation()
                    true
                } else {
                    false
                }
            }
            .then(primaryButtonModifier)
            .requiredWidthIn(min = 160.dp)
            .defaultMinSize(minHeight = 44.dp),
        scale = ButtonDefaults.scale(focusedScale = 1.03f),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = primaryTextColor,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = primaryTextColor,
        ),
        glow = ButtonDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(
                elevationColor = expressiveColors.focusGlow.copy(alpha = 0.48f),
                elevation = 10.dp,
            ),
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = expressiveColors.focusRing,
                ),
            ),
        ),
        ) {
        Text(
            text = primaryLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        }
        secondaryActions.forEachIndexed { index, (label, onClick) ->
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .focusRequester(secondaryFocusRequesters[index])
                .requiredWidthIn(min = 140.dp)
                .defaultMinSize(minHeight = 44.dp)
                .focusProperties {
                    left = when (index) {
                        0 -> primaryFocusRequester ?: FocusRequester.Default
                        else -> secondaryFocusRequesters[index - 1]
                    }
                    if (index < secondaryFocusRequesters.lastIndex) {
                        right = secondaryFocusRequesters[index + 1]
                    }
                    if (primaryDownFocusRequester != null && onDownNavigation == null) {
                        down = primaryDownFocusRequester
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    if (
                        onDownNavigation != null &&
                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        onDownNavigation()
                        true
                    } else {
                        false
                    }
                },
            scale = ButtonDefaults.scale(focusedScale = 1.03f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.colors(
                containerColor = expressiveColors.detailBadge,
                contentColor = MaterialTheme.colorScheme.onBackground,
                focusedContainerColor = expressiveColors.detailPanelFocused,
                focusedContentColor = MaterialTheme.colorScheme.onBackground,
            ),
            glow = ButtonDefaults.glow(
                focusedGlow = androidx.tv.material3.Glow(
                    elevationColor = expressiveColors.focusGlow.copy(alpha = 0.34f),
                    elevation = 8.dp,
                ),
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
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        var isSectionFocused by remember(title) { mutableStateOf(false) }

        Column(
        modifier = modifier
        .padding(top = 24.dp)
        .onFocusChanged { state ->
            isSectionFocused = state.hasFocus || state.isFocused
        },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        BoxWithConstraints(
        modifier = Modifier.padding(horizontal = 48.dp)
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                expressiveColors.detailPanel,
                                expressiveColors.detailPanelMuted,
                                Color.Transparent,
                            )
                        ),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = expressiveColors.borderSubtle.copy(alpha = if (isSectionFocused) 0.7f else 0.32f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    eyebrow?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSectionFocused) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        icon?.let {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                tint = if (isSectionFocused) expressiveColors.focusRing else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(3.dp)
                    .width((this@BoxWithConstraints.maxWidth * 0.18f).coerceAtLeast(80.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                if (isSectionFocused) expressiveColors.focusRing else expressiveColors.titleAccent,
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
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
        }
        }

        /** Centered spinner used during loading. */
        @Composable
        fun DetailLoadingState() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
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
        .fillMaxWidth()
        .height(1.dp)
        .focusRequester(focusRequester)
        .blockBringIntoView()
        .focusable()
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
 */
@Composable
fun EpisodeListRow(
    episode: com.rpeters.cinefintv.ui.screens.detail.EpisodeModel,
    modifier: Modifier = Modifier,
    isNext: Boolean = false,
    onFocus: () -> Unit = {},
    onMenuAction: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    var isFocused by remember { mutableStateOf(false) }
    var menuHandledForCurrentPress by remember { mutableStateOf(false) }
    val progress = (episode.playbackProgress ?: 0f).coerceIn(0f, 1f)
    val isInProgress = progress > 0f && !episode.isWatched

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .testTag(DetailTestTags.EpisodeItem)
            .onPreviewKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                when {
                    onMenuAction == null -> false
                    nativeEvent.action == android.view.KeyEvent.ACTION_UP -> {
                        menuHandledForCurrentPress = false
                        false
                    }
                    shouldOpenCardMenu(nativeEvent) && !menuHandledForCurrentPress -> {
                        menuHandledForCurrentPress = true
                        onMenuAction()
                        true
                    }
                    shouldOpenCardMenu(nativeEvent) -> true
                    else -> false
                }
            }
            .onFocusChanged {
                val focused = it.isFocused || it.hasFocus
                if (focused != isFocused) {
                    isFocused = focused
                    if (focused) onFocus()
                }
            },
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.02f),
        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(8.dp)),
        border = androidx.tv.material3.CardDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isInProgress) Color(0x33E50914) else Color.Transparent,
                ),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, expressiveColors.focusRing),
            ),
        ),
        colors = androidx.tv.material3.CardDefaults.colors(
            containerColor = when {
                episode.isWatched -> expressiveColors.detailPanelMuted.copy(alpha = 0.5f)
                isInProgress -> Color(0x10E50914)
                else -> expressiveColors.detailPanelMuted.copy(alpha = 0.88f)
            },
            focusedContainerColor = when {
                episode.isWatched -> expressiveColors.detailPanelMuted.copy(alpha = 0.6f)
                isInProgress -> Color(0x18E50914)
                else -> expressiveColors.detailPanelFocused
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(expressiveColors.accentSurface),
            ) {
                if (episode.imageUrl != null) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(episode.imageUrl)
                            .crossfade(true)
                            .size(320, 180)
                            .build(),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                episode.duration?.let { duration ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp)
                            .background(Color(0xBF000000), RoundedCornerShape(999.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = duration,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = Color.White,
                        )
                    }
                }

                if (episode.isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(16.dp)
                            .background(Color(0xFF2E7D32), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }

                if (isInProgress) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color(0xFFE50914), RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = episode.episodeCode ?: episode.number?.let { "E$it" } ?: "Episode",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = Color(0xFF888888),
                    maxLines = 1,
                )
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = if (episode.isWatched) Color(0xFFBBBBBB) else Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(episode.duration, episode.audioLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                    color = Color(0xFF666666),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                episode.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = Color(0xFF999999),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            if (isNext || isInProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .background(Color(0x18E50914), RoundedCornerShape(999.dp))
                        .border(1.dp, Color(0x66E50914), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "RESUME",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFFE50914),
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
                color = LocalCinefinExpressiveColors.current.detailBadge,
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
