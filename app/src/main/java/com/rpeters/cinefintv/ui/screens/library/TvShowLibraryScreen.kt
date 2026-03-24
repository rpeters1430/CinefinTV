package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ExperimentalTvMaterial3Api

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvShowLibraryScreen(
    onOpenItem: (LibraryCardModel) -> Unit,
    viewModel: TvShowLibraryViewModel = hiltViewModel()
) {
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
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
    LibraryGridContent(
        uiState = uiState,
        errorTitle = "Failed to load TV shows",
        emptyTitle = "No TV shows found",
        columnCount = 5,
        aspectRatio = 2f / 3f,
        onOpenItem = onOpenItem,
        onRetry = { pagedItems.retry() },
    )
}
