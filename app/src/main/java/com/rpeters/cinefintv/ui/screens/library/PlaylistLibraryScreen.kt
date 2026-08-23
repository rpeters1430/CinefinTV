package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinDialogActions
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface
import com.rpeters.cinefintv.ui.components.CinefinTextInputField
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistLibraryScreen(
    onOpenItem: (LibraryCardModel) -> Unit,
    viewModel: PlaylistLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var selectedItem by remember { mutableStateOf<LibraryCardModel?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<LibraryCardModel?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.actionErrors.collect { message -> actionError = message }
    }

    val gridUiState = when (val state = uiState) {
        PlaylistLibraryUiState.Loading -> LibraryGridUiState.Loading
        is PlaylistLibraryUiState.Error -> LibraryGridUiState.Error(state.message)
        is PlaylistLibraryUiState.Content -> {
            if (state.items.isEmpty()) LibraryGridUiState.Empty else LibraryGridUiState.Content(state.items)
        }
    }

    selectedItem?.let { item ->
        MediaActionDialog(
            title = item.title,
            actions = listOf(
                MediaActionDialogItem(
                    label = "Open playlist",
                    supportingText = "View and play items in this playlist.",
                    onClick = { onOpenItem(item) },
                ),
                MediaActionDialogItem(
                    label = "Delete",
                    supportingText = "Remove this playlist from your Jellyfin library.",
                    isDestructive = true,
                    onClick = { pendingDeleteItem = item },
                ),
            ),
            onDismissRequest = { selectedItem = null },
        )
    }

    pendingDeleteItem?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete ${item.title}?",
            message = "This will remove the playlist from your Jellyfin library.",
            onDismissRequest = { pendingDeleteItem = null },
            onConfirmDelete = {
                pendingDeleteItem = null
                selectedItem = null
                viewModel.deletePlaylist(item.id) { viewModel.load() }
            },
        )
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismissRequest = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                viewModel.createPlaylist(name) { viewModel.load() }
            },
        )
    }

    actionError?.let { message ->
        PlaylistActionErrorDialog(
            message = message,
            onDismissRequest = { actionError = null },
        )
    }

    LibraryGridContent(
        uiState = gridUiState,
        errorTitle = "Failed to load playlists",
        emptyTitle = "No playlists found",
        columnCount = 3,
        aspectRatio = 16f / 9f,
        gridState = gridState,
        onOpenItem = onOpenItem,
        onItemMenuAction = { selectedItem = it },
        onRetry = { viewModel.load() },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistActionErrorDialog(
    message: String,
    onDismissRequest: () -> Unit,
) {
    CinefinDialogSurface(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CinefinDialogActions(
                dismissLabel = null,
                confirmLabel = "OK",
                onDismiss = null,
                onConfirm = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreatePlaylistDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    CinefinDialogSurface(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "New playlist",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            CinefinTextInputField(
                label = "Playlist name",
                value = name,
                onValueChange = { name = it },
                placeholder = "My playlist",
            )
            CinefinDialogActions(
                dismissLabel = "Cancel",
                confirmLabel = "Create",
                confirmEnabled = name.isNotBlank(),
                onDismiss = onDismissRequest,
                onConfirm = { onCreate(name.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
