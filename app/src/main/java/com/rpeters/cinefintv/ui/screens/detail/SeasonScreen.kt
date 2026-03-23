@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero

@Composable
fun SeasonScreen(
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SeasonUiState.Loading -> DetailLoadingState()
            is SeasonUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is SeasonUiState.Content -> SeasonContent(
                season = state.season,
                episodes = state.episodes,
                onOpenEpisode = onOpenEpisode,
            )
        }
    }
}

@Composable
private fun SeasonContent(
    season: SeasonDetailModel,
    episodes: List<EpisodeModel>,
    onOpenEpisode: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val primaryActionFocusRequester = remember { FocusRequester() }
    val episodeGridEntryRequester = remember { FocusRequester() }

    val resumeEpisode = remember(episodes) {
        episodes.firstOrNull { (it.playbackProgress ?: 0f) > 0f && !it.isWatched }
            ?: episodes.firstOrNull { !it.isWatched }
    }
    var lastFocusedEpisodeId by rememberSaveable { mutableStateOf<String?>(episodes.firstOrNull()?.id) }

    LaunchedEffect(season.id) {
        primaryActionFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            CinematicHero(
                backdropUrl = season.backdropUrl,
                logoUrl = null,
                title = season.title,
                eyebrow = listOfNotNull(season.seriesName, "${episodes.size} episodes").joinToString(" · "),
                ratingText = null,
                genres = emptyList(),
                primaryActionLabel = if (resumeEpisode != null) "▶ Resume" else "▶ Play",
                onPrimaryAction = {
                    val target = resumeEpisode ?: episodes.firstOrNull()
                    if (target != null) onOpenEpisode(target.id)
                },
                secondaryActions = if (episodes.isNotEmpty())
                    listOf("Start From Episode 1" to { onOpenEpisode(episodes.first().id) })
                else emptyList(),
                primaryActionFocusRequester = primaryActionFocusRequester,
            )
        }

        item {
            DetailContentSection(
                title = "Episodes",
                eyebrow = "${episodes.count { !it.isWatched }} unwatched",
                icon = Icons.Default.VideoLibrary,
            ) {}
        }

        items(episodes, key = { it.id }) { episode ->
            EpisodeListRow(
                episode = episode,
                modifier = Modifier
                    .then(
                        if (episode.id == lastFocusedEpisodeId) Modifier.focusRequester(episodeGridEntryRequester) else Modifier
                    )
                    .then(
                        if (episode.id == episodes.firstOrNull()?.id) {
                            Modifier.focusProperties { up = primaryActionFocusRequester }
                        } else {
                            Modifier
                        }
                    ),
                onFocus = {
                    lastFocusedEpisodeId = episode.id
                },
                onClick = { onOpenEpisode(episode.id) },
            )
        }
    }
}
