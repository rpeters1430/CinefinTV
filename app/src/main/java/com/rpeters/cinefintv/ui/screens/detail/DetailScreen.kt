package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.components.TvMediaCard

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
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
            val isSeriesDetail = state.seasons.isNotEmpty()

            LaunchedEffect(state.isDeleted) {
                if (state.isDeleted) {
                    onBack()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
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

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item { Spacer(Modifier.fillParentMaxHeight(0.35f)) }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.displaySmall,
                            )

                            if (!item.subtitle.isNullOrBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (!item.overview.isNullOrBlank()) {
                                Text(
                                    text = item.overview,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (item.metaBadges.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    item.metaBadges.forEach { badge ->
                                        Surface(shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                text = badge,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 6.dp,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            if (item.infoRows.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    item.infoRows.forEach { infoRow ->
                                        Surface(shape = RoundedCornerShape(12.dp)) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Text(
                                                    text = infoRow.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    text = infoRow.value,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (!state.actionErrorMessage.isNullOrBlank()) {
                                Text(
                                    text = state.actionErrorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                state.playableItemId?.let { playableItemId ->
                                    Button(onClick = { onPlay(playableItemId) }) {
                                        Text(state.playButtonLabel)
                                    }
                                }

                                if (!isSeriesDetail) {
                                    if (state.isDeleting) {
                                        Surface(shape = RoundedCornerShape(12.dp)) {
                                            Text(
                                                text = "Deleting...",
                                                modifier = Modifier.padding(
                                                    horizontal = 18.dp,
                                                    vertical = 12.dp,
                                                ),
                                            )
                                        }
                                    } else if (state.isDeleteConfirmationVisible) {
                                        Button(onClick = viewModel::confirmDelete) {
                                            Text("Confirm Delete")
                                        }
                                        OutlinedButton(onClick = viewModel::cancelDelete) {
                                            Text("Cancel")
                                        }
                                    } else {
                                        OutlinedButton(onClick = viewModel::requestDelete) {
                                            Text("Delete")
                                        }
                                    }
                                }

                                if (!isSeriesDetail) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.dismissActionError()
                                            onBack()
                                        },
                                    ) {
                                        Text("Back")
                                    }
                                }
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
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(state.seasons, key = { it.id }) { season ->
                                        TvMediaCard(
                                            title = season.title,
                                            subtitle = season.subtitle,
                                            imageUrl = season.imageUrl,
                                            onClick = { onOpenItem(season.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.seasons.isEmpty() && episodes.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Episodes",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
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

                    if (state.related.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "More Like This",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
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
