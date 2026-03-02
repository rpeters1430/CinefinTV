package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.rpeters.cinefintv.ui.navigation.NavRoutes

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
            var selectedSeasonIndex by remember(state.seasons) { mutableIntStateOf(0) }
            val selectedSeason = state.seasons.getOrNull(selectedSeasonIndex)
            val episodes = selectedSeason?.let { state.episodesBySeasonId[it.id].orEmpty() }.orEmpty()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    if (item.backdropUrl != null) {
                        AsyncImage(
                            model = item.backdropUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.displaySmall,
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.genres.isNotEmpty()) {
                            Text(
                                text = item.genres.joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                if (listOfNotNull(item.rating, item.year, item.runtime).isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(
                                listOfNotNull(item.rating, item.year, item.runtime),
                                key = { it },
                            ) { metadata ->
                                Surface(shape = RoundedCornerShape(999.dp)) {
                                    Text(
                                        text = metadata,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (!item.overview.isNullOrBlank()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = item.overview,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                if (state.seasons.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Seasons",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.seasons, key = { it.id }) { season ->
                                    val isSelected = season.id == selectedSeason?.id
                                    if (isSelected) {
                                        Button(onClick = {}) {
                                            Text(season.title)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                selectedSeasonIndex = state.seasons.indexOfFirst { it.id == season.id }
                                                    .coerceAtLeast(0)
                                            },
                                        ) {
                                            Text(season.title)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (episodes.isNotEmpty()) {
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
                                            onClick = { onNavigate(NavRoutes.player(episode.id)) },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "No episodes available for this season.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

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

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}
