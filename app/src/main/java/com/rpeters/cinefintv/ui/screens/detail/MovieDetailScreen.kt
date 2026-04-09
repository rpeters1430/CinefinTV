@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroIconAction
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout

@Composable
fun MovieDetailScreen(
    itemId: String,
    onPlayMovie: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.init(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenPaused by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> hasBeenPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                    hasBeenPaused = false
                    viewModel.load(silent = true)
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
    val contentId = (uiState as? MovieDetailUiState.Content)?.movie?.id
    // Keyed on contentId so it resets when navigating to a different movie (e.g. from More Like This).
    var didInitialFocus by remember(contentId) { mutableStateOf(false) }

    LaunchedEffect(contentId) {
        if (!didInitialFocus) {
            val content = uiState as? MovieDetailUiState.Content ?: return@LaunchedEffect
            focusDetailScreenAtTop(
                listState = listState,
                initialFocusRequester = primaryActionFocus,
                anchorFocusRequester = topFocus,
            )
            didInitialFocus = true
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
                if (showDeleteDialog) {
                    ConfirmDeleteDialog(
                        title = "Delete ${movie.title}?",
                        message = "This will remove the item from your Jellyfin library.",
                        onDismissRequest = { showDeleteDialog = false },
                        onConfirmDelete = {
                            showDeleteDialog = false
                            viewModel.deleteMovie(onBack)
                        },
                    )
                }

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

                val heroTagline = remember(movie) {
                    movie.overview
                        ?.substringBefore(".")
                        ?.trim()
                        ?.takeIf { it.length in 24..96 }
                        ?.let { "$it." }
                }

                val heroSummary = remember(movie, heroTagline) {
                    heroTagline ?: movie.overview
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { overview ->
                            if (overview.length <= 140) overview else overview.take(137).trimEnd() + "..."
                        }
                }

                val heroBadges = remember(movie) {
                    buildList {
                        movie.videoQuality?.let(::add)
                        movie.audioLabel?.let(::add)
                        movie.officialRating?.let(::add)
                        addAll(movie.genres.take(2))
                    }
                }

                val directorLine = remember(movie) {
                    movie.directors
                        .take(2)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?.let { "Directed by $it" }
                }

                val primaryActionLabel = when {
                    movie.playbackProgress != null -> "▶ Resume"
                    else -> "▶ Play"
                }

                val heroSecondaryActions = remember(movie.isWatched) {
                    listOf(
                        HeroIconAction(
                            icon = Icons.Default.Check,
                            contentDescription = if (movie.isWatched) "Mark unwatched" else "Mark watched",
                            onClick = {
                                if (movie.isWatched) {
                                    viewModel.markUnwatched()
                                } else {
                                    viewModel.markWatched()
                                }
                            },
                        ),
                        HeroIconAction(
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete movie",
                            onClick = { showDeleteDialog = true },
                        ),
                    )
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
                    topFocusRequester = topFocus,
                    primaryActionFocusRequester = primaryActionFocus,
                    description = movie.overview ?: "",
                    heroTagline = heroSummary,
                    directorLine = directorLine,
                    heroBadges = heroBadges,
                    heroSecondaryActions = heroSecondaryActions,
                    factItems = factItems,
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
