@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard

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
) {
    val anchorFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(stuff.id) {
        anchorFocusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DetailAnchor(
            focusRequester = anchorFocusRequester,
            onFocused = {
                primaryActionFocusRequester.requestFocus()
            }
        )
        DetailHeroBox(backdropUrl = stuff.backdropUrl, modifier = Modifier.fillMaxSize()) {
            DetailGlassPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.55f)
                    .padding(horizontal = 56.dp, vertical = 32.dp),
            ) {
                DetailChipRow(
                    labels = buildList {
                        add(stuff.type ?: "Video")
                        if (!stuff.isCollection) add("Ready to play")
                    }
                )
                Text(
                    text = stuff.title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                stuff.overview?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
                stuff.playbackProgress?.let {
                    DetailProgressLabel(progress = it)
                }
                DetailActionRow(
                    primaryLabel = if (stuff.playbackProgress != null) "Resume Now" else "Play Now",
                    onPrimaryClick = { onPlayItem(stuff.id) },
                    primaryFocusRequester = primaryActionFocusRequester,
                )
            }
        }
    }
}

@Composable
private fun StuffCollectionContent(
    stuff: StuffDetailModel,
    items: List<StuffItemModel>,
    onOpenItem: (String, String?) -> Unit,
) {
    val anchorFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val gridEntryFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(items.firstOrNull()?.id) }

    LaunchedEffect(stuff.id) {
        anchorFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailAnchor(
            focusRequester = anchorFocusRequester,
            onFocused = {
                primaryActionFocusRequester.requestFocus()
            }
        )
        DetailHeroBox(backdropUrl = stuff.backdropUrl) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                DetailPosterArt(
                    imageUrl = null,
                    title = stuff.title,
                    modifier = Modifier
                        .width(172.dp)
                        .height(258.dp),
                )
                DetailGlassPanel(
                    modifier = Modifier.fillMaxWidth(0.66f)
                ) {
                    DetailChipRow(
                        labels = buildList {
                            add(stuff.type ?: "Collection")
                            add("${items.size} items")
                        }
                    )
                    Text(
                        text = stuff.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    stuff.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    DetailActionRow(
                        primaryLabel = "Browse Items",
                        onPrimaryClick = {},
                        primaryFocusRequester = primaryActionFocusRequester,
                        primaryDownFocusRequester = if (items.isNotEmpty()) gridEntryFocusRequester else null,
                    )
                }
            }
        }

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
                                    Modifier.focusProperties { up = primaryActionFocusRequester }
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
