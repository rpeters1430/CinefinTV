package com.rpeters.cinefintv.ui.screens.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import com.rpeters.cinefintv.ui.components.TvMediaCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberTvLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh data when screen becomes active (e.g. returning from player)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    when (val state = uiState) {
        is HomeUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Loading home...",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        is HomeUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Home could not load",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::refresh) {
                    Text("Retry")
                }
            }
        }

        is HomeUiState.Content -> {
            Box(modifier = Modifier.fillMaxSize()) {
                TvLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    if (state.featuredItems.isNotEmpty()) {
                        item {
                            FeaturedCarousel(
                                items = state.featuredItems,
                                onMoreInfo = onOpenItem,
                                onPlay = onPlayItem,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }

                    items(
                        state.sections,
                        key = { it.title },
                        contentType = { "Section" }
                    ) { section ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            TvLazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(
                                    section.items,
                                    key = { it.id },
                                    contentType = { "MediaCard" }
                                ) { item ->
                                    TvMediaCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        imageUrl = item.imageUrl,
                                        onClick = { onOpenItem(item) },
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
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedCarousel(
    items: List<HomeCardModel>,
    onMoreInfo: (HomeCardModel) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val performanceProfile = LocalPerformanceProfile.current

    Carousel(
        itemCount = items.size,
        modifier = modifier
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp)),
        autoScrollDurationMillis = 6000L,
    ) { index ->
        val item = items[index]
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(item.backdropUrl ?: item.imageUrl)
                    .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                    // High-quality backdrops for carousel, but capped for memory
                    .size(1280, 720)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.85f),
                                0.55f to Color.Black.copy(alpha = 0.4f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.5f)
                    .padding(horizontal = 40.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val carouselMeta = listOfNotNull(
                    item.year?.toString(),
                    item.runtime,
                    item.officialRating,
                    item.rating?.let { "★ $it" },
                ).joinToString("  ·  ")
                if (carouselMeta.isNotBlank()) {
                    Text(
                        text = carouselMeta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { onPlay(item.id) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(onClick = { onMoreInfo(item) }) {
                        Text("More Info")
                    }
                }
            }
        }
    }
}
