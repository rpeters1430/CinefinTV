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
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard

@Composable
fun MovieDetailScreen(
    onPlayMovie: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is MovieDetailUiState.Loading -> DetailLoadingState()
            is MovieDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is MovieDetailUiState.Content -> MovieDetailContent(
                movie = state.movie,
                cast = state.cast,
                similarMovies = state.similarMovies,
                onPlayMovie = onPlayMovie,
                onOpenMovie = onOpenMovie,
            )
        }
    }
}

@Composable
private fun MovieDetailContent(
    movie: MovieDetailModel,
    cast: List<CastModel>,
    similarMovies: List<SimilarMovieModel>,
    onPlayMovie: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(backdropUrl = movie.backdropUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        movie.year?.let {
                            Text(text = "$it", style = MaterialTheme.typography.bodyLarge)
                        }
                        movie.officialRating?.let {
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
                        movie.duration?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        movie.rating?.let {
                            Text(
                                text = "★ $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFFFD700),
                            )
                        }
                    }
                    movie.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { onPlayMovie(movie.id) },
                            modifier = Modifier.focusRequester(playFocusRequester),
                        ) {
                            Text("Play")
                        }
                        OutlinedButton(onClick = {}) {
                            Text("Trailer")
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

        if (similarMovies.isNotEmpty()) {
            item {
                DetailContentSection(title = "Similar Movies") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(similarMovies) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                onClick = { onOpenMovie(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
