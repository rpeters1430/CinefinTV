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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import kotlinx.coroutines.launch

enum class LibraryCategory(
    val title: String,
    val collectionType: String,
    val itemTypes: String?,
) {
    MOVIES(
        title = "Movies",
        collectionType = "movies",
        itemTypes = "Movie",
    ),
    TV_SHOWS(
        title = "TV Shows",
        collectionType = "tvshows",
        itemTypes = "Series",
    ),
    STUFF(
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
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(category) {
        viewModel.load(category)
    }

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "LibraryContent"
    ) { state ->
        when (state) {
            is LibraryUiState.Loading -> {
                Text(
                    text = "Loading ${category.title}...",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(48.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            is LibraryUiState.Error -> {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "${category.title} could not load",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { viewModel.load(category, forceRefresh = true) }) {
                        Text("Retry")
                    }
                }
            }

            is LibraryUiState.Content -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryHeader(
                                title = category.title,
                                description = "A curated view of your ${category.title.lowercase()} library with fast focus navigation.",
                                count = state.items.size,
                            )
                        }

                        if (state.items.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "No ${category.title.lowercase()} found in your library.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }

                        itemsIndexed(
                            state.items,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "MediaCard" }
                        ) { index, item ->
                            TvMediaCard(
                                title = item.title,
                                subtitle = item.subtitle,
                                imageUrl = item.imageUrl,
                                onClick = { onOpenItem(item.id) },
                                onFocus = {
                                    val visibleItems = gridState.layoutInfo.visibleItemsInfo
                                    if (visibleItems.isEmpty()) return@TvMediaCard

                                    val firstVisible = visibleItems.first().index
                                    val lastVisible = visibleItems.last().index
                                    val rowsInView = visibleItems
                                        .map { it.row }
                                        .distinct()
                                        .size
                                        .coerceAtLeast(1)
                                    val estimatedColumns =
                                        (visibleItems.size / rowsInView).coerceAtLeast(1)
                                    val shouldNudgeScroll =
                                        index >= lastVisible - estimatedColumns ||
                                            index <= firstVisible + estimatedColumns
                                    val targetIndex = (index - estimatedColumns).coerceAtLeast(0)

                                    if (shouldNudgeScroll && gridState.firstVisibleItemIndex != targetIndex) {
                                        coroutineScope.launch {
                                            gridState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                },
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                unwatchedCount = item.unwatchedCount,
                            )
                        }
                    }
                }
            }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        expressiveColors.chromeSurface,
                        expressiveColors.accentSurface.copy(alpha = 0.95f),
                    ),
                ),
            )
            .border(
                border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.8f)),
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(expressiveColors.pillMuted)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CollectionsBookmark,
                    contentDescription = null,
                    tint = expressiveColors.titleAccent,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$count titles available",
                style = MaterialTheme.typography.labelLarge,
                color = expressiveColors.titleAccent,
            )
        }
    }
}
