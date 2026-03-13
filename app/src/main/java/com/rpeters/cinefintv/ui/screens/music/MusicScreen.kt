package com.rpeters.cinefintv.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import org.jellyfin.sdk.model.api.BaseItemDto

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicScreen(
    onPlayTrack: (AudioPlaybackRequest) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is MusicUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading music...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is MusicUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Music could not load",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { viewModel.loadGrid(state.viewType) }) {
                    Text("Retry")
                }
            }
        }

        is MusicUiState.Grid -> {
            MusicGridContent(
                state = state,
                onViewTypeChange = { viewModel.loadGrid(it) },
                onOpenAlbum = { viewModel.openAlbum(it) },
                onOpenArtist = { viewModel.openArtist(it) },
                imageUrl = { viewModel.imageUrl(it) },
            )
        }

        is MusicUiState.AlbumDetail -> {
            AlbumDetailContent(
                state = state,
                onBack = { viewModel.backToGrid() },
                onPlayTrack = { track ->
                    viewModel.buildPlaybackRequest(track, state.tracks)?.let(onPlayTrack)
                },
                imageUrl = { viewModel.imageUrl(it) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MusicGridContent(
    state: MusicUiState.Grid,
    onViewTypeChange: (MusicViewType) -> Unit,
    onOpenAlbum: (BaseItemDto) -> Unit,
    onOpenArtist: (BaseItemDto) -> Unit,
    imageUrl: (BaseItemDto) -> String?,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Music",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.viewType == MusicViewType.ALBUMS) {
                    Button(onClick = { onViewTypeChange(MusicViewType.ALBUMS) }) {
                        Text("Albums")
                    }
                    OutlinedButton(onClick = { onViewTypeChange(MusicViewType.ARTISTS) }) {
                        Text("Artists")
                    }
                } else {
                    OutlinedButton(onClick = { onViewTypeChange(MusicViewType.ALBUMS) }) {
                        Text("Albums")
                    }
                    Button(onClick = { onViewTypeChange(MusicViewType.ARTISTS) }) {
                        Text("Artists")
                    }
                }
            }
        }

        if (state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "No ${state.viewType.name.lowercase()} found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            gridItems(state.items, key = { it.id }) { item ->
                val yearStr = item.productionYear?.toString()
                if (state.viewType == MusicViewType.ALBUMS) {
                    TvMediaCard(
                        title = item.name ?: "Unknown Album",
                        subtitle = yearStr,
                        imageUrl = imageUrl(item),
                        onClick = { onOpenAlbum(item) },
                    )
                } else {
                    TvMediaCard(
                        title = item.name ?: "Unknown Artist",
                        subtitle = null,
                        imageUrl = imageUrl(item),
                        onClick = { onOpenArtist(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumDetailContent(
    state: MusicUiState.AlbumDetail,
    onBack: () -> Unit,
    onPlayTrack: (BaseItemDto) -> Unit,
    imageUrl: (BaseItemDto) -> String?,
) {
    val album = state.album
    val albumTitle = album.name ?: "Unknown Album"
    val albumYear = album.productionYear?.toString()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = albumTitle,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!albumYear.isNullOrBlank()) {
                    Text(
                        text = albumYear,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val artistName = album.albumArtist ?: album.artists?.firstOrNull()
                if (!artistName.isNullOrBlank()) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Back")
                }
            }
        }

        if (state.tracks.isEmpty()) {
            item {
                Text(
                    text = "No tracks found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            listItems(state.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onPlay = { onPlayTrack(track) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackRow(
    track: BaseItemDto,
    onPlay: () -> Unit,
) {
    val trackNumber = track.indexNumber
    val title = track.name ?: "Unknown Track"
    val durationTicks = track.runTimeTicks
    val durationText = if (durationTicks != null) {
        val totalSeconds = durationTicks / 10_000_000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        "%d:%02d".format(minutes, seconds)
    } else {
        null
    }

    Button(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trackNumber != null) {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (!durationText.isNullOrBlank()) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
