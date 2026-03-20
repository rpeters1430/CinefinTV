@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.WatchStatus

@Composable
fun SeasonScreen(
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

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
    val primaryActionFocusRequester = remember { FocusRequester() }
    val episodeGridEntryRequester = remember { FocusRequester() }
    val nextEpisodeId = remember(episodes) {
        episodes.firstOrNull { !it.isWatched }?.id ?: episodes.firstOrNull()?.id
    }
    val resumableEpisode = remember(episodes) {
        episodes.firstOrNull { (it.playbackProgress ?: 0f) > 0f && !it.isWatched }
    }
    var lastFocusedEpisodeId by rememberSaveable { mutableStateOf<String?>(episodes.firstOrNull()?.id) }

    LaunchedEffect(season.id) {
        primaryActionFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeroBox(backdropUrl = season.backdropUrl) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                DetailPosterArt(
                    imageUrl = season.posterUrl,
                    title = season.title,
                    modifier = Modifier
                        .width(172.dp)
                        .height(258.dp),
                )
                DetailGlassPanel(
                    modifier = Modifier.fillMaxWidth(0.66f)
                ) {
                    DetailChipRow(
                        labels = buildList {
                            add("Season")
                            add("${episodes.size} episodes")
                            if (nextEpisodeId != null) add("Ready to continue")
                        }
                    )
                    season.seriesName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Text(
                        text = season.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    season.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    resumableEpisode?.playbackProgress?.let {
                        DetailProgressLabel(progress = it)
                    }
                    if (nextEpisodeId != null) {
                        DetailActionRow(
                            primaryLabel = if (resumableEpisode != null) "Resume Episode" else "Continue Watching",
                            onPrimaryClick = {
                                onOpenEpisode(resumableEpisode?.id ?: nextEpisodeId)
                            },
                            secondaryLabel = episodes.firstOrNull()?.let { "Start From Episode 1" },
                            onSecondaryClick = episodes.firstOrNull()?.let { firstEpisode ->
                                { onOpenEpisode(firstEpisode.id) }
                            },
                            primaryFocusRequester = primaryActionFocusRequester,
                            primaryDownFocusRequester = if (episodes.isNotEmpty()) episodeGridEntryRequester else null,
                        )
                    }
                }
            }
        }

        DetailContentSection(
            title = "Episodes",
            eyebrow = "${episodes.count { !it.isWatched }} unwatched",
            modifier = Modifier.padding(top = 0.dp),
        ) {}

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(episodes) { episode ->
                TvMediaCard(
                    title = episode.title,
                    subtitle = episode.episodeCode ?: episode.number?.let { "Episode $it" },
                    imageUrl = episode.imageUrl,
                    aspectRatio = 16f / 9f,
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
                    watchStatus = when {
                        episode.isWatched -> WatchStatus.WATCHED
                        (episode.playbackProgress ?: 0f) > 0f -> WatchStatus.IN_PROGRESS
                        else -> WatchStatus.NONE
                    },
                    playbackProgress = episode.playbackProgress,
                    onFocus = { lastFocusedEpisodeId = episode.id },
                    onClick = { onOpenEpisode(episode.id) },
                )
            }
        }
    }
}
