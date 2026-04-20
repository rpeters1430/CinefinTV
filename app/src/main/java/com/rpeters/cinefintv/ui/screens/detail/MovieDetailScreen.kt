@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
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
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroSecondaryAction
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
    var showMoreDialog by remember { mutableStateOf(false) }
    val chromeFocusController = LocalAppChromeFocusController.current
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
    val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocus)
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
                            chromeFocusController?.shouldRestoreFocusToContent = true
                            viewModel.deleteMovie(onBack)
                        },
                    )
                }

                if (showMoreDialog) {
                    MediaActionDialog(
                        title = movie.title,
                        actions = buildList {
                            add(
                                MediaActionDialogItem(
                                    label = if (movie.playbackProgress != null) "Resume" else "Play",
                                    supportingText = "Start playback immediately.",
                                    onClick = { onPlayMovie(movie.id) },
                                )
                            )
                            add(
                                MediaActionDialogItem(
                                    label = if (movie.isWatched) "Remove from Watchlist" else "Add to Watchlist",
                                    supportingText = "Toggle this item's saved state.",
                                    onClick = {
                                        if (movie.isWatched) viewModel.markUnwatched()
                                        else viewModel.markWatched()
                                    },
                                )
                            )
                            add(
                                MediaActionDialogItem(
                                    label = "Delete",
                                    supportingText = "Remove this item from your Jellyfin library.",
                                    isDestructive = true,
                                    onClick = { showDeleteDialog = true },
                                )
                            )
                        },
                        onDismissRequest = { showMoreDialog = false },
                    )
                }

                val metadataItems = remember(movie) {
                    buildList {
                        movie.year?.toString()?.let(::add)
                        movie.duration?.let(::add)
                        movie.officialRating?.let(::add)
                    }
                }

                val qualityBadges = remember(movie) {
                    buildList {
                        movie.videoQuality?.let(::add)
                        if (movie.audioLabel?.contains("HDR", ignoreCase = true) == true) add("HDR")
                        if (movie.audioLabel?.contains("DV", ignoreCase = true) == true) add("DV")
                    }
                }

                val primaryActionLabel = when {
                    movie.playbackProgress != null -> "Resume"
                    else -> "Play"
                }

                val heroSecondaryActions = remember(movie.isWatched) {
                    listOf(
                        HeroSecondaryAction(
                            label = if (movie.isWatched) "✓ In Watchlist" else "+ Watchlist",
                            onClick = {
                                if (movie.isWatched) {
                                    viewModel.markUnwatched()
                                } else {
                                    viewModel.markWatched()
                                }
                            },
                        ),
                        HeroSecondaryAction(
                            label = "···",
                            onClick = { showMoreDialog = true },
                        ),
                    )
                }

                MovieDetailLayout(
                    backdropUrl = movie.backdropUrl,
                    posterUrl = movie.posterUrl,
                    title = movie.title,
                    metadataItems = metadataItems,
                    qualityBadges = qualityBadges,
                    genres = movie.genres,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = { onPlayMovie(movie.id) },
                    topFocusRequester = topFocus,
                    primaryActionFocusRequester = primaryActionFocus,
                    description = movie.overview ?: "",
                    heroSecondaryActions = heroSecondaryActions,
                    castItems = state.cast,
                    similarItems = state.similarMovies,
                    onCastClick = { personId -> onOpenPerson(personId) },
                    onSimilarClick = { onOpenMovie(it) },
                    listState = listState,
                    drawerFocusRequester = destinationFocus.drawerFocusRequester,
                )
            }
        }
    }
}
