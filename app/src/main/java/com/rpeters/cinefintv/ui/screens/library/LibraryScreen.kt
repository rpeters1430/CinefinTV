package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.RegisterPrimaryScreenFocus
import com.rpeters.cinefintv.ui.components.RequestScreenFocus
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvScreenTopFocusAnchor
import com.rpeters.cinefintv.ui.components.rememberTvScreenFocusState
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

enum class LibraryCategory(
    val title: String,
    val collectionType: String,
    val itemTypes: List<BaseItemKind>?,
) {
    MOVIES(
        title = "Movies",
        collectionType = "movies",
        itemTypes = listOf(BaseItemKind.MOVIE),
    ),
    TV_SHOWS(
        title = "TV Shows",
        collectionType = "tvshows",
        itemTypes = listOf(BaseItemKind.SERIES),
    ),
    COLLECTIONS(
        title = "Stuff",
        collectionType = "homevideos",
        itemTypes = null,
    ),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    category: LibraryCategory,
    onOpenItem: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedItems = viewModel.pagedItems.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val spacing = LocalCinefinSpacing.current
    
    // First focus anchor
    val screenFocus = rememberTvScreenFocusState()
    RegisterPrimaryScreenFocus(
        route = when (category) {
            LibraryCategory.MOVIES -> NavRoutes.LIBRARY_MOVIES
            LibraryCategory.TV_SHOWS -> NavRoutes.LIBRARY_TVSHOWS
            LibraryCategory.COLLECTIONS -> NavRoutes.LIBRARY_COLLECTIONS
        },
        requester = screenFocus.primaryContentRequester,
    )

    androidx.compose.runtime.LaunchedEffect(category) {
        viewModel.load(category)
    }

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "LibraryContent"
    ) { state ->
        when (state) {
            is LibraryUiState.Loading -> {
                LibraryLoadingState(category = category)
            }

            is LibraryUiState.Content -> {
                val featuredItems = state.recentlyAdded
                val featuredSecondRequester = remember { FocusRequester() }
                val firstGridItemRequester = remember { FocusRequester() }

                when (val refreshState = pagedItems.loadState.refresh) {
                    is LoadState.Loading -> {
                        if (pagedItems.itemCount == 0) {
                            LibraryLoadingState(category = category)
                        }
                    }

                    is LoadState.Error -> {
                        if (pagedItems.itemCount == 0) {
                            LibraryErrorState(
                                category = category,
                                message = refreshState.error.message ?: "Unknown paging error",
                                onRetry = pagedItems::refresh,
                            )
                        }
                    }

                    else -> Unit
                }

                if (pagedItems.itemCount > 0 || pagedItems.loadState.refresh is LoadState.NotLoading) {
                    RequestScreenFocus(
                        key = category to pagedItems.itemCount,
                        requester = screenFocus.topAnchorRequester,
                        enabled = pagedItems.itemCount > 0,
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.gutter + 16.dp,
                                end = spacing.gutter + 16.dp,
                                top = spacing.safeZoneVertical,
                                bottom = 120.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TvScreenTopFocusAnchor(
                                    state = screenFocus,
                                    onFocused = {
                                        coroutineScope.launch {
                                            gridState.animateScrollToItem(0)
                                        }
                                    }
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
                                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                                        ) {
                                            featuredItems.forEachIndexed { index, item ->
                                                TvMediaCard(
                                                    title = item.title,
                                                    subtitle = item.subtitle,
                                                    imageUrl = item.imageUrl,
                                                    onClick = { onOpenItem(item.id) },
                                                    watchStatus = item.watchStatus,
                                                    playbackProgress = item.playbackProgress,
                                                    unwatchedCount = item.unwatchedCount,
                                                    aspectRatio = 16f / 9f,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .then(
                                                            if (index == 0) {
                                                                Modifier
                                                                    .focusRequester(screenFocus.primaryContentRequester)
                                                                    .focusProperties {
                                                                        up = screenFocus.topAnchorRequester
                                                                        right = featuredSecondRequester
                                                                        down = firstGridItemRequester
                                                                    }
                                                            } else {
                                                                Modifier
                                                                    .focusRequester(featuredSecondRequester)
                                                                    .focusProperties {
                                                                        up = screenFocus.topAnchorRequester
                                                                        left = screenFocus.primaryContentRequester
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
                                        text = "No ${category.title.lowercase()} found in your library.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }

                            items(
                                count = pagedItems.itemCount,
                                key = { index -> pagedItems[index]?.id ?: "placeholder-$index" },
                                contentType = { "MediaCard" },
                            ) { index ->
                                val item = pagedItems[index] ?: return@items

                                TvMediaCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    imageUrl = item.imageUrl,
                                    onClick = { onOpenItem(item.id) },
                                    watchStatus = item.watchStatus,
                                    playbackProgress = item.playbackProgress,
                                    unwatchedCount = item.unwatchedCount,
                                    aspectRatio = 16f / 9f,
                                    cardWidth = null,
                                    modifier = if (index == 0) {
                                        Modifier
                                            .focusRequester(firstGridItemRequester)
                                            .focusProperties {
                                                up = if (featuredItems.isNotEmpty()) screenFocus.primaryContentRequester else screenFocus.topAnchorRequester
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryLoadingState(category: LibraryCategory) {
    val spacing = LocalCinefinSpacing.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.gutter),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(
                text = "Loading ${category.title}...",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LibraryErrorState(
    category: LibraryCategory,
    message: String,
    onRetry: () -> Unit,
) {
    val spacing = LocalCinefinSpacing.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
    ) {
        Text(
            text = "${category.title} could not load",
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryHeader(
    title: String,
    description: String,
    count: Int,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.cornerContainer))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        expressiveColors.chromeSurface.copy(alpha = 0.92f),
                        expressiveColors.elevatedSurface.copy(alpha = 0.94f),
                    ),
                ),
            )
            .border(
                border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(spacing.elementGap))
                    .background(expressiveColors.pillMuted)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CollectionsBookmark,
                    contentDescription = null,
                    tint = expressiveColors.titleAccent,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = com.rpeters.cinefintv.ui.theme.OnBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = com.rpeters.cinefintv.ui.theme.OnSurfaceMuted,
            )
            Text(
                text = "$count titles available",
                style = MaterialTheme.typography.labelMedium,
                color = expressiveColors.titleAccent,
            )
        }
    }
}
