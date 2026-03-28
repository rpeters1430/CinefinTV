package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.components.WatchStatus

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StuffLibraryScreen(
    onOpenItem: (LibraryCardModel) -> Unit,
    onPlayItem: (LibraryCardModel) -> Unit,
    viewModel: StuffLibraryViewModel = hiltViewModel()
) {
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    var selectedItem by remember { mutableStateOf<LibraryCardModel?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<LibraryCardModel?>(null) }
    val uiState = when (val refreshState = pagedItems.loadState.refresh) {
        is LoadState.Loading -> LibraryGridUiState.Loading
        is LoadState.Error -> LibraryGridUiState.Error(refreshState.error.message ?: "Unknown error")
        is LoadState.NotLoading -> {
            val items = List(pagedItems.itemCount) { index -> pagedItems[index] }.filterNotNull()
            if (items.isEmpty()) {
                LibraryGridUiState.Empty
            } else {
                LibraryGridUiState.Content(
                    items = items,
                    isAppending = pagedItems.loadState.append is LoadState.Loading,
                )
            }
        }
    }

    selectedItem?.let { item ->
        MediaActionDialog(
            title = item.title,
            actions = buildList {
                if (item.itemType.isPlayableLibraryVideo()) {
                    add(
                        MediaActionDialogItem(
                            label = "Play",
                            supportingText = "Start playback immediately.",
                            onClick = { onPlayItem(item) },
                        )
                    )
                }
                add(
                    MediaActionDialogItem(
                        label = "Open details",
                        supportingText = "Go to the item detail screen.",
                        onClick = { onOpenItem(item) },
                    )
                )
                if (item.itemType.isWatchToggleSupported()) {
                    add(
                        MediaActionDialogItem(
                            label = if (item.watchStatus == WatchStatus.WATCHED) "Mark unwatched" else "Mark watched",
                            supportingText = "Update the watched state for this item.",
                            onClick = {
                                if (item.watchStatus == WatchStatus.WATCHED) {
                                    viewModel.markUnwatched(item.id) { pagedItems.refresh() }
                                } else {
                                    viewModel.markWatched(item.id) { pagedItems.refresh() }
                                }
                            },
                        )
                    )
                }
                add(
                    MediaActionDialogItem(
                        label = "Delete",
                        supportingText = "Remove this item from the library.",
                        isDestructive = true,
                        onClick = { pendingDeleteItem = item },
                    )
                )
            },
            onDismissRequest = { selectedItem = null },
        )
    }

    pendingDeleteItem?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete ${item.title}?",
            message = "This will remove the item from your Jellyfin library.",
            onDismissRequest = { pendingDeleteItem = null },
            onConfirmDelete = {
                pendingDeleteItem = null
                selectedItem = null
                viewModel.deleteItem(item.id) { pagedItems.refresh() }
            },
        )
    }

    LibraryGridContent(
        uiState = uiState,
        errorTitle = "Failed to load collections",
        emptyTitle = "No collections found",
        columnCount = 3,
        aspectRatio = 16f / 9f,
        gridState = gridState,
        onOpenItem = onOpenItem,
        onItemMenuAction = { selectedItem = it },
        onRetry = { pagedItems.retry() },
    )
}

private fun String?.isPlayableLibraryVideo(): Boolean {
    return this.equals("Movie", ignoreCase = true) ||
        this.equals("Episode", ignoreCase = true) ||
        this.equals("Video", ignoreCase = true)
}

private fun String?.isWatchToggleSupported(): Boolean {
    return isPlayableLibraryVideo()
}
