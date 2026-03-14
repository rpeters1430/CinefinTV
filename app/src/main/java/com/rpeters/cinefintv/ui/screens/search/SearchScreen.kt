package com.rpeters.cinefintv.ui.screens.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenItem: (com.rpeters.cinefintv.ui.screens.home.HomeCardModel) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    
    val searchFieldRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFieldRequester.requestFocus()
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
            columns = GridCells.Adaptive(minSize = 260.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, top = spacing.rowGap, bottom = spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
            verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchHero(
                    query = uiState.query,
                    modifier = Modifier.padding(bottom = spacing.elementGap),
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchField(
                    value = uiState.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier
                        .padding(bottom = spacing.elementGap)
                        .focusRequester(searchFieldRequester)
                )
            }

            when {
                uiState.query.isBlank() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Browse with intent: search movies, TV, music, and library content from one surface.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                uiState.isLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Searching for \"${uiState.query}\"...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                uiState.results.isEmpty() && !uiState.isLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No results found for \"${uiState.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${uiState.results.size} results",
                            style = MaterialTheme.typography.titleLarge,
                            color = expressiveColors.titleAccent,
                        )
                    }
                    items(uiState.results, key = { it.id }) { item ->
                        TvMediaCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            onClick = { onOpenItem(item) },
                            watchStatus = item.watchStatus,
                            playbackProgress = item.playbackProgress,
                            unwatchedCount = item.unwatchedCount,
                        )                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchHero(
    query: String,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.cornerContainer))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        expressiveColors.heroStart.copy(alpha = 0.9f),
                        expressiveColors.heroEnd.copy(alpha = 0.95f),
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
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = expressiveColors.titleAccent,
            )
            Text(
                text = if (query.isBlank()) "Search" else "Searching \"$query\"",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Fast, full-library discovery with a TV-first focus flow.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val spacing = LocalCinefinSpacing.current
    val shape = RoundedCornerShape(spacing.elementGap)
    val expressiveColors = LocalCinefinExpressiveColors.current
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.border,
        animationSpec = tween(300),
        label = "SearchBorderColor"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 2.dp,
                color = borderColor,
                shape = shape,
            )
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        if (isFocused) expressiveColors.accentSurface else MaterialTheme.colorScheme.surface,
                        expressiveColors.elevatedSurface,
                    ),
                ),
                shape = shape
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = "Search your Jellyfin library...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}
