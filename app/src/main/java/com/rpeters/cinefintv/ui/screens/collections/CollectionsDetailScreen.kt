package com.rpeters.cinefintv.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.RegisterPrimaryScreenFocus
import com.rpeters.cinefintv.ui.components.RequestScreenFocus
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvScreenFocusState
import com.rpeters.cinefintv.ui.components.TvScreenTopFocusAnchor
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CollectionsDetailScreen(
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionsDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing.current

    when (val state = uiState) {
        is CollectionsDetailUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = "Loading Collections details...",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        is CollectionsDetailUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Collections detail could not load", 
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = viewModel::load) { Text("Retry") }
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        }

        is CollectionsDetailUiState.Content -> {
            val item = state.item
            val playButtonRequester = remember { FocusRequester() }
            val topAnchorRequester = remember { FocusRequester() }
            val moreShelfRequester = remember { FocusRequester() }
            val screenFocus = remember(topAnchorRequester, playButtonRequester) {
                TvScreenFocusState(
                    topAnchorRequester = topAnchorRequester,
                    primaryContentRequester = playButtonRequester,
                )
            }

            RegisterPrimaryScreenFocus(
                route = NavRoutes.COLLECTIONS_DETAIL,
                requester = screenFocus.topAnchorRequester,
            )

            RequestScreenFocus(
                key = state.item.id,
                requester = screenFocus.topAnchorRequester,
            )

            androidx.compose.runtime.LaunchedEffect(state.isDeleted) {
                if (state.isDeleted) {
                    onBack()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.backdropUrl != null) {
                        AsyncImage(
                            model = item.backdropUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Black,
                                    ),
                                ),
                            ),
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.safeZoneVertical),
                ) {
                    item {
                        TvScreenTopFocusAnchor(
                            state = screenFocus,
                            onFocused = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        )
                    }

                    item { Spacer(Modifier.fillParentMaxHeight(0.22f)) }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item.metadataLine?.let { metadata ->
                                Text(
                                    text = metadata,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                            item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                                Text(
                                    text = overview,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            item.technicalDetails?.summary?.let { techSummary ->
                                Text(
                                    text = techSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Button(
                                    onClick = { onPlay(item.id) },
                                    modifier = Modifier
                                        .focusRequester(playButtonRequester)
                                        .focusProperties {
                                            if (state.moreFromCollections.isNotEmpty()) {
                                                down = moreShelfRequester
                                            }
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play")
                                }
                                if (state.isDeleting) {
                                    Text(
                                        text = "Deleting...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                    )
                                } else if (state.isDeleteConfirmationVisible) {
                                    Button(
                                        onClick = viewModel::confirmDelete,
                                        modifier = Modifier.focusProperties {
                                            if (state.moreFromCollections.isNotEmpty()) {
                                                down = moreShelfRequester
                                            }
                                        }
                                    ) {
                                        Text("Confirm Delete")
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::cancelDelete,
                                        modifier = Modifier.focusProperties {
                                            if (state.moreFromCollections.isNotEmpty()) {
                                                down = moreShelfRequester
                                            }
                                        }
                                    ) {
                                        Text("Cancel")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::requestDelete,
                                        modifier = Modifier.focusProperties {
                                            if (state.moreFromCollections.isNotEmpty()) {
                                                down = moreShelfRequester
                                            }
                                        }
                                    ) {
                                        Text("Delete")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onBack,
                                    modifier = Modifier.focusProperties {
                                        if (state.moreFromCollections.isNotEmpty()) {
                                            down = moreShelfRequester
                                        }
                                    }
                                ) {
                                    Text("Back")
                                }
                            }
                            state.actionErrorMessage?.let { actionError ->
                                Text(
                                    text = actionError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    if (state.moreFromCollections.isNotEmpty()) {
                        item { Spacer(Modifier.height(32.dp)) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CinefinShelfTitle(
                                    title = "More Collections",
                                    eyebrow = item.title,
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    items(state.moreFromCollections, key = { it.id }) { item ->
                                        TvMediaCard(
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            imageUrl = item.imageUrl,
                                            onClick = { onOpenItem(item.id) },
                                            watchStatus = item.watchStatus,
                                            playbackProgress = item.playbackProgress,
                                            aspectRatio = 16f / 9f,
                                            cardWidth = 260.dp,
                                            modifier = Modifier
                                                .then(
                                                    if (item == state.moreFromCollections.first()) {
                                                        Modifier.focusRequester(moreShelfRequester)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .focusProperties {
                                                    up = playButtonRequester
                                                },
                                        )
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
