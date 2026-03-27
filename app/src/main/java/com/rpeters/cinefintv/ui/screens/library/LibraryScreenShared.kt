package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

sealed class LibraryGridUiState {
    data object Loading : LibraryGridUiState()
    data class Error(val message: String) : LibraryGridUiState()
    data object Empty : LibraryGridUiState()
    data class Content(
        val items: List<LibraryCardModel>,
        val isAppending: Boolean = false,
    ) : LibraryGridUiState()
}

object LibraryTestTags {
    const val Loading = "library_loading"
    const val Error = "library_error"
    const val RetryButton = "library_retry_button"
    const val Empty = "library_empty"
    const val Grid = "library_grid"

    fun item(index: Int): String = "library_item_$index"
}

@Composable
internal fun LibraryGridContent(
    uiState: LibraryGridUiState,
    errorTitle: String,
    emptyTitle: String,
    columnCount: Int,
    aspectRatio: Float,
    gridState: LazyGridState,
    onOpenItem: (LibraryCardModel) -> Unit,
    onItemMenuAction: ((LibraryCardModel) -> Unit)? = null,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            LibraryGridUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LibraryTestTags.Loading),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is LibraryGridUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LibraryTestTags.Error),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = errorTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag(LibraryTestTags.RetryButton),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            LibraryGridUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LibraryTestTags.Empty),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is LibraryGridUiState.Content -> {
                val spacing = LocalCinefinSpacing.current
                val firstItemFocusRequester = remember { FocusRequester() }
                var lastFocusedItemId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
                val restoredFocusIndex = uiState.items.indexOfFirst { it.id == lastFocusedItemId }
                    .takeIf { it >= 0 } ?: 0
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(
                        horizontal = spacing.gridContentPadding,
                        vertical = 32.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LibraryTestTags.Grid),
                ) {
                    items(uiState.items.size) { index ->
                        val item = uiState.items[index]
                        TvMediaCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            aspectRatio = aspectRatio,
                            watchStatus = item.watchStatus,
                            playbackProgress = item.playbackProgress,
                            unwatchedCount = item.unwatchedCount,
                            onClick = { onOpenItem(item) },
                            onMenuAction = onItemMenuAction?.let { menuHandler ->
                                { menuHandler(item) }
                            },
                            onFocus = { lastFocusedItemId = item.id },
                            compactMetadata = true,
                            modifier = if (index == restoredFocusIndex) {
                                Modifier
                                    .testTag(LibraryTestTags.item(index))
                                    .focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier.testTag(LibraryTestTags.item(index))
                            },
                        )
                    }

                    if (uiState.isAppending) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
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
