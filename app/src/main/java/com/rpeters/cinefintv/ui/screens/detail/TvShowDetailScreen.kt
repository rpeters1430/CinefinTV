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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroSecondaryAction
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout

@Composable
fun TvShowDetailScreen(
    itemId: String,
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvShowDetailViewModel = hiltViewModel(),
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
    val contentId = (uiState as? TvShowDetailUiState.Content)?.show?.id
    // Keyed on contentId so it resets when navigating to a different show (e.g. from Similar row).
    var didInitialFocus by remember(contentId) { mutableStateOf(false) }

    LaunchedEffect(contentId) {
        if (!didInitialFocus) {
            val content = uiState as? TvShowDetailUiState.Content ?: return@LaunchedEffect
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
            is TvShowDetailUiState.Loading -> DetailLoadingState()
            is TvShowDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is TvShowDetailUiState.Content -> {
                val show = state.show
                if (showDeleteDialog) {
                    ConfirmDeleteDialog(
                        title = "Delete ${show.title}?",
                        message = "This will remove the show from your Jellyfin library.",
                        onDismissRequest = { showDeleteDialog = false },
                        onConfirmDelete = {
                            showDeleteDialog = false
                            chromeFocusController?.shouldRestoreFocusToContent = true
                            viewModel.deleteShow(onBack)
                        },
                    )
                }

                if (showMoreDialog) {
                    MediaActionDialog(
                        title = show.title,
                        actions = buildList {
                            add(
                                MediaActionDialogItem(
                                    label = if (show.nextUpEpisodeId != null) "Play Next Up" else "Open Seasons",
                                    supportingText = "Start watching this series.",
                                    onClick = {
                                        if (show.nextUpEpisodeId != null) onPlayEpisode(show.nextUpEpisodeId)
                                        else state.seasons.firstOrNull()?.let { onOpenSeason(it.id) }
                                    },
                                )
                            )
                            add(
                                MediaActionDialogItem(
                                    label = if (show.isWatched) "Remove from Watchlist" else "Add to Watchlist",
                                    supportingText = "Toggle this show's saved state.",
                                    onClick = {
                                        if (show.isWatched) viewModel.markUnwatched()
                                        else viewModel.markWatched()
                                    },
                                )
                            )
                            add(
                                MediaActionDialogItem(
                                    label = "Delete",
                                    supportingText = "Remove this show from your Jellyfin library.",
                                    isDestructive = true,
                                    onClick = { showDeleteDialog = true },
                                )
                            )
                        },
                        onDismissRequest = { showMoreDialog = false },
                    )
                }

                val primaryActionLabel = remember(show) {
                    when {
                        show.nextUpEpisodeId != null -> "Resume"
                        else -> "Play"
                    }
                }

                val qualityBadges = remember(show) {
                    buildList {
                        show.officialRating?.let(::add)
                        if (show.rating?.toDoubleOrNull()?.let { it >= 8.0 } == true) add("4K")
                    }
                }

                val metadataItems = remember(show) {
                    buildList {
                        show.yearRange?.let(::add)
                        if (show.seasonCount > 0) add("${show.seasonCount} seasons")
                        show.status?.let(::add)
                    }
                }

                val heroSecondaryActions = remember(show.isWatched) {
                    listOf(
                        HeroSecondaryAction(
                            label = if (show.isWatched) "✓ In Watchlist" else "+ Watchlist",
                            onClick = {
                                if (show.isWatched) viewModel.markUnwatched()
                                else viewModel.markWatched()
                            },
                        ),
                        HeroSecondaryAction(
                            label = "···",
                            onClick = { showMoreDialog = true },
                        )
                    )
                }

                TvShowDetailLayout(
                    backdropUrl = show.backdropUrl,
                    posterUrl = show.posterUrl,
                    title = show.title,
                    metadataItems = metadataItems,
                    qualityBadges = qualityBadges,
                    genres = show.genres,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = {
                        if (show.nextUpEpisodeId != null) {
                            onPlayEpisode(show.nextUpEpisodeId)
                        } else {
                            state.seasons.firstOrNull()?.let { onOpenSeason(it.id) }
                        }
                    },
                    topFocusRequester = topFocus,
                    primaryActionFocusRequester = primaryActionFocus,
                    nextUpTitle = show.nextUpTitle,
                    onNextUpClick = show.nextUpEpisodeId?.let { nextUpId -> { onPlayEpisode(nextUpId) } },
                    seasons = state.seasons,
                    onSeasonClick = { season -> onOpenSeason(season.id) },
                    castItems = state.cast,
                    similarItems = state.similarShows,
                    onCastClick = { personId -> onOpenPerson(personId) },
                    onSimilarClick = { showId -> onOpenShow(showId) },
                    description = show.overview ?: "",
                    heroSecondaryActions = heroSecondaryActions,
                    listState = listState,
                    drawerFocusRequester = destinationFocus.drawerFocusRequester,
                )
            }
        }
    }
}
