package com.rpeters.cinefintv.ui.screens.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.RegisterPrimaryContentFocusRequester
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import org.jellyfin.sdk.model.api.BaseItemDto
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicScreen(
    onPlayTrack: (AudioPlaybackRequest) -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val gridState = rememberLazyGridState()
    val albumDetailListState = rememberLazyListState()
    val retryFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = uiState is MusicUiState.AlbumDetail) {
        viewModel.backToGrid()
    }

    when (val state = uiState) {
        is MusicUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                expressiveColors.backgroundTop,
                                expressiveColors.backgroundBottom,
                            ),
                        ),
                    ),
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
            RegisterPrimaryContentFocusRequester(retryFocusRequester)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                expressiveColors.backgroundTop,
                                expressiveColors.backgroundBottom,
                            ),
                        ),
                    )
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
                Button(
                    onClick = { viewModel.loadGrid(state.viewType) },
                    modifier = Modifier.focusRequester(retryFocusRequester),
                ) {
                    Text("Retry")
                }
            }
        }

        is MusicUiState.Grid -> {
            MusicGridContent(
                state = state,
                gridState = gridState,
                onViewTypeChange = { viewModel.loadGrid(it) },
                onOpenAlbum = { viewModel.openAlbum(it) },
                onOpenArtist = { viewModel.openArtist(it) },
                imageUrl = { viewModel.imageUrl(it) },
            )
        }

        is MusicUiState.AlbumDetail -> {
            AlbumDetailContent(
                state = state,
                listState = albumDetailListState,
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
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onViewTypeChange: (MusicViewType) -> Unit,
    onOpenAlbum: (BaseItemDto) -> Unit,
    onOpenArtist: (BaseItemDto) -> Unit,
    imageUrl: (BaseItemDto) -> String?,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val primaryContentRequester = remember { FocusRequester() }
    val chromeFocusController = LocalAppChromeFocusController.current
    val navUpRequester = chromeFocusController?.topNavFocusRequester

    RegisterPrimaryContentFocusRequester(primaryContentRequester)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        expressiveColors.backgroundTop,
                        expressiveColors.backgroundBottom,
                    ),
                ),
            ),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.viewType == MusicViewType.ALBUMS) {
                    Button(
                        onClick = { onViewTypeChange(MusicViewType.ALBUMS) },
                        modifier = Modifier
                            .focusRequester(primaryContentRequester)
                            .focusProperties {
                                navUpRequester?.let { up = it }
                            }
                    ) {
                        Text("Albums")
                    }
                    OutlinedButton(onClick = { onViewTypeChange(MusicViewType.ARTISTS) }) {
                        Text("Artists")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onViewTypeChange(MusicViewType.ALBUMS) },
                        modifier = Modifier
                            .focusRequester(primaryContentRequester)
                            .focusProperties {
                                navUpRequester?.let { up = it }
                            }
                    ) {
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBack: () -> Unit,
    onPlayTrack: (BaseItemDto) -> Unit,
    imageUrl: (BaseItemDto) -> String?,
) {
    val album = state.album
    val albumTitle = album.name ?: "Unknown Album"
    val albumYear = album.productionYear?.toString()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val primaryContentRequester = remember { FocusRequester() }
    val chromeFocusController = LocalAppChromeFocusController.current
    val navUpRequester = chromeFocusController?.topNavFocusRequester

    RegisterPrimaryContentFocusRequester(primaryContentRequester)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        expressiveColors.backgroundTop,
                        expressiveColors.backgroundBottom,
                    ),
                ),
            ),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.74f),
                ),
                border = androidx.tv.material3.Border(
                    border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.6f)),
                ),
                tonalElevation = 2.dp,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    CinefinShelfTitle(
                        title = albumTitle,
                        eyebrow = album.albumArtist ?: album.artists?.firstOrNull() ?: "Album Detail",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = listOfNotNull(albumYear, album.albumArtist ?: album.artists?.firstOrNull())
                            .joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .focusRequester(primaryContentRequester)
                            .focusProperties {
                                navUpRequester?.let { up = it }
                            },
                    ) {
                        Text("Back")
                    }
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

    ListItem(
        selected = false,
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            if (trackNumber != null) {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailingContent = {
            if (!durationText.isNullOrBlank()) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MusicHero(
    viewType: MusicViewType,
    count: Int,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val icon = if (viewType == MusicViewType.ALBUMS) Icons.Default.Album else Icons.Default.GraphicEq

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(
                containerColor = expressiveColors.chromeSurface.copy(alpha = 0.74f),
            ),
            border = androidx.tv.material3.Border(
                border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.6f)),
            ),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.tv.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = expressiveColors.titleAccent,
                )
                Text(
                    text = if (viewType == MusicViewType.ALBUMS) "Albums" else "Artists",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (viewType == MusicViewType.ALBUMS) {
                        "Browse albums with cover-forward focus states."
                    } else {
                        "Browse artists with a richer catalog view."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$count ${viewType.name.lowercase()} loaded",
                    style = MaterialTheme.typography.labelMedium,
                    color = expressiveColors.titleAccent,
                )
            }
        }
    }
}
