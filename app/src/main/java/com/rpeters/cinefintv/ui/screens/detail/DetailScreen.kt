package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.RequestScreenFocus
import com.rpeters.cinefintv.ui.components.TvScreenFocusState
import com.rpeters.cinefintv.ui.components.TvScreenTopFocusAnchor
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(
    onPlay: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenPerson: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var focusedDescription by remember { mutableStateOf<String?>(null) }
    var focusedBackdropUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val performanceProfile = LocalPerformanceProfile.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val spacing = LocalCinefinSpacing.current

    // Refresh data when screen becomes active
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "DetailContent"
    ) { state ->
        when (state) {
            is DetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Loading details...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            is DetailUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
                ) {
                    Text(text = "Detail could not load", style = MaterialTheme.typography.headlineLarge)
                    Text(text = state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = viewModel::load) { Text("Retry") }
                        OutlinedButton(onClick = onBack) { Text("Back") }
                    }
                }
            }

            is DetailUiState.Content -> {
                val item = state.item
                val playButtonRequester = remember { FocusRequester() }
                val primaryShelfRequester = remember { FocusRequester() }
                val castShelfRequester = remember { FocusRequester() }
                val relatedShelfRequester = remember { FocusRequester() }
                val topAnchorRequester = remember { FocusRequester() }
                
                val firstShelfRequester = when {
                    state.seasons.isNotEmpty() || state.episodesBySeasonId.isNotEmpty() -> primaryShelfRequester
                    state.cast.isNotEmpty() -> castShelfRequester
                    state.related.isNotEmpty() -> relatedShelfRequester
                    else -> null
                }

                // Focus top anchor on initial load to ensure we start at the top
                val screenFocus = remember(topAnchorRequester, playButtonRequester) {
                    TvScreenFocusState(
                        topAnchorRequester = topAnchorRequester,
                        primaryContentRequester = playButtonRequester,
                    )
                }

                RequestScreenFocus(
                    key = state.item.id,
                    requester = screenFocus.topAnchorRequester,
                )
                androidx.compose.runtime.LaunchedEffect(state.item.id) {
                    focusedBackdropUrl = null
                }

                androidx.compose.runtime.LaunchedEffect(state.isDeleted) {
                    if (state.isDeleted) onBack()
                }

                BackHandler(onBack = onBack)

                val listState = rememberLazyListState()

                Box(modifier = Modifier.fillMaxSize()) {
                    // Backdrop
                    val activeBackdropUrl = focusedBackdropUrl ?: item.backdropUrl
                    AnimatedContent(
                        targetState = activeBackdropUrl,
                        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                        label = "ImmersiveBackdrop"
                    ) { url ->
                        if (url != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(url)
                                    .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                                    .size(1920, 1080)
                                    .build(),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Black.copy(alpha = 0.55f),
                                    0.75f to Color.Black.copy(alpha = 0.88f),
                                    1.0f to Color.Black,
                                ),
                            ),
                        ),
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
                    ) {
                        item {
                            TvScreenTopFocusAnchor(
                                state = screenFocus,
                                onFocused = {
                                    coroutineScope.launch { listState.animateScrollToItem(0) }
                                }
                            )
                        }

                        item { Spacer(Modifier.fillParentMaxHeight(0.22f)) }

                        item {
                            DetailHeroSection(
                                item = item,
                                focusedDescription = focusedDescription
                            )
                        }

                        item {
                            DetailActionRow(
                                state = state,
                                onPlay = onPlay,
                                onBack = onBack,
                                onRequestDelete = viewModel::requestDelete,
                                onConfirmDelete = viewModel::confirmDelete,
                                onCancelDelete = viewModel::cancelDelete,
                                onDismissActionError = viewModel::dismissActionError,
                                playButtonRequester = playButtonRequester,
                                firstShelfRequester = firstShelfRequester,
                                onFocusedDescriptionChange = {
                                    focusedDescription = it
                                    focusedBackdropUrl = null
                                }
                            )
                        }

                        item {
                            DetailShelves(
                                state = state,
                                onOpenItem = onOpenItem,
                                onOpenPerson = onOpenPerson,
                                onFocusedDescriptionChange = { focusedDescription = it },
                                onFocusedPreviewImageChange = { focusedBackdropUrl = it },
                                playButtonRequester = playButtonRequester,
                                primaryShelfRequester = primaryShelfRequester,
                                castShelfRequester = castShelfRequester,
                                relatedShelfRequester = relatedShelfRequester
                            )
                        }

                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}
