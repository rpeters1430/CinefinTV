package com.rpeters.cinefintv.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinTextInputField
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
    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onOpenItem = onOpenItem,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SearchScreenContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenItem: (com.rpeters.cinefintv.ui.screens.home.HomeCardModel) -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    val gridState = rememberLazyGridState()
    val primaryContentRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    var lastFocusedResultId by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

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
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
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
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .padding(bottom = spacing.elementGap)
                        .focusRequester(primaryContentRequester)
                        .focusProperties {
                            if (uiState.results.isNotEmpty()) {
                                down = firstResultFocusRequester
                            }
                        }
                )
            }

            when {
                uiState.query.isBlank() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Browse with intent: search movies, TV, music, and library content from one surface.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(SearchTestTags.Hint),
                        )
                    }
                }

                uiState.isLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Searching for \"${uiState.query}\"...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag(SearchTestTags.Loading),
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(SearchTestTags.Error),
                        )
                    }
                }

                uiState.results.isEmpty() && !uiState.isLoading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No results found for \"${uiState.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(SearchTestTags.Empty),
                        )
                    }
                }

                else -> {
                    val restoredFocusIndex = uiState.results.indexOfFirst { it.id == lastFocusedResultId }
                        .takeIf { it >= 0 } ?: 0
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${uiState.results.size} results",
                            style = MaterialTheme.typography.titleLarge,
                            color = expressiveColors.titleAccent,
                            modifier = Modifier.testTag(SearchTestTags.ResultsCount),
                        )
                    }
                    itemsIndexed(uiState.results, key = { _, item -> item.id }) { index, item ->
                        TvMediaCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            imageUrl = item.imageUrl,
                            onClick = { onOpenItem(item) },
                            watchStatus = item.watchStatus,
                            playbackProgress = item.playbackProgress,
                            unwatchedCount = item.unwatchedCount,
                            onFocus = { lastFocusedResultId = item.id },
                            modifier = Modifier
                                .testTag(SearchTestTags.resultItem(index))
                                .then(
                                    if (index == restoredFocusIndex) Modifier.focusRequester(firstResultFocusRequester) else Modifier
                                )
                                .then(
                                    if (index < 6) {
                                        Modifier.focusProperties { up = primaryContentRequester }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
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
            .padding(top = 6.dp, bottom = 2.dp),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier
                .testTag(SearchTestTags.Hero)
                .widthIn(max = 620.dp)
                .clip(RoundedCornerShape(spacing.cornerContainer)),
            shape = RoundedCornerShape(spacing.cornerContainer),
            colors = SurfaceDefaults.colors(
                containerColor = expressiveColors.chromeSurface.copy(alpha = 0.72f),
            ),
            border = Border(
                border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.55f)),
            ),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = expressiveColors.titleAccent,
                )
                Text(
                    text = if (query.isBlank()) "Search" else "Searching \"$query\"",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Find movies, shows, music, and library content from one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    CinefinTextInputField(
        label = "Search library",
        value = value,
        onValueChange = onValueChange,
        placeholder = "Search your Jellyfin library...",
        modifier = modifier.testTag(SearchTestTags.Field),
        imeAction = ImeAction.Search,
        keyboardType = KeyboardType.Text,
    )
}
