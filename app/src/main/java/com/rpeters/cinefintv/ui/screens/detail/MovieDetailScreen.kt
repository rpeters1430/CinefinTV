@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout

@Composable
fun MovieDetailScreen(
    onPlayMovie: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenPaused by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> hasBeenPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                    hasBeenPaused = false
                    viewModel.refreshWatchStatus()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val listState = rememberLazyListState()
    val topFocus = remember { FocusRequester() }
    val primaryActionFocus = remember { FocusRequester() }

    LaunchedEffect((uiState as? MovieDetailUiState.Content)?.movie?.id) {
        if (uiState is MovieDetailUiState.Content) {
            focusDetailScreenAtTop(
                listState = listState,
                initialFocusRequester = topFocus,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is MovieDetailUiState.Loading -> DetailLoadingState()
            is MovieDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is MovieDetailUiState.Content -> {
                val movie = state.movie

                val factItems = remember(movie) {
                    buildList {
                        movie.duration?.let {
                            add(DetailLabeledMetaItem(Icons.Default.Schedule, "Runtime", it))
                        }
                        movie.premieredDate?.let {
                            add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "Released", it))
                        } ?: movie.year?.let {
                            add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "Released", "$it"))
                        }
                        if (movie.directors.isNotEmpty()) {
                            add(
                                DetailLabeledMetaItem(
                                    Icons.Default.Movie,
                                    "Directed by",
                                    movie.directors.take(2).joinToString(", "),
                                )
                            )
                        }
                        if (movie.studios.isNotEmpty()) {
                            add(
                                DetailLabeledMetaItem(
                                    Icons.Default.Apartment,
                                    "Studio",
                                    movie.studios.take(2).joinToString(", "),
                                )
                            )
                        }
                    }
                }

                val eyebrow = remember(movie) {
                    listOfNotNull(movie.year?.toString(), movie.duration).joinToString(" · ")
                }

                val ratingText = remember(movie) {
                    movie.rating?.let { "★ $it" }
                }

                val factSummary = remember(movie) {
                    listOfNotNull(
                        movie.directors.firstOrNull(),
                        movie.studios.firstOrNull(),
                    ).joinToString(" · ")
                }

                val primaryActionLabel = when {
                    movie.playbackProgress != null -> "▶ Resume"
                    else -> "▶ Play"
                }

                MovieDetailLayout(
                    backdropUrl = movie.backdropUrl,
                    posterUrl = movie.posterUrl,
                    logoUrl = movie.logoUrl,
                    title = movie.title,
                    eyebrow = eyebrow,
                    ratingText = ratingText,
                    genres = movie.genres,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = { onPlayMovie(movie.id) },
                    secondaryActions = listOf("Watchlist" to {}),
                    topFocusRequester = topFocus,
                    primaryActionFocusRequester = primaryActionFocus,
                    description = movie.overview ?: "",
                    factItems = factItems,
                    factSummary = factSummary,
                    castItems = state.cast,
                    similarItems = state.similarMovies,
                    onCastClick = { personId -> onOpenPerson(personId) },
                    onSimilarClick = { onOpenMovie(it) },
                    listState = listState,
                )
            }
        }
    }
}
