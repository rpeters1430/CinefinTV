@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.WatchStatus
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val anchorFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val episodeGridEntryRequester = remember { FocusRequester() }
    
    val nextEpisode = remember(episodes) {
        episodes.firstOrNull { !it.isWatched } ?: episodes.firstOrNull()
    }
    val resumableEpisode = remember(episodes) {
        episodes.firstOrNull { (it.playbackProgress ?: 0f) > 0f && !it.isWatched }
    }
    
    var focusedEpisode by remember { mutableStateOf<EpisodeModel?>(null) }
    var lastFocusedEpisodeId by rememberSaveable { mutableStateOf<String?>(episodes.firstOrNull()?.id) }

    LaunchedEffect(season.id) {
        anchorFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailAnchor(
                focusRequester = anchorFocusRequester,
                onFocused = {
                    scope.launch {
                        listState.scrollToItem(0)
                        primaryActionFocusRequester.requestFocus()
                        focusedEpisode = null
                    }
                }
            )
        }

        item {
            val displayBackdrop = focusedEpisode?.imageUrl ?: season.backdropUrl
            val displayTitle = focusedEpisode?.title ?: season.title
            val displayOverview = focusedEpisode?.overview ?: season.overview
            val displayEyebrow = if (focusedEpisode != null) {
                focusedEpisode?.episodeCode ?: "Episode ${focusedEpisode?.number}"
            } else {
                season.seriesName ?: "Season"
            }

            DetailHeroBox(
                backdropUrl = displayBackdrop,
                modifier = Modifier.onFocusChanged { 
                    if (it.hasFocus && listState.firstVisibleItemIndex == 0) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DetailPosterArt(
                        imageUrl = if (focusedEpisode != null) null else season.posterUrl,
                        title = displayTitle,
                        modifier = Modifier
                            .width(172.dp)
                            .height(258.dp),
                    )
                    DetailGlassPanel(
                        modifier = Modifier.fillMaxWidth(0.66f)
                    ) {
                        DetailChipRow(
                            labels = buildList {
                                if (focusedEpisode != null) {
                                    add("Episode")
                                    focusedEpisode?.duration?.let { add(it) }
                                } else {
                                    add("Season")
                                    add("${episodes.size} episodes")
                                }
                            }
                        )
                        
                        Text(
                            text = displayEyebrow,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        
                        displayOverview?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }
                        
                        if (focusedEpisode == null) {
                            resumableEpisode?.playbackProgress?.let {
                                DetailProgressLabel(progress = it)
                            }
                            if (nextEpisode != null) {
                                DetailActionRow(
                                    primaryLabel = if (resumableEpisode != null) "Resume Episode" else "Continue Watching",
                                    onPrimaryClick = {
                                        onOpenEpisode(resumableEpisode?.id ?: nextEpisode.id)
                                    },
                                    secondaryLabel = episodes.firstOrNull()?.let { "Start From Episode 1" },
                                    onSecondaryClick = episodes.firstOrNull()?.let { firstEpisode ->
                                        { onOpenEpisode(firstEpisode.id) }
                                    },
                                    primaryFocusRequester = primaryActionFocusRequester,
                                    primaryDownFocusRequester = if (episodes.isNotEmpty()) episodeGridEntryRequester else null,
                                )
                            }
                        } else {
                            DetailActionRow(
                                primaryLabel = if ((focusedEpisode?.playbackProgress ?: 0f) > 0f) "Resume Episode" else "Play Episode",
                                onPrimaryClick = { onOpenEpisode(focusedEpisode!!.id) },
                                primaryFocusRequester = null, // Handled by grid focus
                            )
                        }
                    }
                }
            }
        }

        item {
            DetailContentSection(
                title = "Episodes",
                eyebrow = "${episodes.count { !it.isWatched }} unwatched",
                modifier = Modifier.padding(top = 0.dp),
            ) {}
        }

        // 5-column grid for smaller cards
        val columns = 5
        val rows = (episodes.size + columns - 1) / columns
        items(rows) { rowIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (columnIndex in 0 until columns) {
                    val episodeIndex = rowIndex * columns + columnIndex
                    if (episodeIndex < episodes.size) {
                        val episode = episodes[episodeIndex]
                        TvMediaCard(
                            title = episode.title,
                            subtitle = episode.episodeCode ?: "E${episode.number}",
                            imageUrl = episode.imageUrl,
                            aspectRatio = 16f / 9f,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (episode.id == lastFocusedEpisodeId) Modifier.focusRequester(episodeGridEntryRequester) else Modifier
                                )
                                .then(
                                    if (episodeIndex < columns) {
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
                            onFocus = { 
                                lastFocusedEpisodeId = episode.id 
                                focusedEpisode = episode
                            },
                            onClick = { onOpenEpisode(episode.id) },
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
