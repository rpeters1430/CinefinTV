@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.utils.formatMs

@Composable
fun EpisodeDetailScreen(
    onPlayEpisode: (String, Long?) -> Unit,
    onBack: () -> Unit,
    viewModel: EpisodeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is EpisodeDetailUiState.Loading -> DetailLoadingState()
            is EpisodeDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is EpisodeDetailUiState.Content -> EpisodeDetailContent(
                episode = state.episode,
                chapters = state.chapters,
                onPlayEpisode = onPlayEpisode,
            )
        }
    }
}

@Composable
private fun EpisodeDetailContent(
    episode: EpisodeDetailModel,
    chapters: List<ChapterModel>,
    onPlayEpisode: (String, Long?) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val primaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val firstContentFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    var lastFocusedChapterId by rememberSaveable { mutableStateOf<String?>(chapters.firstOrNull()?.id) }

    LaunchedEffect(episode.id) {
        listState.scrollToItem(0)
        primaryActionFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(
                backdropUrl = episode.backdropUrl,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) scope.launch { listState.scrollToItem(0) } },
            ) {
                DetailGlassPanel(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                ) {
                    DetailChipRow(
                        labels = buildList {
                            add("Episode")
                            episode.episodeCode?.let { add(it) }
                            episode.year?.let { add("$it") }
                            episode.duration?.let { add(it) }
                            if (episode.isWatched) add("Watched")
                        }
                    )
                    episode.seriesName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    episode.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                        )
                    }
                    episode.playbackProgress?.let {
                        DetailProgressLabel(progress = it)
                    }
                    DetailActionRow(
                        primaryLabel = when {
                            episode.playbackProgress != null -> "Resume Episode"
                            episode.isWatched -> "Play Again"
                            else -> "Play Episode"
                        },
                        onPrimaryClick = { onPlayEpisode(episode.id, null) },
                        secondaryLabel = if (chapters.isNotEmpty()) "Jump to Chapter" else null,
                        onSecondaryClick = if (chapters.isNotEmpty()) {
                            { onPlayEpisode(episode.id, chapters.first().positionMs) }
                        } else {
                            null
                        },
                        primaryFocusRequester = primaryActionFocusRequester,
                        primaryDownFocusRequester = if (chapters.isNotEmpty()) firstContentFocusRequester else null,
                    )
                }
            }
        }

        if (chapters.isNotEmpty()) {
            item {
                DetailContentSection(title = "Chapters", eyebrow = "${chapters.size} markers") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(chapters, key = { it.id }) { chapter ->
                            TvMediaCard(
                                title = chapter.name,
                                subtitle = formatMs(chapter.positionMs),
                                imageUrl = chapter.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 240.dp,
                                modifier = Modifier
                                    .then(
                                        if (chapter.id == lastFocusedChapterId) Modifier.focusRequester(firstContentFocusRequester) else Modifier
                                    )
                                    .then(
                                        if (chapter.id == chapters.firstOrNull()?.id) {
                                            Modifier.focusProperties { up = primaryActionFocusRequester }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedChapterId = chapter.id },
                                onClick = { onPlayEpisode(episode.id, chapter.positionMs) },
                            )
                        }
                    }
                }
            }
        }
    }
}
