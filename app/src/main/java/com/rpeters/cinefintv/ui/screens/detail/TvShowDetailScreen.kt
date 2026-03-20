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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
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
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(backdropUrl = show.backdropUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        show.yearRange?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        show.officialRating?.let {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                colors = SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        show.status?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        show.rating?.let {
                            Text(
                                text = "★ $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                    show.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (show.nextUpEpisodeId != null) {
                            Button(
                                onClick = { onPlayEpisode(show.nextUpEpisodeId) },
                                modifier = Modifier.focusRequester(playFocusRequester),
                            ) {
                                Text("Play Next Up: ${show.nextUpTitle}")
                            }
                        } else {
                            Button(
                                onClick = { seasons.firstOrNull()?.let { onOpenSeason(it.id) } },
                                modifier = Modifier.focusRequester(playFocusRequester),
                                enabled = seasons.isNotEmpty(),
                            ) {
                                Text("Browse Seasons")
                            }
                        }
                    }
                }
            }
        }

        if (seasons.isNotEmpty()) {
            item {
                DetailContentSection(title = "Seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(seasons) { season ->
                            TvMediaCard(
                                title = season.title,
                                subtitle = season.episodeCount?.let { "$it Episodes" },
                                imageUrl = season.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                unwatchedCount = season.unwatchedCount,
                                onClick = { onOpenSeason(season.id) },
                            )
                        }
                    }
                }
            }
        }

        if (cast.isNotEmpty()) {
            item {
                DetailContentSection(title = "Cast & Crew") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(cast) { person ->
                            TvMediaCard(
                                title = person.name,
                                subtitle = person.role,
                                imageUrl = person.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 120.dp,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }

        if (similarShows.isNotEmpty()) {
            item {
                DetailContentSection(title = "More Like This") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(similarShows) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                onClick = { onOpenShow(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
