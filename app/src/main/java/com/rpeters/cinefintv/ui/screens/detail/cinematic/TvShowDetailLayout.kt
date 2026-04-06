@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.DetailShelfPanel
import com.rpeters.cinefintv.ui.screens.detail.SeasonModel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import kotlinx.coroutines.launch

/**
 * TV Show detail screen content: CinematicHero + continuous vertical scroll.
 */
@Composable
fun TvShowDetailLayout(
    backdropUrl: String?,
    posterUrl: String?,
    logoUrl: String?,
    title: String,
    eyebrow: String,
    ratingText: String?,
    genres: List<String>,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    nextUpTitle: String?,
    onNextUpClick: (() -> Unit)?,
    seasons: List<SeasonModel>,
    onSeasonClick: (SeasonModel) -> Unit,
    castItems: List<CastModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    description: String,
    heroTagline: String?,
    creditLine: String?,
    heroBadges: List<String>,
    heroSecondaryActions: List<HeroIconAction>,
    factItems: List<DetailLabeledMetaItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val overviewFocusRequester = remember { FocusRequester() }
    val nextUpFocusRequester = remember { FocusRequester() }
    val firstSeasonFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val similarCardWidth: Dp = 196.dp
    val hasNextUp = !nextUpTitle.isNullOrBlank() && onNextUpClick != null
    val firstContentFocusRequester = when {
        hasNextUp -> nextUpFocusRequester
        seasons.isNotEmpty() -> firstSeasonFocusRequester
        else -> overviewFocusRequester
    }
    val overviewItemIndex = 1 + (if (hasNextUp) 1 else 0) + (if (seasons.isNotEmpty()) 1 else 0)
    val navigationHeroActions = remember(seasons, castItems) {
        buildList {
            if (seasons.isNotEmpty()) {
                add(
                    HeroIconAction(
                        icon = Icons.Default.VideoLibrary,
                        contentDescription = "Jump to seasons",
                        onClick = {},
                    )
                )
            }
            add(
                HeroIconAction(
                    icon = Icons.Default.Info,
                    contentDescription = "Jump to details",
                    onClick = {},
                )
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.gutter * 2),
    ) {
        item {
            Column {
                DetailAnchor(
                    focusRequester = topFocusRequester,
                    downFocusRequester = primaryActionFocusRequester,
                    onFocused = {},
                )
                FlatDetailHero(
                    backdropUrl = backdropUrl,
                    logoUrl = logoUrl,
                    title = title,
                    eyebrow = eyebrow,
                    ratingText = ratingText,
                    badges = heroBadges,
                    tagline = heroTagline,
                    summary = heroTagline ?: description.takeIf { it.isNotBlank() },
                    creditLine = creditLine,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    secondaryIconActions = (heroSecondaryActions + navigationHeroActions).mapIndexed { index, action ->
                        when {
                            index >= heroSecondaryActions.size &&
                                seasons.isNotEmpty() &&
                                index == heroSecondaryActions.size -> action.copy(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(1 + if (hasNextUp) 1 else 0)
                                        androidx.compose.runtime.withFrameNanos { }
                                        runCatching { firstSeasonFocusRequester.requestFocus() }
                                    }
                                }
                            )
                            index >= heroSecondaryActions.size -> action.copy(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(overviewItemIndex)
                                        androidx.compose.runtime.withFrameNanos { }
                                        runCatching { overviewFocusRequester.requestFocus() }
                                    }
                                }
                            )
                            else -> action
                        }
                    },
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    primaryActionDownFocusRequester = firstContentFocusRequester,
                    onDownNavigation = {
                        if (listState.isScrollInProgress) return@FlatDetailHero
                        coroutineScope.launch {
                            listState.animateScrollToItem(1)
                            androidx.compose.runtime.withFrameNanos { }
                            runCatching { firstContentFocusRequester.requestFocus() }
                        }
                    },
                )
            }
        }

        if (hasNextUp) {
            item {
                NextUpPanel(
                    title = nextUpTitle.orEmpty(),
                    onPlay = onNextUpClick,
                    focusRequester = nextUpFocusRequester,
                    upFocusRequester = primaryActionFocusRequester,
                    downFocusRequester = if (seasons.isNotEmpty()) firstSeasonFocusRequester else overviewFocusRequester,
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .padding(horizontal = spacing.gutter)
                        .testTag(DetailTestTags.TvNextUpPanel),
                )
            }
        }

        if (seasons.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvEpisodesPanel), // Reusing tag for consistency
                ) {
                    DetailStripTitle(
                        title = "Seasons",
                        modifier = Modifier.padding(
                            horizontal = spacing.gutter,
                            vertical = spacing.elementGap,
                        ),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    ) {
                        items(seasons) { season ->
                            SeasonShowcaseCard(
                                season = season,
                                modifier = if (season.id == seasons.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstSeasonFocusRequester)
                                        .focusProperties {
                                            up = if (hasNextUp) nextUpFocusRequester else primaryActionFocusRequester
                                            down = overviewFocusRequester
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                runCatching {
                                                    if (hasNextUp) nextUpFocusRequester.requestFocus()
                                                    else primaryActionFocusRequester.requestFocus()
                                                }
                                                true
                                            } else if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                            ) {
                                                runCatching { overviewFocusRequester.requestFocus() }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .testTag(DetailTestTags.FirstSeasonItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onSeasonClick(season) },
                            )
                        }
                    }
                }
            }
        }

        item {
            DetailOverviewSection(
                title = title,
                posterUrl = posterUrl,
                description = description,
                factItems = factItems,
                chips = genres,
                focusRequester = overviewFocusRequester,
                upFocusRequester = when {
                    seasons.isNotEmpty() -> firstSeasonFocusRequester
                    hasNextUp -> nextUpFocusRequester
                    else -> primaryActionFocusRequester
                },
                onNavigateUp = {
                    if (listState.isScrollInProgress) return@DetailOverviewSection
                    coroutineScope.launch {
                        when {
                            seasons.isNotEmpty() -> {
                                listState.scrollToItem(1 + if (hasNextUp) 1 else 0)
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { firstSeasonFocusRequester.requestFocus() }
                            }
                            hasNextUp -> {
                                listState.scrollToItem(1)
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { nextUpFocusRequester.requestFocus() }
                            }
                            else -> {
                                listState.scrollToItem(0)
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { primaryActionFocusRequester.requestFocus() }
                            }
                        }
                    }
                },
                onNavigateDown = if (castItems.isNotEmpty() || similarItems.isNotEmpty()) {
                    {
                        if (listState.isScrollInProgress) return@DetailOverviewSection
                        coroutineScope.launch {
                            when {
                                castItems.isNotEmpty() -> {
                                    listState.scrollToItem(overviewItemIndex + 1)
                                    androidx.compose.runtime.withFrameNanos { }
                                    runCatching { firstCastFocusRequester.requestFocus() }
                                }
                                similarItems.isNotEmpty() -> {
                                    listState.scrollToItem(overviewItemIndex + 1)
                                    androidx.compose.runtime.withFrameNanos { }
                                    runCatching { firstSimilarFocusRequester.requestFocus() }
                                }
                            }
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.padding(top = spacing.rowGap),
            )
        }

        if (castItems.isNotEmpty()) {
            item {
                DetailShelfPanel(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvCastPanel),
                    title = "People",
                    subtitle = "Cast and recurring faces",
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    ) {
                        items(castItems) { person ->
                            TvPersonCard(
                                name = person.name,
                                role = person.role,
                                imageUrl = person.imageUrl,
                                modifier = if (person.id == castItems.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstCastFocusRequester)
                                        .focusProperties {
                                            up = overviewFocusRequester
                                            down = if (similarItems.isNotEmpty()) firstSimilarFocusRequester else firstCastFocusRequester
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            when {
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { overviewFocusRequester.requestFocus() }
                                                    true
                                                }
                                                similarItems.isNotEmpty() &&
                                                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    runCatching { firstSimilarFocusRequester.requestFocus() }
                                                    true
                                                }
                                                else -> false
                                            }
                                        }
                                        .testTag(DetailTestTags.FirstCastItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onCastClick(person.id) },
                            )
                        }
                    }
                }
            }
        }

        if (similarItems.isNotEmpty()) {
            item {
                DetailShelfPanel(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvSimilarPanel),
                    title = "More Like This",
                    subtitle = "Other shows with a similar vibe",
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    ) {
                        items(similarItems, key = { it.id }) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                aspectRatio = 2f / 3f,
                                cardWidth = 220.dp,
                                modifier = if (item.id == similarItems.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstSimilarFocusRequester)
                                        .focusProperties { up = if (castItems.isNotEmpty()) firstCastFocusRequester else overviewFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                runCatching {
                                                    if (castItems.isNotEmpty()) firstCastFocusRequester.requestFocus()
                                                    else overviewFocusRequester.requestFocus()
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .testTag(DetailTestTags.FirstSimilarItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onSimilarClick(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpPanel(
    title: String,
    onPlay: () -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = expressiveColors.detailPanel.copy(alpha = 0.82f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            )
            .border(
                width = 1.dp,
                color = expressiveColors.borderSubtle.copy(alpha = 0.55f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "NEXT UP",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Resume the series from the next recommended episode.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusProperties {
                        up = upFocusRequester
                        down = downFocusRequester
                    }
                    .testTag(DetailTestTags.TvNextUpAction),
                scale = ButtonDefaults.scale(focusedScale = 1.03f),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Text(
                    text = "Play Next Episode",
                    modifier = Modifier.padding(start = spacing.elementGap),
                )
            }
        }
    }
}

@Composable
private fun SeasonShowcaseCard(
    season: SeasonModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val performanceProfile = LocalPerformanceProfile.current
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(340.dp)
            .onFocusChanged { state -> isFocused = state.isFocused || state.hasFocus },
        colors = CardDefaults.colors(
            containerColor = expressiveColors.detailPanel.copy(alpha = 0.88f),
            focusedContainerColor = expressiveColors.detailPanelFocused,
        ),
        shape = CardDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
        scale = CardDefaults.scale(focusedScale = 1.03f),
        border = CardDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = expressiveColors.borderSubtle.copy(alpha = 0.42f),
                ),
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = expressiveColors.focusRing,
                ),
            ),
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = expressiveColors.focusGlow.copy(alpha = 0.24f),
                elevation = 10.dp,
            ),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(expressiveColors.accentSurface),
            ) {
                if (season.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(season.imageUrl)
                            .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                            .size(680, 380)
                            .build(),
                        contentDescription = season.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (season.watchStatus) {
                        WatchStatus.WATCHED -> SeasonHeroPill("Watched")
                        WatchStatus.IN_PROGRESS -> SeasonHeroPill("Resume")
                        WatchStatus.NONE -> Unit
                    }
                    if (season.unwatchedCount > 0) {
                        SeasonHeroPill("${season.unwatchedCount} new")
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = season.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            season.playbackProgress != null && season.playbackProgress > 0f ->
                                "${(season.playbackProgress * 100).toInt()}% watched"
                            season.unwatchedCount > 0 -> "${season.unwatchedCount} unwatched episodes"
                            else -> "Open season"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (season.playbackProgress != null && season.playbackProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                color = expressiveColors.chromeSurface.copy(alpha = 0.55f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(season.playbackProgress.coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                            expressiveColors.titleAccent.copy(alpha = 0.88f),
                                        ),
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            season.watchStatus == WatchStatus.WATCHED -> "Completed season"
                            season.unwatchedCount > 0 -> "Continue with new episodes"
                            else -> "Browse episodes"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonHeroPill(
    label: String,
) {
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}
