@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

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
                    }
                }
            )
        }

        item {
            DetailHeroBox(
                backdropUrl = season.backdropUrl,
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
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
                            }
                        )

                        Text(
                            text = season.seriesName ?: "Season",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )

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
                    }
                }
            }
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
