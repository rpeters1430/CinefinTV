@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.TopLevelDestinationFocus
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.ImmersiveBackground
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection
import com.rpeters.cinefintv.ui.screens.music.AudioPlaybackRequest

@Composable
fun PlaylistDetailScreen(
    itemId: String,
    onOpenItem: (String, String?) -> Unit,
    onPlayTrack: (AudioPlaybackRequest) -> Unit,
    onPlayVideo: (itemId: String, queueIds: List<String>) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.init(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    val primaryActionFocusRequester = remember { FocusRequester() }
    val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocusRequester)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val state = uiState) {
            is PlaylistDetailUiState.Loading -> DetailLoadingState()
            is PlaylistDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is PlaylistDetailUiState.Content -> PlaylistContent(
                playlist = state.playlist,
                items = state.items,
                onOpenItem = onOpenItem,
                onPlayTrack = onPlayTrack,
                onPlayVideo = onPlayVideo,
                viewModel = viewModel,
                onBack = onBack,
                primaryActionFocusRequester = primaryActionFocusRequester,
                destinationFocus = destinationFocus,
            )
        }
    }
}

private fun String?.isPlayableVideoType(): Boolean =
    equals("Movie", ignoreCase = true) || equals("Episode", ignoreCase = true) || equals("Video", ignoreCase = true)

@Composable
private fun PlaylistContent(
    playlist: PlaylistDetailModel,
    items: List<PlaylistItemModel>,
    onOpenItem: (String, String?) -> Unit,
    onPlayTrack: (AudioPlaybackRequest) -> Unit,
    onPlayVideo: (itemId: String, queueIds: List<String>) -> Unit,
    viewModel: PlaylistDetailViewModel,
    onBack: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    destinationFocus: TopLevelDestinationFocus,
) {
    val overviewFocusRequester = remember { FocusRequester() }
    val gridEntryFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(items.firstOrNull()?.id) }
    var showDeleteDialog by remember(playlist.id) { mutableStateOf(false) }
    var didInitialFocus by remember { mutableStateOf(false) }
    val chromeFocusController = LocalAppChromeFocusController.current

    var focusedBackdropUrl by remember(playlist.backdropUrl) { mutableStateOf(playlist.backdropUrl) }

    val allAudio = items.isNotEmpty() && items.all { it.isAudio }
    val queueIds = remember(items) { items.map { it.id } }
    val videoQueueIds = remember(items) { items.filter { it.itemType.isPlayableVideoType() }.map { it.id } }

    val factItems = remember(playlist, items) {
        listOf(DetailLabeledMetaItem(Icons.Default.MusicNote, "Items", items.size.toString()))
    }
    val chips = remember(items) {
        listOf("Playlist", "${items.size} items")
    }

    LaunchedEffect(playlist.id) {
        if (!didInitialFocus) {
            primaryActionFocusRequester.requestFocus()
            didInitialFocus = true
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete ${playlist.title}?",
            message = "This will remove the playlist from your Jellyfin library.",
            onDismissRequest = { showDeleteDialog = false },
            onConfirmDelete = {
                showDeleteDialog = false
                chromeFocusController?.shouldRestoreFocusToContent = true
                viewModel.deletePlaylist(onBack)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ImmersiveBackground(backdropUrl = focusedBackdropUrl)

        Column(modifier = Modifier.fillMaxSize()) {
            CinematicHero(
                backdropUrl = playlist.backdropUrl,
                logoUrl = null,
                title = playlist.title,
                eyebrow = "Playlist · ${items.size} items",
                ratingText = null,
                genres = chips,
                primaryActionLabel = if (items.isEmpty()) "Playlist" else "▶ Play all",
                onPrimaryAction = {
                    if (items.isNotEmpty()) {
                        val first = items.first()
                        when {
                            allAudio -> onPlayTrack(AudioPlaybackRequest(trackId = first.id, queueIds = queueIds))
                            first.itemType.isPlayableVideoType() -> onPlayVideo(first.id, videoQueueIds)
                            else -> onOpenItem(first.id, first.itemType)
                        }
                    }
                },
                secondaryActions = listOf("Delete" to { showDeleteDialog = true }),
                primaryActionFocusRequester = primaryActionFocusRequester,
                primaryActionDownFocusRequester = overviewFocusRequester,
                drawerFocusRequester = destinationFocus.drawerFocusRequester,
            )

            DetailOverviewSection(
                title = playlist.title,
                posterUrl = playlist.posterUrl,
                description = playlist.overview ?: "Browse everything in this playlist below.",
                factItems = factItems,
                chips = chips,
                focusRequester = overviewFocusRequester,
                upFocusRequester = primaryActionFocusRequester,
                modifier = Modifier.padding(top = 16.dp),
            )

            DetailContentSection(
                title = "Items",
                eyebrow = "${items.size} in playlist",
                icon = Icons.Default.MusicNote,
            ) {}

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No items in this playlist yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.playlistItemId }) { index, item ->
                        TvMediaCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            aspectRatio = 16f / 9f,
                            modifier = Modifier
                                .then(
                                    if (item.id == lastFocusedItemId) Modifier.focusRequester(gridEntryFocusRequester) else Modifier
                                )
                                .then(
                                    if (item.id == items.firstOrNull()?.id) {
                                        Modifier.focusProperties { up = overviewFocusRequester }
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    destinationFocus.drawerEscapeModifier(isLeftEdge = index % 4 == 0)
                                ),
                            watchStatus = item.watchStatus,
                            playbackProgress = item.playbackProgress,
                            unwatchedCount = item.unwatchedCount,
                            onFocus = {
                                lastFocusedItemId = item.id
                                focusedBackdropUrl = item.imageUrl ?: playlist.backdropUrl
                            },
                            onClick = {
                                when {
                                    item.isAudio -> onPlayTrack(AudioPlaybackRequest(trackId = item.id, queueIds = queueIds))
                                    item.itemType.isPlayableVideoType() -> onPlayVideo(item.id, videoQueueIds)
                                    else -> onOpenItem(item.id, item.itemType)
                                }
                            },
                            onMenuAction = { viewModel.removeItem(item.playlistItemId) },
                        )
                    }
                }
            }
        }
    }
}
