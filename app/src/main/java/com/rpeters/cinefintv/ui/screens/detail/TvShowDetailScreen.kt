package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.LocalPerformanceProfile

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvShowDetailScreen(
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvShowDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TvShowDetailUiState.Loading -> {
                LoadingState()
            }
            is TvShowDetailUiState.Error -> {
                ErrorState(message = state.message, onRetry = { viewModel.load() }, onBack = onBack)
            }
            is TvShowDetailUiState.Content -> {
                TvShowDetailContent(
                    show = state.show,
                    seasons = state.seasons,
                    cast = state.cast,
                    similarShows = state.similarShows,
                    onPlayEpisode = onPlayEpisode,
                    onOpenSeason = onOpenSeason,
                    onOpenShow = onOpenShow
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvShowDetailContent(
    show: TvShowDetailModel,
    seasons: List<SeasonModel>,
    cast: List<CastModel>,
    similarShows: List<SimilarMovieModel>,
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit
) {
    val spacing = LocalCinefinSpacing.current
    val performanceProfile = LocalPerformanceProfile.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop Image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(show.backdropUrl)
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
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1500f
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp)
                .padding(top = 80.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title and Metadata
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    show.yearRange?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyLarge)
                    }
                    show.officialRating?.let {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            colors = SurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Text(text = "${show.seasonCount} Seasons", style = MaterialTheme.typography.bodyLarge)
                    show.status?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyLarge)
                    }
                    show.rating?.let {
                        Text(text = "★ $it", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFFFD700))
                    }
                }
            }

            // Overview
            show.overview?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )
            }

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (show.nextUpEpisodeId != null) {
                    Button(
                        onClick = { onPlayEpisode(show.nextUpEpisodeId) },
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text("Play Next Up: ${show.nextUpTitle}")
                    }
                } else {
                    // Fallback to play from beginning or something if no next up
                    Button(
                        onClick = { 
                            // Try to play first episode of first season? 
                            // For now just show Play if seasons are available
                            if (seasons.isNotEmpty()) {
                                onOpenSeason(seasons.first().id)
                            }
                        },
                        contentPadding = ButtonDefaults.ContentPadding,
                        enabled = seasons.isNotEmpty()
                    ) {
                        Text("Seasons")
                    }
                }
                
                OutlinedButton(
                    onClick = { /* TODO: Trailer if available */ },
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Text("Trailer")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seasons
            if (seasons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(seasons) { season ->
                            TvMediaCard(
                                title = season.title,
                                subtitle = "${season.episodeCount} Episodes",
                                imageUrl = season.imageUrl,
                                unwatchedCount = season.unwatchedCount,
                                onClick = { onOpenSeason(season.id) }
                            )
                        }
                    }
                }
            }

            // Cast & Crew
            if (cast.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Cast & Crew",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(cast) { person ->
                            TvPersonCard(
                                name = person.name,
                                role = person.role,
                                imageUrl = person.imageUrl,
                                onClick = { /* TODO: Navigate to Person */ }
                            )
                        }
                    }
                }
            }

            // Similar Shows
            if (similarShows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "More Like This",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(similarShows) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                onClick = { onOpenShow(item.id) }
                            )
                        }
                    }
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
