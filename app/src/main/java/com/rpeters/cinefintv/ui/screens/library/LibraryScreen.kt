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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val screenFocus = rememberTvScreenFocusState()
    RegisterPrimaryScreenFocus(
        route = when (category) {
            LibraryCategory.MOVIES -> NavRoutes.LIBRARY_MOVIES
            LibraryCategory.TV_SHOWS -> NavRoutes.LIBRARY_TVSHOWS
            LibraryCategory.STUFF -> NavRoutes.LIBRARY_STUFF
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
                RequestScreenFocus(
                    key = category to state.items.size,
                    requester = screenFocus.topAnchorRequester,
                    enabled = state.items.isNotEmpty(),
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.gutter + 16.dp,
                            end = spacing.gutter + 16.dp,
                            top = 8.dp,
                            bottom = 120.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
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
                                aspectRatio = 2f / 3f,
                                cardWidth = null,
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(screenFocus.primaryContentRequester)
                                        .focusProperties {
                                            up = screenFocus.topAnchorRequester
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
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
