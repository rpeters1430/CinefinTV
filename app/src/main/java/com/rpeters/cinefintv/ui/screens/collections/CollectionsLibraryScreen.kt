package com.rpeters.cinefintv.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.RegisterPrimaryScreenFocus
import com.rpeters.cinefintv.ui.components.ScrollFocusAnchor
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CollectionsLibraryScreen(
    onOpenItem: (String) -> Unit,
    viewModel: CollectionsLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val topAnchorRequester = remember { FocusRequester() }
    val firstItemRequester = remember { FocusRequester() }
    val firstGridItemRequester = remember { FocusRequester() }

    RegisterPrimaryScreenFocus(
        route = NavRoutes.LIBRARY_COLLECTIONS,
        requester = firstItemRequester,
    )

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    when (val state = uiState) {
        is CollectionsLibraryUiState.Loading -> {
            CollectionsLoadingState()
        }

        is CollectionsLibraryUiState.Content -> {
            val featuredItems = state.recentlyAdded
            val featuredSecondRequester = remember { FocusRequester() }

            when (val refreshState = pagedItems.loadState.refresh) {
                is LoadState.Loading -> {
                    if (pagedItems.itemCount == 0) {
                        CollectionsLoadingState()
                    }
                }

                is LoadState.Error -> {
                    if (pagedItems.itemCount == 0) {
                        CollectionsErrorState(
                            message = refreshState.error.message ?: "Unknown paging error",
                            onRetry = pagedItems::refresh,
                        )
                    }
                }

                else -> Unit
            }

            LaunchedEffect(pagedItems.itemCount) {
                if (pagedItems.itemCount > 0) {
                    topAnchorRequester.requestFocus()
                }
            }

            if (pagedItems.itemCount > 0 || pagedItems.loadState.refresh is LoadState.NotLoading) {
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
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 64.dp, vertical = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ScrollFocusAnchor(
                                modifier = Modifier
                                    .focusRequester(topAnchorRequester)
                                    .focusProperties {
                                        down = if (featuredItems.isNotEmpty()) firstItemRequester else firstGridItemRequester
                                    },
                                onFocused = {
                                    gridState.requestScrollToItem(0)
                                },
                            )
                        }

                        if (featuredItems.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "Recently Added",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    ) {
                                        featuredItems.forEachIndexed { index, item ->
                                            TvMediaCard(
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                imageUrl = item.imageUrl,
                                                onClick = { onOpenItem(item.id) },
                                                watchStatus = item.watchStatus,
                                                playbackProgress = item.playbackProgress,
                                                aspectRatio = 16f / 9f,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .then(
                                                        if (index == 0) {
                                                            Modifier
                                                                .focusRequester(firstItemRequester)
                                                                .focusProperties {
                                                                    up = topAnchorRequester
                                                                    right = featuredSecondRequester
                                                                    down = firstGridItemRequester
                                                                }
                                                        } else {
                                                            Modifier
                                                                .focusRequester(featuredSecondRequester)
                                                                .focusProperties {
                                                                    up = topAnchorRequester
                                                                    left = firstItemRequester
                                                                    down = firstGridItemRequester
                                                                }
                                                        }
                                                    ),
                                            )
                                        }
                                        if (featuredItems.size == 1) {
                                            Box(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        if (pagedItems.itemCount == 0) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "No items were found in Stuff.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(
                            count = pagedItems.itemCount,
                            key = { index -> pagedItems[index]?.id ?: "placeholder-$index" },
                        ) { index ->
                            val item = pagedItems[index] ?: return@items

                            TvMediaCard(
                                title = item.title,
                                subtitle = item.subtitle,
                                imageUrl = item.imageUrl,
                                onClick = { onOpenItem(item.id) },
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                aspectRatio = 16f / 9f,
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(firstGridItemRequester)
                                        .focusProperties {
                                            up = if (featuredItems.isNotEmpty()) firstItemRequester else topAnchorRequester
                                        }
                                } else {
                                    Modifier
                                },
                            )
                        }

                        if (pagedItems.loadState.append is LoadState.Loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionsLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(
                text = "Loading Stuff...",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CollectionsErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Stuff could not load",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
