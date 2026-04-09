@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Schedule
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection

@Composable
fun CollectionDetailScreen(
    itemId: String,
    onOpenItem: (String, String?) -> Unit,
    onPlayItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.init(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenPaused by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasBeenPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                    hasBeenPaused = false
                    viewModel.load(silent = true)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is CollectionDetailUiState.Loading -> DetailLoadingState()
            is CollectionDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is CollectionDetailUiState.Content -> {
                if (state.stuff.isCollection) {
                    CollectionFolderContent(
                        stuff = state.stuff,
                        items = state.items,
                        onOpenItem = onOpenItem,
                        onBack = onBack,
                        viewModel = viewModel,
                    )
                } else {
                    CollectionVideoContent(
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
private fun CollectionVideoContent(
    stuff: CollectionDetailModel,
    onPlayItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel,
) {
    val primaryActionFocusRequester = remember { FocusRequester() }
    val overviewFocusRequester = remember { FocusRequester() }
    var showDeleteDialog by remember(stuff.id) { mutableStateOf(false) }
    var didInitialFocus by remember { mutableStateOf(false) }

    val factItems = remember(stuff) {
        buildList {
            stuff.type?.let {
                add(DetailLabeledMetaItem(Icons.Default.Category, "Type", it))
            }
            stuff.runtime?.let {
                add(DetailLabeledMetaItem(Icons.Default.Schedule, "Runtime", it))
            }
            stuff.addedDate?.let {
                add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "Added", it))
            }
            stuff.mediaQuality?.let {
                add(DetailLabeledMetaItem(Icons.Default.HighQuality, "Quality", it))
            }
            stuff.playbackProgress?.let {
                add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Progress", "${(it * 100).toInt()}% watched"))
            }
        }
    }
    val eyebrow = remember(stuff) {
        listOfNotNull(
            stuff.type,
            stuff.year?.toString(),
            stuff.runtime,
        ).joinToString(" · ").ifBlank { "Video" }
    }
    val ratingText = remember(stuff) {
        stuff.playbackProgress?.let { "${(it * 100).toInt()}% watched" }
    }
    val chips = remember(stuff) {
        buildList {
            add(stuff.type ?: "Video")
            if (stuff.playbackProgress != null) {
                add("Resume available")
            }
            if (stuff.isWatched) {
                add("Watched")
            }
            stuff.mediaQuality?.let(::add)
        }.distinct()
    }

    LaunchedEffect(stuff.id) {
        if (!didInitialFocus) {
            primaryActionFocusRequester.requestFocus()
            didInitialFocus = true
        }
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
            eyebrow = eyebrow,
            ratingText = ratingText,
            genres = chips,
            primaryActionLabel = if (stuff.playbackProgress != null) "▶ Resume" else "▶ Play",
            onPrimaryAction = { onPlayItem(stuff.id) },
            secondaryActions = buildList {
                add("Delete" to { showDeleteDialog = true })
                add(
                    if (stuff.isWatched) {
                        "Mark Unwatched" to { viewModel.markUnwatched() }
                    } else {
                        "Mark Watched" to { viewModel.markWatched() }
                    }
                )
            },
            primaryActionFocusRequester = primaryActionFocusRequester,
            primaryActionDownFocusRequester = overviewFocusRequester,
        )

        DetailOverviewSection(
            title = stuff.title,
            posterUrl = stuff.posterUrl,
            description = stuff.overview ?: "No description available for this item yet.",
            factItems = factItems,
            chips = chips,
            focusRequester = overviewFocusRequester,
            upFocusRequester = primaryActionFocusRequester,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}

@Composable
private fun CollectionFolderContent(
    stuff: CollectionDetailModel,
    items: List<CollectionItemModel>,
    onOpenItem: (String, String?) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel,
) {
    val primaryActionFocusRequester = remember { FocusRequester() }
    val overviewFocusRequester = remember { FocusRequester() }
    val gridEntryFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(items.firstOrNull()?.id) }
    var showDeleteDialog by remember(stuff.id) { mutableStateOf(false) }
    var didInitialFocus by remember { mutableStateOf(false) }

    val factItems = remember(stuff, items) {
        buildList {
            stuff.type?.let {
                add(DetailLabeledMetaItem(Icons.Default.Category, "Type", it))
            }
            add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Items", items.size.toString()))
            stuff.addedDate?.let {
                add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "Added", it))
            }
        }
    }
    val chips = remember(stuff, items) {
        buildList {
            add(stuff.type ?: "Collection")
            add("${items.size} items")
        }
    }

    LaunchedEffect(stuff.id) {
        if (!didInitialFocus) {
            primaryActionFocusRequester.requestFocus()
            didInitialFocus = true
        }
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
            eyebrow = listOfNotNull(stuff.type ?: "Collection", "${items.size} items").joinToString(" · "),
            ratingText = null,
            genres = chips,
            primaryActionLabel = if (items.isEmpty()) "Collection" else "Browse Items",
            onPrimaryAction = {
                if (items.isNotEmpty()) {
                    gridEntryFocusRequester.requestFocus()
                }
            },
            secondaryActions = listOf("Delete" to { showDeleteDialog = true }),
            primaryActionFocusRequester = primaryActionFocusRequester,
            primaryActionDownFocusRequester = overviewFocusRequester,
        )

        DetailOverviewSection(
            title = stuff.title,
            posterUrl = stuff.posterUrl,
            description = stuff.overview ?: "Browse everything included in this collection below.",
            factItems = factItems,
            chips = chips,
            focusRequester = overviewFocusRequester,
            upFocusRequester = primaryActionFocusRequester,
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
                            ),
                        watchStatus = item.watchStatus,
                        playbackProgress = item.playbackProgress,
                        unwatchedCount = item.unwatchedCount,
                        onFocus = { lastFocusedItemId = item.id },
                        onClick = { onOpenItem(item.id, item.itemType) },
                    )
                }
            }
        }
    }
}
