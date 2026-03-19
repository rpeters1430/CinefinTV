package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.LocalPerformanceProfile

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonScreen(
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SeasonUiState.Loading -> {
                LoadingState()
            }
            is SeasonUiState.Error -> {
                ErrorState(message = state.message, onRetry = { viewModel.load() }, onBack = onBack)
            }
            is SeasonUiState.Content -> {
                SeasonContent(
                    season = state.season,
                    episodes = state.episodes,
                    onOpenEpisode = onOpenEpisode,
                    onBack = onBack
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonContent(
    season: SeasonDetailModel,
    episodes: List<EpisodeModel>,
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit
) {
    val spacing = LocalCinefinSpacing.current
    val performanceProfile = LocalPerformanceProfile.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop Image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(season.backdropUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp)
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            
            // Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Poster
                Card(
                    onClick = { /* No action on poster */ },
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(2f / 3f),
                    shape = CardDefaults.shape(MaterialTheme.shapes.medium)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(season.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = season.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = season.seriesName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = season.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    season.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Episodes Grid
            Text(
                text = "Episodes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(episodes) { episode ->
                    TvMediaCard(
                        title = episode.title,
                        subtitle = episode.episodeCode ?: "Episode ${episode.number}",
                        imageUrl = episode.imageUrl,
                        aspectRatio = 16f / 9f,
                        watchStatus = if (episode.isWatched) WatchStatus.WATCHED else if ((episode.playbackProgress ?: 0f) > 0f) WatchStatus.IN_PROGRESS else WatchStatus.NONE,
                        playbackProgress = episode.playbackProgress,
                        onClick = { onOpenEpisode(episode.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
    }
}
