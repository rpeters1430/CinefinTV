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
import androidx.compose.runtime.snapshotFlow
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
import com.rpeters.cinefintv.ui.screens.detail.DetailShelfPanel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.flow.first
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
    val firstContentFocusRequester = overviewFocusRequester

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
                    creditLine = directorLine,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    secondaryIconActions = heroSecondaryActions,
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    primaryActionDownFocusRequester = firstContentFocusRequester,
                    onDownNavigation = {
                        if (listState.isScrollInProgress) return@FlatDetailHero
                        coroutineScope.launch {
                            listState.scrollToItemAndAwaitLayout(1)
                            runCatching { firstContentFocusRequester.requestFocus() }
                        }
                    },
                )
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
                upFocusRequester = primaryActionFocusRequester,
                onNavigateUp = {
                    if (listState.isScrollInProgress) return@DetailOverviewSection
                    coroutineScope.launch {
                        listState.scrollToItemAndAwaitLayout(0)
                        runCatching { primaryActionFocusRequester.requestFocus() }
                    }
                },
                onNavigateDown = if (castItems.isNotEmpty() || similarItems.isNotEmpty()) {
                    {
                        if (listState.isScrollInProgress) return@DetailOverviewSection
                        coroutineScope.launch {
                            when {
                                castItems.isNotEmpty() -> {
                                    listState.scrollToItemAndAwaitLayout(2)
                                    runCatching { firstCastFocusRequester.requestFocus() }
                                }
                                similarItems.isNotEmpty() -> {
                                    listState.scrollToItemAndAwaitLayout(2)
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
                        .padding(top = spacing.rowGap / 2)
                        .testTag(DetailTestTags.MovieCastSection),
                    title = "People",
                    subtitle = "Cast and key performers",
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
                        .testTag(DetailTestTags.MovieSimilarSection),
                    title = "More Like This",
                    subtitle = "Recommended from your library",
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    ) {
                        items(similarItems, key = { it.id }) { mediaItem ->
                            TvMediaCard(
                                title = mediaItem.title,
                                imageUrl = mediaItem.imageUrl,
                                watchStatus = mediaItem.watchStatus,
                                playbackProgress = mediaItem.playbackProgress,
                                aspectRatio = 2f / 3f, // Standard poster ratio
                                cardWidth = 220.dp,
                                modifier = if (mediaItem.id == similarItems.firstOrNull()?.id) {
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
                                onClick = { onSimilarClick(mediaItem.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollToItemAndAwaitLayout(index: Int) {
    if (!isIndexVisible(index)) {
        scrollToItem(index)
        snapshotFlow { isIndexVisible(index) }.first { it }
    }
}

private fun LazyListState.isIndexVisible(index: Int): Boolean =
    layoutInfo.visibleItemsInfo.any { it.index == index }
