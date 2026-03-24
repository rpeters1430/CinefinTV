@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
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
    overviewFocusRequester: FocusRequester,
    description: String,
    factItems: List<DetailLabeledMetaItem>,
    factSummary: String,
    castItems: List<CastModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    listState: LazyListState,
    communityRating: Float? = null,
    criticRating: Float? = null,
    status: String? = null,
    originalLanguage: String? = null,
    budget: Double? = null,
    revenue: Double? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = spacing.gutter),
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
                focusProperties = { down = overviewFocusRequester },
            )
        }

        item {
            DetailOverviewSection(
                title = title,
                posterUrl = posterUrl,
                description = description,
                factItems = factItems,
                chips = genres,
                overviewFocusRequester = overviewFocusRequester,
                modifier = Modifier.padding(top = spacing.rowGap),
            )
        }

        if (castItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.testTag(DetailTestTags.MovieCastSection),
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
                    modifier = Modifier.testTag(DetailTestTags.MovieSimilarSection),
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
                                aspectRatio = 16f / 9f,
                                cardWidth = 280.dp,
                                onClick = { onSimilarClick(mediaItem.id) },
                            )
                        }
                    }
                }
            }
        }

        if (criticRating != null || communityRating != null) {
            item {
                ReviewsSection(
                    criticRating = criticRating,
                    communityRating = communityRating,
                    modifier = Modifier.padding(top = spacing.rowGap),
                )
            }
        }

        val statItems = buildList {
            status?.let { add("Status" to it) }
            originalLanguage?.let { lang ->
                val displayLanguage = java.util.Locale.forLanguageTag(lang)
                    .getDisplayLanguage(java.util.Locale.ENGLISH)
                    .takeIf { it.isNotBlank() } ?: lang
                add("Original Language" to displayLanguage)
            }
            budget?.let { formatCurrency(it)?.let { fmt -> add("Budget" to fmt) } }
            revenue?.let { formatCurrency(it)?.let { fmt -> add("Revenue" to fmt) } }
        }
        if (statItems.isNotEmpty()) {
            item {
                StatsSection(
                    stats = statItems,
                    modifier = Modifier.padding(top = spacing.rowGap),
                )
            }
        }
    }
}

private fun formatCurrency(amount: Double): String? = when {
    amount >= 1_000_000 -> "\$${(amount / 1_000_000).toInt()}M"
    amount >= 1_000 -> "\$${(amount / 1_000).toInt()}K"
    else -> null
}

@Composable
private fun ReviewsSection(
    criticRating: Float?,
    communityRating: Float?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        CinefinShelfTitle(
            title = "Reviews",
            modifier = Modifier.padding(
                horizontal = spacing.gutter,
                vertical = spacing.elementGap,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            criticRating?.let { rating ->
                RatingCard(
                    label = "Critics",
                    icon = "🎬",
                    score = "${rating.toInt()}%",
                    modifier = Modifier.weight(1f),
                    surfaceColor = expressiveColors.surfaceContainerHigh,
                )
            }
            communityRating?.let { rating ->
                RatingCard(
                    label = "Community",
                    icon = "⭐",
                    score = String.format(java.util.Locale.US, "%.1f", rating),
                    modifier = Modifier.weight(1f),
                    surfaceColor = expressiveColors.surfaceContainerHigh,
                )
            }
            // If only one rating, fill the other half with an empty box so the card doesn't stretch full width
            if (criticRating == null || communityRating == null) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RatingCard(
    label: String,
    icon: String,
    score: String,
    surfaceColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = surfaceColor,
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = icon, fontSize = 22.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        Text(
            text = score,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatsSection(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        CinefinShelfTitle(
            title = "Details",
            modifier = Modifier.padding(
                horizontal = spacing.gutter,
                vertical = spacing.elementGap,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter)
                .background(
                    color = expressiveColors.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            stats.forEach { (label, value) ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
