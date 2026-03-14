package com.rpeters.cinefintv.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val expressiveColors = LocalCinefinExpressiveColors.current

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
            val sectionFocusRequesters = remember(state.sections.size) {
                List(state.sections.size) { FocusRequester() }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
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
                                sectionCount = state.sections.size,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }

                    itemsIndexed(
                        state.sections,
                        key = { _, section -> section.title },
                        contentType = { _, _ -> "Section" }
                    ) { sectionIndex, section ->
                        val lazyColumnIndex = sectionIndex + if (state.featuredItems.isNotEmpty()) 1 else 0
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            HomeSectionHeader(
                                title = section.title,
                                itemCount = section.items.size,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                itemsIndexed(
                                    section.items,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> "MediaCard" }
                                ) { itemIndex, item ->
                                    val sectionRequester = sectionFocusRequesters[sectionIndex]
                                    val upRequester = sectionFocusRequesters.getOrNull(sectionIndex - 1)
                                    val downRequester = sectionFocusRequesters.getOrNull(sectionIndex + 1)
                                    TvMediaCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        imageUrl = item.imageUrl,
                                        onClick = { onOpenItem(item) },
                                        onFocus = {
                                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                                            if (visibleItems.isEmpty()) return@TvMediaCard

                                            val firstVisible = visibleItems.first().index
                                            val lastVisible = visibleItems.last().index
                                            val shouldNudgeDown = lazyColumnIndex >= lastVisible - 1
                                            val shouldNudgeUp = lazyColumnIndex < firstVisible
                                            val targetIndex = when {
                                                shouldNudgeDown -> lazyColumnIndex
                                                shouldNudgeUp -> lazyColumnIndex.coerceAtLeast(0)
                                                else -> -1
                                            }

                                            if (targetIndex >= 0 &&
                                                listState.firstVisibleItemIndex != targetIndex
                                            ) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(targetIndex)
                                                }
                                            }
                                        },
                                        watchStatus = item.watchStatus,
                                        playbackProgress = item.playbackProgress,
                                        unwatchedCount = item.unwatchedCount,
                                        modifier = Modifier
                                            .then(
                                                if (itemIndex == 0) {
                                                    Modifier.focusRequester(sectionRequester)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .focusProperties {
                                                upRequester?.let { up = it }
                                                downRequester?.let { down = it }
                                            }
                                            .border(
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = expressiveColors.borderSubtle.copy(alpha = 0.35f),
                                                ),
                                                shape = RoundedCornerShape(18.dp),
                                            ),
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
    sectionCount: Int,
    modifier: Modifier = Modifier,
) {
    val performanceProfile = LocalPerformanceProfile.current
    val expressiveColors = LocalCinefinExpressiveColors.current

    Carousel(
        itemCount = items.size,
        modifier = modifier
            .height(430.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(
                border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(28.dp),
            ),
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
                                0.0f to expressiveColors.heroStart.copy(alpha = 0.96f),
                                0.38f to Color.Black.copy(alpha = 0.76f),
                                0.68f to expressiveColors.heroEnd.copy(alpha = 0.35f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.75f to Color.Black.copy(alpha = 0.18f),
                                1.0f to Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.56f)
                    .padding(horizontal = 44.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroChip(label = "Featured")
                    HeroChip(label = "$sectionCount Collections", strong = false)
                    item.officialRating?.let { HeroChip(label = it, strong = false) }
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
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
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = expressiveColors.titleAccent,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSectionHeader(
    title: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                expressiveColors.focusRing,
                                expressiveColors.titleAccent,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.Black,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "$itemCount titles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Explore",
                style = MaterialTheme.typography.labelLarge,
                color = expressiveColors.titleAccent,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = expressiveColors.titleAccent,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroChip(
    label: String,
    strong: Boolean = true,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val backgroundColor = if (strong) expressiveColors.pillStrong else expressiveColors.pillMuted
    val contentColor = if (strong) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(
                border = BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
