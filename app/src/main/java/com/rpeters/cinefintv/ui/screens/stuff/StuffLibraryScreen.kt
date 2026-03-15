package com.rpeters.cinefintv.ui.screens.stuff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.RegisterPrimaryScreenFocus
import com.rpeters.cinefintv.ui.components.ScrollFocusAnchor
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.screens.library.LibraryHeader
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StuffLibraryScreen(
    onOpenItem: (String) -> Unit,
    viewModel: StuffLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val topAnchorRequester = remember { FocusRequester() }
    val firstItemRequester = remember { FocusRequester() }

    RegisterPrimaryScreenFocus(
        route = NavRoutes.LIBRARY_STUFF,
        requester = firstItemRequester,
    )

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
            Column(
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
                    topAnchorRequester.requestFocus()
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
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 64.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ScrollFocusAnchor(
                            modifier = Modifier
                                .focusRequester(topAnchorRequester)
                                .focusProperties {
                                    down = firstItemRequester
                                },
                            onFocused = {
                                gridState.requestScrollToItem(0)
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
                            watchStatus = item.watchStatus,
                            playbackProgress = item.playbackProgress,
                            modifier = if (index == 0) {
                                Modifier
                                    .focusRequester(firstItemRequester)
                                    .focusProperties {
                                        up = topAnchorRequester
                                    }
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
