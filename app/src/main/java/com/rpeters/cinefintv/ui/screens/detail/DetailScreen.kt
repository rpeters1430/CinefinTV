package com.rpeters.cinefintv.ui.screens.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.components.TvMediaCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is DetailUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Loading details...",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        is DetailUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Detail could not load",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = viewModel::load) {
                        Text("Retry")
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }

        is DetailUiState.Content -> {
            val item = state.item
            val episodes = state.episodesBySeasonId.values.flatten()

            Box(modifier = Modifier.fillMaxSize()) {
                // Backdrop fills the entire screen background
                if (item.backdropUrl != null) {
                    AsyncImage(
                        model = item.backdropUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }

                // Gradient overlay darkening toward the bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Black.copy(alpha = 0.55f),
                                    0.75f to Color.Black.copy(alpha = 0.88f),
                                    1.0f to Color.Black,
                                ),
                            ),
                        ),
                )

                // Scrollable content overlaid on the backdrop
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 0.dp),
                ) {
                    // Spacer so backdrop shows prominently at the top
                    item { Spacer(Modifier.fillParentMaxHeight(0.35f)) }

                    // Title / metadata section
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val metaParts = listOfNotNull(
                                item.genres.joinToString("/").ifBlank { null },
                                item.year,
                                item.runtime,
                            )
                            if (!item.rating.isNullOrBlank() || metaParts.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    if (!item.rating.isNullOrBlank()) {
                                        Surface(shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = item.rating,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp,
                                                ),
                                            )
                                        }
                                    }
                                    if (metaParts.isNotEmpty()) {
                                        Text(
                                            text = metaParts.joinToString(" • "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.displaySmall,
                            )

                            if (!item.overview.isNullOrBlank()) {
                                Text(
                                    text = item.overview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { onPlay(item.id) }) {
                                    Text("Play")
                                }
                                OutlinedButton(onClick = onBack) {
                                    Text("Back")
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }

                    // Season cards (Series detail) — each card navigates to that season's detail
                    if (state.seasons.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Seasons",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(state.seasons, key = { it.id }) { season ->
                                        TvMediaCard(
                                            title = season.title,
                                            imageUrl = season.imageUrl,
                                            onClick = { onOpenItem(season.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Episode cards (Season detail — seasons list is empty but episodes exist)
                    if (state.seasons.isEmpty() && episodes.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Episodes",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(episodes, key = { it.id }) { episode ->
                                        TvMediaCard(
                                            title = episode.title,
                                            subtitle = episode.subtitle,
                                            imageUrl = episode.imageUrl,
                                            onClick = { onOpenItem(episode.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Related / More Like This
                    if (state.related.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "More Like This",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(state.related, key = { it.id }) { related ->
                                        TvMediaCard(
                                            title = related.title,
                                            subtitle = related.subtitle,
                                            imageUrl = related.imageUrl,
                                            onClick = { onOpenItem(related.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}
