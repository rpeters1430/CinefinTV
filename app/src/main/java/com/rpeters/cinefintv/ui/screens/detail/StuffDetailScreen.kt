@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection

@Composable
fun StuffDetailScreen(
    onOpenItem: (String, String?) -> Unit,
    onPlayItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StuffDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is StuffDetailUiState.Loading -> DetailLoadingState()
            is StuffDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is StuffDetailUiState.Content -> {
                if (state.stuff.isCollection) {
                    StuffCollectionContent(
                        stuff = state.stuff,
                        items = state.items,
                        onOpenItem = onOpenItem,
                    )
                } else {
                    StuffVideoContent(
                        stuff = state.stuff,
                        onPlayItem = onPlayItem,
                        onBack = onBack,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun StuffVideoContent(
    stuff: StuffDetailModel,
    onPlayItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: StuffDetailViewModel,
) {
    val primaryActionFocusRequester = remember { FocusRequester() }
    val overviewFocusRequester = remember { FocusRequester() }
    var showDeleteDialog by remember(stuff.id) { mutableStateOf(false) }
    val factItems = remember(stuff) {
        buildList {
            stuff.type?.let {
                add(DetailLabeledMetaItem(Icons.Default.Category, "Type", it))
            }
            stuff.playbackProgress?.let {
                add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Progress", "${(it * 100).toInt()}% watched"))
            }
        }
    }

    LaunchedEffect(stuff.id) {
        primaryActionFocusRequester.requestFocus()
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete ${stuff.title}?",
            message = "This will remove the item from your Jellyfin library.",
            onDismissRequest = { showDeleteDialog = false },
            onConfirmDelete = {
                showDeleteDialog = false
                viewModel.deleteItem(onBack)
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CinematicHero(
            backdropUrl = stuff.backdropUrl,
            logoUrl = null,
            title = stuff.title,
            eyebrow = (stuff.type ?: "Video").uppercase(),
            ratingText = null,
            genres = emptyList(),
            primaryActionLabel = if (stuff.playbackProgress != null) "▶ Resume" else "▶ Play",
            onPrimaryAction = { onPlayItem(stuff.id) },
            secondaryActions = buildList {
                add(
                    if (stuff.isWatched) {
                        "Mark Unwatched" to { viewModel.markUnwatched() }
                    } else {
                        "Mark Watched" to { viewModel.markWatched() }
                    }
                )
                add("Delete" to { showDeleteDialog = true })
            },
            primaryActionFocusRequester = primaryActionFocusRequester,
            primaryActionDownFocusRequester = overviewFocusRequester,
        )

        DetailOverviewSection(
            title = stuff.title,
            posterUrl = stuff.posterUrl,
            description = stuff.overview.orEmpty(),
            factItems = factItems,
            chips = listOfNotNull(stuff.type),
            focusRequester = overviewFocusRequester,
            upFocusRequester = primaryActionFocusRequester,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}

@Composable
private fun StuffCollectionContent(
    stuff: StuffDetailModel,
    items: List<StuffItemModel>,
    onOpenItem: (String, String?) -> Unit,
) {
    val primaryActionFocusRequester = remember { FocusRequester() }
    val overviewFocusRequester = remember { FocusRequester() }
    val gridEntryFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(items.firstOrNull()?.id) }
    val factItems = remember(stuff, items) {
        buildList {
            stuff.type?.let {
                add(DetailLabeledMetaItem(Icons.Default.Category, "Type", it))
            }
            add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Items", items.size.toString()))
        }
    }

    LaunchedEffect(stuff.id) {
        primaryActionFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CinematicHero(
            backdropUrl = stuff.backdropUrl,
            logoUrl = null,
            title = stuff.title,
            eyebrow = "Collection · ${items.size} items",
            ratingText = null,
            genres = emptyList(),
            primaryActionLabel = "Browse",
            onPrimaryAction = { /* no-op, collection browsing is the content below */ },
            primaryActionFocusRequester = primaryActionFocusRequester,
            primaryActionDownFocusRequester = overviewFocusRequester,
        )

        DetailOverviewSection(
            title = stuff.title,
            posterUrl = stuff.posterUrl,
            description = stuff.overview.orEmpty(),
            factItems = factItems,
            chips = listOfNotNull(stuff.type),
            focusRequester = overviewFocusRequester,
            upFocusRequester = primaryActionFocusRequester,
            downFocusRequester = if (items.isNotEmpty()) gridEntryFocusRequester else null,
            modifier = Modifier.padding(top = 28.dp),
        )

        DetailContentSection(
            title = "Items",
            eyebrow = "${items.size} in collection",
            icon = Icons.Default.VideoLibrary,
            modifier = Modifier.padding(top = 0.dp),
        ) {}

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No items found in this collection",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    TvMediaCard(
                        title = item.title,
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
                            ),
                        watchStatus = item.watchStatus,
                        playbackProgress = item.playbackProgress,
                        onFocus = { lastFocusedItemId = item.id },
                        onClick = { onOpenItem(item.id, item.itemType) },
                    )
                }
            }
        }
    }
}
