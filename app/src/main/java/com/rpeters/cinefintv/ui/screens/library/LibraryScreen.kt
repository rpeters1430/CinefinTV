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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
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
    val spacing = LocalCinefinSpacing.current
    
    // First focus anchor
    val firstItemRequester = remember { FocusRequester() }

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
                Box(modifier = Modifier.fillMaxSize().padding(spacing.gutter)) {
                    Text(
                        text = "Loading ${category.title}...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            is LibraryUiState.Error -> {
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
                // Initial focus
                LaunchedEffect(state) {
                    if (state.items.isNotEmpty()) {
                        firstItemRequester.requestFocus()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, top = spacing.rowGap, bottom = spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
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
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                unwatchedCount = item.unwatchedCount,
                                modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier
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
    val spacing = LocalCinefinSpacing.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.cornerContainer))
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
                shape = RoundedCornerShape(spacing.cornerContainer),
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(spacing.elementGap))
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
