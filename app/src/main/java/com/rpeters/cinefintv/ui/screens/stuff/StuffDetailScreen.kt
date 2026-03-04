package com.rpeters.cinefintv.ui.screens.stuff

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.components.TvMediaCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StuffDetailScreen(
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StuffDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is StuffDetailUiState.Loading -> Text(
            text = "Loading Stuff details...",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(48.dp),
        )

        is StuffDetailUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Stuff detail could not load", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = viewModel::load) { Text("Retry") }
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        }

        is StuffDetailUiState.Content -> {
            val item = state.item
            Box(modifier = Modifier.fillMaxSize()) {
                if (item.backdropUrl != null) {
                    AsyncImage(
                        model = item.backdropUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black,
                                ),
                            ),
                        ),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 48.dp),
                ) {
                    item { Spacer(Modifier.fillParentMaxHeight(0.35f)) }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item.metadataLine?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(item.title, style = MaterialTheme.typography.displaySmall)
                            item.overview?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { onPlay(item.id) }) { Text("Play") }
                                OutlinedButton(onClick = onBack) { Text("Back") }
                            }
                        }
                    }

                    if (state.moreFromStuff.isNotEmpty()) {
                        item { Spacer(Modifier.height(28.dp)) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("More Stuff", style = MaterialTheme.typography.titleLarge)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(state.moreFromStuff, key = { it.id }) { related ->
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
                }
            }
        }
    }
}
