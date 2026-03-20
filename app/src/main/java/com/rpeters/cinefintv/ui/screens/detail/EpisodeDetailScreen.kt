@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
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
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocusRequester.requestFocus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailHeroBox(backdropUrl = episode.backdropUrl) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        episode.episodeCode?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        episode.year?.let {
                            Text(text = "$it", style = MaterialTheme.typography.bodyLarge)
                        }
                        episode.duration?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (episode.isWatched) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                colors = SurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            ) {
                                Text(
                                    text = "Watched",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    episode.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Button(
                        onClick = { onPlayEpisode(episode.id, null) },
                        modifier = Modifier.focusRequester(playFocusRequester),
                    ) {
                        Text("Play")
                    }
                }
            }
        }

        if (chapters.isNotEmpty()) {
            item {
                DetailContentSection(title = "Chapters") {
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
                                onClick = { onPlayEpisode(episode.id, chapter.positionMs) },
                            )
                        }
                    }
                }
            }
        }
    }
}
