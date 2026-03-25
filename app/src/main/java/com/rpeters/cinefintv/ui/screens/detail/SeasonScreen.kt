@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.flow.first

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
    val spacing = LocalCinefinSpacing.current
    val listState = rememberLazyListState()
    val primaryActionFocusRequester = remember { FocusRequester() }

    val resumeEpisode = remember(episodes) {
        episodes.firstOrNull { (it.playbackProgress ?: 0f) > 0f && !it.isWatched }
            ?: episodes.firstOrNull { !it.isWatched }
    }
    
    val factItems = remember(season, episodes) {
        buildList {
            season.seriesName?.let {
                add(DetailLabeledMetaItem(Icons.Default.Subscriptions, "Series", it))
            }
            add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Episodes", episodes.size.toString()))
            resumeEpisode?.episodeCode?.let {
                add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Continue With", it))
            }
        }
    }

    LaunchedEffect(season.id) {
        listState.scrollToItem(0)
        snapshotFlow { listState.isScrollInProgress }
            .first { !it }
        try { primaryActionFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.gutter * 2),
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
            DetailOverviewSection(
                title = season.title,
                posterUrl = season.posterUrl,
                description = season.overview.orEmpty(),
                factItems = factItems,
                chips = emptyList(),
                modifier = Modifier.padding(top = spacing.rowGap),
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
                isNext = episode.id == resumeEpisode?.id,
                onClick = { onOpenEpisode(episode.id) },
            )
        }
    }
}
