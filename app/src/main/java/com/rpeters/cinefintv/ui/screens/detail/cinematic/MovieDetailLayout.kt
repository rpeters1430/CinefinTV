@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.launch

/**
 * Movie detail screen content: CinematicHero + continuous vertical scroll.
 * [listState] must be owned by MovieDetailScreen for the scroll-anchor fix.
 */
@Composable
fun MovieDetailLayout(
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
    description: String,
    heroTagline: String?,
    directorLine: String?,
    heroBadges: List<String>,
    heroSecondaryActions: List<HeroIconAction>,
    factItems: List<DetailLabeledMetaItem>,
    castItems: List<CastModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val overviewFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val similarCardWidth: Dp = 196.dp
    val firstContentFocusRequester = when {
        castItems.isNotEmpty() -> firstCastFocusRequester
        similarItems.isNotEmpty() -> firstSimilarFocusRequester
        else -> null
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
                    creditLine = directorLine,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    secondaryIconActions = heroSecondaryActions,
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

        if (castItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap / 2)
                        .testTag(DetailTestTags.MovieCastSection),
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
                                            up = primaryActionFocusRequester
                                            down = overviewFocusRequester
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            when {
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { primaryActionFocusRequester.requestFocus() }
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
                upFocusRequester = if (castItems.isNotEmpty()) {
                    firstCastFocusRequester
                } else {
                    primaryActionFocusRequester
                },
                onNavigateUp = {
                    if (listState.isScrollInProgress) return@DetailOverviewSection
                    coroutineScope.launch {
                        if (castItems.isNotEmpty()) {
                            listState.scrollToItem(1)
                            androidx.compose.runtime.withFrameNanos { }
                            androidx.compose.runtime.withFrameNanos { }
                            runCatching { firstCastFocusRequester.requestFocus() }
                        } else {
                            listState.scrollToItem(0)
                            androidx.compose.runtime.withFrameNanos { }
                            androidx.compose.runtime.withFrameNanos { }
                            runCatching { primaryActionFocusRequester.requestFocus() }
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
                        .testTag(DetailTestTags.MovieSimilarSection),
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
                        items(similarItems, key = { it.id }) { mediaItem ->
                            TvMediaCard(
                                title = mediaItem.title,
                                imageUrl = mediaItem.imageUrl,
                                watchStatus = mediaItem.watchStatus,
                                playbackProgress = mediaItem.playbackProgress,
                                aspectRatio = 2f / 3f, // Standard poster ratio
                                cardWidth = similarCardWidth,
                                modifier = if (mediaItem.id == similarItems.firstOrNull()?.id) {
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
                                onClick = { onSimilarClick(mediaItem.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
