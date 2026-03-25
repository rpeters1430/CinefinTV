@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

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
    secondaryActions: List<Pair<String, () -> Unit>>,
    primaryActionFocusRequester: FocusRequester,
    description: String,
    factItems: List<DetailLabeledMetaItem>,
    factSummary: String,
    castItems: List<CastModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = spacing.gutter * 2),
    ) {
        item {
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
            )
        }

        item {
            DetailOverviewSection(
                title = title,
                posterUrl = posterUrl,
                description = description,
                factItems = factItems,
                chips = genres,
                modifier = Modifier.padding(top = spacing.rowGap),
            )
        }

        if (castItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .padding(top = spacing.rowGap)
                        .testTag(DetailTestTags.MovieCastSection),
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
                        .testTag(DetailTestTags.MovieSimilarSection),
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
                        items(similarItems) { mediaItem ->
                            TvMediaCard(
                                title = mediaItem.title,
                                imageUrl = mediaItem.imageUrl,
                                aspectRatio = 2f / 3f, // Standard poster ratio
                                onClick = { onSimilarClick(mediaItem.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
