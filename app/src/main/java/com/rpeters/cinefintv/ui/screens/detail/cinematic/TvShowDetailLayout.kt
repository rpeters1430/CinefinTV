@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.EpisodeListRow
import com.rpeters.cinefintv.ui.screens.detail.EpisodeModel
import com.rpeters.cinefintv.ui.screens.detail.SeasonModel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.theme.CinefinRed
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

enum class TvShowTab { Episodes, Cast, Similar, Details }

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
    selectedSeasonIndex: Int,
    onSeasonSelected: (Int) -> Unit,
    episodes: List<EpisodeModel>,
    resumeEpisodeIndex: Int,
    onEpisodeClick: (EpisodeModel) -> Unit,
    castItems: List<CastModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    description: String,
    factItems: List<DetailLabeledMetaItem>,
    factSummary: String,
    selectedTab: TvShowTab,
    onTabSelected: (TvShowTab) -> Unit,
    episodeListState: LazyListState,
    castGridState: LazyGridState,
    similarGridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val panelFocusRequester = remember { FocusRequester() }
    val railFocusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxSize()) {
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
            focusProperties = { down = railFocusRequester },
        )

        val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left rail
            LazyColumn(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .padding(top = spacing.rowGap),
            ) {
                itemsIndexed(TvShowTab.entries) { index, tab ->
                    var railItemFocused by remember { mutableStateOf(false) }
                    val isSelected = selectedTab == tab

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .then(
                                if (index == 0) Modifier.focusRequester(railFocusRequester) else Modifier
                            )
                            .drawBehind {
                                if (isSelected) {
                                    drawRect(
                                        color = CinefinRed,
                                        size = Size(4.dp.toPx(), size.height),
                                    )
                                }
                            }
                            .onFocusChanged { railItemFocused = it.isFocused }
                            .focusProperties { right = panelFocusRequester }
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected || railItemFocused) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected || railItemFocused)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        drawRect(color = dividerColor)
                    }
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(panelFocusRequester)
                    .focusProperties { left = railFocusRequester },
            ) { tab ->
                when (tab) {
                    TvShowTab.Episodes -> EpisodesPanel(
                        seasons = seasons,
                        selectedSeasonIndex = selectedSeasonIndex,
                        onSeasonSelected = onSeasonSelected,
                        episodes = episodes,
                        resumeEpisodeIndex = resumeEpisodeIndex,
                        onEpisodeClick = onEpisodeClick,
                        listState = episodeListState,
                    )
                    TvShowTab.Cast -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = castGridState,
                        contentPadding = PaddingValues(spacing.cardGap),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
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
                    TvShowTab.Similar -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = similarGridState,
                        contentPadding = PaddingValues(spacing.cardGap),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                        verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    ) {
                        items(similarItems) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 2f / 3f,
                                onClick = { onSimilarClick(item.id) },
                            )
                        }
                    }
                    TvShowTab.Details -> DetailsPanel(
                        title = title,
                        posterUrl = posterUrl,
                        description = description,
                        factItems = factItems,
                        genres = genres,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodesPanel(
    seasons: List<SeasonModel>,
    selectedSeasonIndex: Int,
    onSeasonSelected: (Int) -> Unit,
    episodes: List<EpisodeModel>,
    resumeEpisodeIndex: Int,
    onEpisodeClick: (EpisodeModel) -> Unit,
    listState: LazyListState,
) {
    val spacing = LocalCinefinSpacing.current
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.cardGap, vertical = spacing.elementGap),
            horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
        ) {
            itemsIndexed(seasons) { index, season ->
                CinefinChip(
                    label = season.title,
                    strong = index == selectedSeasonIndex,
                    modifier = Modifier.clickable { onSeasonSelected(index) },
                )
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = spacing.gutter),
        ) {
            itemsIndexed(episodes) { index, episode ->
                EpisodeListRow(
                    episode = episode,
                    isNext = index == resumeEpisodeIndex,
                    onClick = { onEpisodeClick(episode) },
                )
            }
        }
    }
}

@Composable
private fun DetailsPanel(
    title: String,
    posterUrl: String?,
    description: String,
    factItems: List<DetailLabeledMetaItem>,
    genres: List<String>,
) {
    val spacing = LocalCinefinSpacing.current
    LazyColumn(
        contentPadding = PaddingValues(start = spacing.cardGap, end = spacing.cardGap, bottom = spacing.gutter),
    ) {
        item {
            DetailOverviewSection(
                title = title,
                posterUrl = posterUrl,
                description = description,
                factItems = factItems,
                chips = genres,
            )
        }
    }
}
