@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.components.TvMediaCard

@Composable
fun TvShowDetailScreen(
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvShowDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TvShowDetailUiState.Loading -> DetailLoadingState()
            is TvShowDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is TvShowDetailUiState.Content -> TvShowDetailContent(
                show = state.show,
                seasons = state.seasons,
                cast = state.cast,
                similarShows = state.similarShows,
                onPlayEpisode = onPlayEpisode,
                onOpenSeason = onOpenSeason,
                onOpenShow = onOpenShow,
            )
        }
    }
}

@Composable
private fun TvShowDetailContent(
    show: TvShowDetailModel,
    seasons: List<SeasonModel>,
    cast: List<CastModel>,
    similarShows: List<SimilarMovieModel>,
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val anchorFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val primaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val seasonsEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val castEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val similarEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    var lastFocusedSeasonId by rememberSaveable { mutableStateOf<String?>(seasons.firstOrNull()?.id) }
    var lastFocusedCastId by rememberSaveable { mutableStateOf<String?>(cast.firstOrNull()?.id) }
    var lastFocusedSimilarId by rememberSaveable { mutableStateOf<String?>(similarShows.firstOrNull()?.id) }

    LaunchedEffect(show.id) {
        // Request initial focus on the top anchor
        anchorFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailAnchor(
                focusRequester = anchorFocusRequester,
                onFocused = {
                    scope.launch {
                        listState.scrollToItem(0)
                        primaryActionFocusRequester.requestFocus()
                    }
                }
            )
        }
        item {
            DetailHeroBox(
                backdropUrl = show.backdropUrl,
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DetailPosterArt(
                        imageUrl = show.posterUrl,
                        title = show.title,
                        modifier = Modifier
                            .width(196.dp)
                            .height(294.dp),
                    )
                    DetailGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        DetailChipRow(
                            labels = buildList {
                                add("Series")
                                show.yearRange?.let { add(it) }
                                show.status?.let { add(it) }
                                show.officialRating?.let { add(it) }
                                show.rating?.let { add("★ $it") }
                                if (show.seasonCount > 0) add("${show.seasonCount} seasons")
                            }
                        )
                        Text(
                            text = show.title,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                        show.overview?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                            )
                        }
                        if (show.genres.isNotEmpty()) {
                            DetailChipRow(labels = show.genres.take(4))
                        }
                        DetailActionRow(
                            primaryLabel = if (show.nextUpEpisodeId != null) {
                                "Play ${show.nextUpTitle ?: "Next Up"}"
                            } else {
                                "Browse Seasons"
                            },
                            onPrimaryClick = {
                                if (show.nextUpEpisodeId != null) {
                                    onPlayEpisode(show.nextUpEpisodeId)
                                } else {
                                    seasons.firstOrNull()?.let { onOpenSeason(it.id) }
                                }
                            },
                            secondaryLabel = if (seasons.isNotEmpty()) "Season Guide" else null,
                            onSecondaryClick = if (seasons.isNotEmpty()) {
                                { onOpenSeason(seasons.first().id) }
                            } else {
                                null
                            },
                            primaryFocusRequester = primaryActionFocusRequester,
                            primaryDownFocusRequester = if (seasons.isNotEmpty() || cast.isNotEmpty() || similarShows.isNotEmpty()) {
                                when {
                                    seasons.isNotEmpty() -> seasonsEntryFocusRequester
                                    cast.isNotEmpty() -> castEntryFocusRequester
                                    else -> similarEntryFocusRequester
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (seasons.isNotEmpty()) {
            item {
                DetailContentSection(title = "Seasons", eyebrow = "${seasons.size} available") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(seasons) { season ->
                            TvMediaCard(
                                title = season.title,
                                subtitle = season.episodeCount?.let { "$it Episodes" },
                                imageUrl = season.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 140.dp,
                                unwatchedCount = season.unwatchedCount,
                                modifier = Modifier
                                    .then(
                                        if (season.id == lastFocusedSeasonId) Modifier.focusRequester(seasonsEntryFocusRequester) else Modifier
                                    )
                                    .then(
                                        if (season.id == seasons.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = primaryActionFocusRequester
                                                if (cast.isNotEmpty()) down = castEntryFocusRequester
                                                else if (similarShows.isNotEmpty()) down = similarEntryFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedSeasonId = season.id },
                                onClick = { onOpenSeason(season.id) },
                            )
                        }
                    }
                }
            }
        }

        if (cast.isNotEmpty()) {
            item {
                DetailContentSection(title = "Cast & Crew", eyebrow = "${cast.size} people") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(cast) { person ->
                            TvPersonCard(
                                name = person.name,
                                role = person.role,
                                imageUrl = person.imageUrl,
                                modifier = Modifier
                                    .then(
                                        if (person.id == lastFocusedCastId) {
                                            Modifier.focusRequester(castEntryFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (person.id == cast.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = if (seasons.isNotEmpty()) seasonsEntryFocusRequester else primaryActionFocusRequester
                                                if (similarShows.isNotEmpty()) down = similarEntryFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedCastId = person.id },
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }

        if (similarShows.isNotEmpty()) {
            item {
                DetailContentSection(title = "More Like This", eyebrow = "Recommended next") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(similarShows) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 140.dp,
                                modifier = Modifier
                                    .then(
                                        if (item.id == lastFocusedSimilarId) {
                                            Modifier.focusRequester(similarEntryFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (item.id == similarShows.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = when {
                                                    cast.isNotEmpty() -> castEntryFocusRequester
                                                    seasons.isNotEmpty() -> seasonsEntryFocusRequester
                                                    else -> primaryActionFocusRequester
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedSimilarId = item.id },
                                onClick = { onOpenShow(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
