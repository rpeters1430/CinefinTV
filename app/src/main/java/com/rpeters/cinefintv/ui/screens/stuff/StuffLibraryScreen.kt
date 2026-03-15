package com.rpeters.cinefintv.ui.screens.stuff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.ScrollFocusAnchor
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.library.LibraryHeader
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StuffLibraryScreen(
    onOpenItem: (String) -> Unit,
    viewModel: StuffLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val topAnchorRequester = remember { FocusRequester() }
    val firstItemRequester = remember { FocusRequester() }
    var lastFocusedIndex by remember { mutableIntStateOf(-1) }
    var focusScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    when (val state = uiState) {
        is StuffLibraryUiState.Loading -> {
            Text(
                text = "Loading Stuff (Home Videos)...",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(48.dp),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        is StuffLibraryUiState.Error -> {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Stuff could not load", 
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { viewModel.load(forceRefresh = true) }) {
                    Text("Retry")
                }
            }
        }

        is StuffLibraryUiState.Content -> {
            LaunchedEffect(state.items) {
                if (state.items.isNotEmpty()) {
                    firstItemRequester.requestFocus()
                }
            }

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
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ScrollFocusAnchor(
                            modifier = Modifier.focusRequester(topAnchorRequester),
                            onFocused = {
                                coroutineScope.launch {
                                    gridState.animateScrollToItem(0)
                                }
                            },
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryHeader(
                            title = "Stuff",
                            description = "Home videos and personal media with a gallery-style browsing surface.",
                            count = state.items.size,
                        )
                    }

                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        TvMediaCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            onClick = { onOpenItem(item.id) },
                            onFocus = {
                                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                if (visibleItems.isEmpty()) return@TvMediaCard

                                val rowsInView = visibleItems
                                    .map { it.row }
                                    .distinct()
                                    .size
                                    .coerceAtLeast(1)
                                val estimatedColumns =
                                    (visibleItems.size / rowsInView).coerceAtLeast(1)
                                val visibleRows = visibleItems
                                    .map { it.row }
                                    .distinct()
                                    .sorted()
                                val bottomThresholdRow = (visibleRows.lastOrNull() ?: 0) - 1
                                val focusedItemRow = visibleItems
                                    .firstOrNull { it.index == index }
                                    ?.row
                                    ?: return@TvMediaCard
                                val movingDown = index > lastFocusedIndex

                                lastFocusedIndex = index

                                if (movingDown && focusedItemRow >= bottomThresholdRow) {
                                    val targetIndex = (index - estimatedColumns).coerceAtLeast(
                                        gridState.firstVisibleItemIndex
                                    )

                                    if (targetIndex > gridState.firstVisibleItemIndex) {
                                        focusScrollJob?.cancel()
                                        focusScrollJob = coroutineScope.launch {
                                            gridState.scrollToItem(targetIndex)
                                        }
                                    }
                                } else if (!movingDown && focusedItemRow <= (visibleRows.firstOrNull()
                                        ?: 0) + 1 && gridState.firstVisibleItemIndex > 0
                                ) {
                                    val targetIndex = (index - estimatedColumns).coerceAtLeast(0)
                                    focusScrollJob?.cancel()
                                    focusScrollJob = coroutineScope.launch {
                                        gridState.scrollToItem(targetIndex)
                                    }
                                }
                            },
                            watchStatus = item.watchStatus,
                            playbackProgress = item.playbackProgress,
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstItemRequester)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}
