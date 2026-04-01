@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout

@Composable
fun TvShowDetailScreen(
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvShowDetailViewModel = hiltViewModel(),
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

    LaunchedEffect((uiState as? TvShowDetailUiState.Content)?.show?.id) {
        val content = uiState as? TvShowDetailUiState.Content ?: return@LaunchedEffect
        focusDetailScreenAtTop(
            listState = listState,
            initialFocusRequester = primaryActionFocus,
            anchorFocusRequester = topFocus,
        )
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

                val factItems = remember(show) {
                    buildList {
                        if (show.seasonCount > 0) {
                            add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Seasons", show.seasonCount.toString()))
                        }
                        show.status?.let {
                            add(DetailLabeledMetaItem(Icons.Default.Tv, "Status", it))
                        }
                        show.endedDate?.let {
                            add(DetailLabeledMetaItem(Icons.Default.Event, "Ends", it))
                        }
                        show.airedDate?.let {
                            add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "First Aired", it))
                        } ?: show.yearRange?.let {
                            add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "Years", it))
                        }
                        if (show.networks.isNotEmpty()) {
                            add(
                                DetailLabeledMetaItem(
                                    Icons.Default.CastConnected,
                                    "Network",
                                    show.networks.take(2).joinToString(", "),
                                )
                            )
                        }
                        if (show.creators.isNotEmpty()) {
                            add(
                                DetailLabeledMetaItem(
                                    Icons.Default.Edit,
                                    "Created by",
                                    show.creators.take(2).joinToString(", "),
                                )
                            )
                        }
                    }
                }

                val eyebrow = remember(show) {
                    val parts = mutableListOf("TV SERIES")
                    if (show.seasonCount > 0) parts.add("${show.seasonCount} SEASONS")
                    parts.joinToString(" · ")
                }

                val ratingText = remember(show) {
                    show.rating?.let { "★ $it" }
                }

                val primaryActionLabel = remember(show) {
                    when {
                        show.nextUpEpisodeId != null -> "▶ ${show.nextUpTitle ?: "Next Up"}"
                        else -> "▶ Play"
                    }
                }

                TvShowDetailLayout(
                    backdropUrl = show.backdropUrl,
                    posterUrl = show.posterUrl,
                    logoUrl = show.logoUrl,
                    title = show.title,
                    eyebrow = eyebrow,
                    ratingText = ratingText,
                    genres = show.genres,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = {
                        if (show.nextUpEpisodeId != null) {
                            onPlayEpisode(show.nextUpEpisodeId)
                        } else {
                            state.episodes.firstOrNull()?.let { onPlayEpisode(it.id) }
                                ?: state.seasons.firstOrNull()?.let { onOpenSeason(it.id) }
                        }
                    },
                    secondaryActions = emptyList(),
                    topFocusRequester = topFocus,
                    primaryActionFocusRequester = primaryActionFocus,
                    seasons = state.seasons,
                    onSeasonClick = { season -> onOpenSeason(season.id) },
                    castItems = state.cast,
                    similarItems = state.similarShows,
                    onCastClick = { personId -> onOpenPerson(personId) },
                    onSimilarClick = { showId -> onOpenShow(showId) },
                    description = show.overview ?: "",
                    factItems = factItems,
                    listState = listState,
                )
            }
        }
    }
}
