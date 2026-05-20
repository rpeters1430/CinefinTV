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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.components.ImmersiveBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.shrinkVertically

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
    val spacing = LocalCinefinSpacing.current
    var focusedItem by remember { androidx.compose.runtime.mutableStateOf<LibraryCardModel?>(null) }
    
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        ImmersiveBackground(backdropUrl = focusedItem?.backdropUrl)
        
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            LibraryMetadataHeader(item = focusedItem)
            
            Box(modifier = Modifier.weight(1f)) {

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
                val retryFocusRequester = remember { FocusRequester() }
                val destinationFocus = rememberTopLevelDestinationFocus(retryFocusRequester)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LibraryTestTags.Error),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(spacing.gutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    ) {
                        Text(
                            text = errorTitle,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .then(destinationFocus.primaryContentModifier())
                                .testTag(LibraryTestTags.RetryButton),
                            scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.1f)
                        ) {
                            Text("Try Again", style = MaterialTheme.typography.titleMedium)
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
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Check your library or server connection.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            is LibraryGridUiState.Content -> {
                val firstItemFocusRequester = remember { FocusRequester() }
                var lastFocusedItemId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
                val restoredFocusIndex = uiState.items.indexOfFirst { it.id == lastFocusedItemId }
                    .takeIf { it >= 0 } ?: 0
                val destinationFocus = rememberTopLevelDestinationFocus(firstItemFocusRequester)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(
                        horizontal = spacing.gridContentPadding + 8.dp,
                        vertical = spacing.gutter + 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGap + 8.dp),
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
                            onFocus = { 
                                focusedItem = item
                                lastFocusedItemId = item.id 
                            },
                            compactMetadata = true,
                            modifier = if (index == restoredFocusIndex) {
                                Modifier
                                    .testTag(LibraryTestTags.item(index))
                                    .focusRequester(firstItemFocusRequester)
                                    .then(
                                        if (index < columnCount) {
                                            destinationFocus.drawerEscapeModifier(
                                                isLeftEdge = index % columnCount == 0,
                                                up = destinationFocus.drawerFocusRequester,
                                            )
                                        } else {
                                            destinationFocus.drawerEscapeModifier(
                                                isLeftEdge = index % columnCount == 0,
                                            )
                                        }
                                    )
                            } else {
                                Modifier
                                    .testTag(LibraryTestTags.item(index))
                                    .then(
                                        if (index < columnCount) {
                                            destinationFocus.drawerEscapeModifier(
                                                isLeftEdge = index % columnCount == 0,
                                                up = destinationFocus.drawerFocusRequester,
                                            )
                                        } else {
                                            destinationFocus.drawerEscapeModifier(
                                                isLeftEdge = index % columnCount == 0,
                                            )
                                        }
                                    )
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
    }
}

@Composable
private fun LibraryMetadataHeader(
    item: LibraryCardModel?,
    modifier: Modifier = Modifier
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current

    AnimatedVisibility(
        visible = item != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        item?.let {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gridContentPadding + 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    it.itemType?.let { type -> CinefinChip(label = type, strong = true) }
                    it.year?.let { year -> CinefinChip(label = year.toString()) }
                    it.rating?.let { rating -> CinefinChip(label = "★ $rating") }
                }
                
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                it.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 800.dp)
                    )
                }
            }
        }
    }
    
    if (item == null) {
        Spacer(modifier = Modifier.height(140.dp))
    }
}

