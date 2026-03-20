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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val primaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val castEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val similarEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    var lastFocusedCastId by rememberSaveable { mutableStateOf<String?>(cast.firstOrNull()?.id) }
    var lastFocusedSimilarId by rememberSaveable { mutableStateOf<String?>(similarMovies.firstOrNull()?.id) }

    LaunchedEffect(movie.id) {
        listState.scrollToItem(0)
        primaryActionFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(
                backdropUrl = movie.backdropUrl,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) scope.launch { listState.scrollToItem(0) } },
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
                        imageUrl = movie.posterUrl,
                        title = movie.title,
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
                                add("Movie")
                                movie.year?.let { add("$it") }
                                movie.officialRating?.let { add(it) }
                                movie.duration?.let { add(it) }
                                movie.rating?.let { add("★ $it") }
                                if (movie.isWatched) add("Watched")
                            }
                        )
                        Text(
                            text = movie.title,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                        movie.overview?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                            )
                        }
                        if (movie.genres.isNotEmpty()) {
                            DetailChipRow(labels = movie.genres.take(4))
                        }
                        if (movie.studios.isNotEmpty()) {
                            Text(
                                text = "Studio: ${movie.studios.take(2).joinToString(" • ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        movie.playbackProgress?.let {
                            DetailProgressLabel(progress = it)
                        }
                        DetailActionRow(
                            primaryLabel = when {
                                movie.playbackProgress != null -> "Resume Movie"
                                movie.isWatched -> "Play Again"
                                else -> "Play Movie"
                            },
                            onPrimaryClick = { onPlayMovie(movie.id) },
                            secondaryLabel = "More Like This",
                            onSecondaryClick = {
                                similarMovies.firstOrNull()?.let { onOpenMovie(it.id) }
                            },
                            primaryFocusRequester = primaryActionFocusRequester,
                            primaryDownFocusRequester = if (cast.isNotEmpty() || similarMovies.isNotEmpty()) {
                                if (cast.isNotEmpty()) castEntryFocusRequester else similarEntryFocusRequester
                            } else {
                                null
                            },
                        )
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
                                        if (person.id == lastFocusedCastId) Modifier.focusRequester(castEntryFocusRequester) else Modifier
                                    )
                                    .then(
                                        if (person.id == cast.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = primaryActionFocusRequester
                                                if (similarMovies.isNotEmpty()) down = similarEntryFocusRequester
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

        if (similarMovies.isNotEmpty()) {
            item {
                DetailContentSection(title = "Similar Movies", eyebrow = "Keep Watching") {
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
                                modifier = Modifier
                                    .then(
                                        if (item.id == lastFocusedSimilarId) {
                                            Modifier.focusRequester(similarEntryFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (item.id == similarMovies.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = if (cast.isNotEmpty()) castEntryFocusRequester else primaryActionFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedSimilarId = item.id },
                                onClick = { onOpenMovie(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
