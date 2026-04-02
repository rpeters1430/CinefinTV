@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.SeasonModel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
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
    factItems: List<DetailLabeledMetaItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val overviewFocusRequester = remember { FocusRequester() }
    val firstSeasonFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val similarCardWidth: Dp = 196.dp
    val firstContentFocusRequester = when {
        seasons.isNotEmpty() -> firstSeasonFocusRequester
        castItems.isNotEmpty() -> firstCastFocusRequester
        similarItems.isNotEmpty() -> firstSimilarFocusRequester
        else -> null
    }
    val overviewItemIndex = 1 + (if (seasons.isNotEmpty()) 1 else 0) + (if (castItems.isNotEmpty()) 1 else 0)
    val heroSecondaryActions = remember(seasons, castItems) {
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
                    description = description,
                    creditLine = creditLine,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    secondaryIconActions = heroSecondaryActions.mapIndexed { index, action ->
                        when {
                            seasons.isNotEmpty() && index == 0 -> action.copy(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(1)
                                        androidx.compose.runtime.withFrameNanos { }
                                        runCatching { firstSeasonFocusRequester.requestFocus() }
                                    }
                                }
                            )
                            else -> action.copy(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(overviewItemIndex)
                                        androidx.compose.runtime.withFrameNanos { }
                                        runCatching { overviewFocusRequester.requestFocus() }
                                    }
                                }
                            )
                        }
                    },
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    primaryActionDownFocusRequester = firstContentFocusRequester ?: overviewFocusRequester,
                    onDownNavigation = {
                        if (listState.isScrollInProgress) return@FlatDetailHero
                        coroutineScope.launch {
                            listState.animateScrollToItem(1)
                            androidx.compose.runtime.withFrameNanos { }
                            runCatching {
                                (firstContentFocusRequester ?: overviewFocusRequester).requestFocus()
                            }
                        }
                    },
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
                            TvMediaCard(
                                title = season.title,
                                imageUrl = season.imageUrl,
                                watchStatus = season.watchStatus,
                                playbackProgress = season.playbackProgress,
                                unwatchedCount = season.unwatchedCount.takeIf { it > 0 },
                                aspectRatio = 2f / 3f,
                                modifier = if (season.id == seasons.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstSeasonFocusRequester)
                                        .focusProperties {
                                            up = primaryActionFocusRequester
                                            down = if (castItems.isNotEmpty()) {
                                                firstCastFocusRequester
                                            } else {
                                                overviewFocusRequester
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                castItems.isNotEmpty() &&
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                            ) {
                                                runCatching { firstCastFocusRequester.requestFocus() }
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

        if (castItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvCastPanel),
                ) {
                    DetailStripTitle(
                        title = "People",
                        modifier = Modifier.padding(
                            horizontal = spacing.gutter,
                            vertical = spacing.elementGap,
                        ),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.gutter),
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
                                            up = if (seasons.isNotEmpty()) {
                                                firstSeasonFocusRequester
                                            } else {
                                                primaryActionFocusRequester
                                            }
                                            down = overviewFocusRequester
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            when {
                                                seasons.isNotEmpty() &&
                                                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { firstSeasonFocusRequester.requestFocus() }
                                                    true
                                                }
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    runCatching { overviewFocusRequester.requestFocus() }
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

        item {
            DetailOverviewSection(
                title = title,
                posterUrl = posterUrl,
                description = description,
                factItems = factItems,
                chips = genres,
                focusRequester = overviewFocusRequester,
                upFocusRequester = when {
                    castItems.isNotEmpty() -> firstCastFocusRequester
                    seasons.isNotEmpty() -> firstSeasonFocusRequester
                    else -> primaryActionFocusRequester
                },
                onNavigateUp = {
                    if (listState.isScrollInProgress) return@DetailOverviewSection
                    coroutineScope.launch {
                        when {
                            castItems.isNotEmpty() -> {
                                val castListIndex = if (seasons.isNotEmpty()) 2 else 1
                                listState.scrollToItem(castListIndex)
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { firstCastFocusRequester.requestFocus() }
                            }
                            seasons.isNotEmpty() -> {
                                listState.scrollToItem(1)
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { firstSeasonFocusRequester.requestFocus() }
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
                modifier = Modifier.padding(top = spacing.rowGap),
            )
        }

        if (similarItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvSimilarPanel),
                ) {
                    DetailStripTitle(
                        title = "More Like This",
                        modifier = Modifier.padding(
                            horizontal = spacing.gutter,
                            vertical = spacing.elementGap,
                        ),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    ) {
                        items(similarItems, key = { it.id }) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                aspectRatio = 2f / 3f,
                                cardWidth = similarCardWidth,
                                modifier = if (item.id == similarItems.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstSimilarFocusRequester)
                                        .focusProperties { up = overviewFocusRequester }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                runCatching { overviewFocusRequester.requestFocus() }
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
