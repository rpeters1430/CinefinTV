package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard

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
                    color = Color.White,
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
                        color = Color.White,
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    items(
                        state.items,
                        key = { it.id },
                        contentType = { "MediaCard" }
                    ) { item ->
                        TvMediaCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            onClick = { onOpenItem(item.id) },
                        )
                    }
                }
            }
        }
    }
}
