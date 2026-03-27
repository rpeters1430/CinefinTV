@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.SeasonModel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

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
    secondaryActions: List<Pair<String, () -> Unit>>,
    primaryActionFocusRequester: FocusRequester,
    seasons: List<SeasonModel>,
    onSeasonClick: (SeasonModel) -> Unit,
    castItems: List<CastModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    description: String,
    factItems: List<DetailLabeledMetaItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val spacing = LocalCinefinSpacing.current
    val overviewFocusRequester = remember { FocusRequester() }
    val firstSeasonFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val firstContentFocusRequester = when {
        seasons.isNotEmpty() -> firstSeasonFocusRequester
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
            Column(
                modifier = Modifier.blockBringIntoView()
            ) {
                DetailAnchor(
                    focusRequester = topFocusRequester,
                    downFocusRequester = primaryActionFocusRequester,
                    onFocused = {},
                )
                CinematicHero(
                    backdropUrl = backdropUrl,
                    logoUrl = logoUrl,
                    title = title,
                    eyebrow = eyebrow,
                    ratingText = ratingText,
                    genres = genres,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    secondaryActions = secondaryActions,
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    primaryActionDownFocusRequester = overviewFocusRequester,
                    upFocusRequester = topFocusRequester,
                    listState = listState,
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
                downFocusRequester = firstContentFocusRequester,
                modifier = Modifier.padding(top = spacing.rowGap),
            )
        }

        if (seasons.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvEpisodesPanel), // Reusing tag for consistency
                ) {
                    CinefinShelfTitle(
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
                                        .focusRequester(firstSeasonFocusRequester)
                                        .focusProperties { up = overviewFocusRequester }
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
                    CinefinShelfTitle(
                        title = "Cast",
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
                                        .focusRequester(firstCastFocusRequester)
                                        .focusProperties {
                                            up = if (seasons.isNotEmpty()) {
                                                firstSeasonFocusRequester
                                            } else {
                                                overviewFocusRequester
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
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.TvSimilarPanel),
                ) {
                    CinefinShelfTitle(
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
                        items(similarItems) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                aspectRatio = 2f / 3f,
                                modifier = if (item.id == similarItems.firstOrNull()?.id) {
                                    Modifier
                                        .focusRequester(firstSimilarFocusRequester)
                                        .focusProperties {
                                            up = when {
                                                castItems.isNotEmpty() -> firstCastFocusRequester
                                                seasons.isNotEmpty() -> firstSeasonFocusRequester
                                                else -> overviewFocusRequester
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
