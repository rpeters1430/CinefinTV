package com.rpeters.cinefintv.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val spacing = LocalCinefinSpacing.current
    
    // Key focus anchors for logical navigation
    val carouselFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }

    when (val state = uiState) {
        HomeUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading Home...",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        is HomeUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
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

            // Ensure first load focus is intentional
            LaunchedEffect(state) {
                if (state.featuredItems.isNotEmpty()) {
                    carouselFocusRequester.requestFocus()
                } else if (state.sections.isNotEmpty()) {
                    firstRowFocusRequester.requestFocus()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.rowGap),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
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
                                    .padding(top = 8.dp)
                                    .focusRequester(carouselFocusRequester)
                                    .focusProperties {
                                        down = firstRowFocusRequester
                                    },
                            )
                        }
                    }

                    itemsIndexed(
                        state.sections,
                        key = { _, section -> section.title }
                    ) { index, section ->
                        val rowRequester = if (index == 0) firstRowFocusRequester else sectionFocusRequesters[index]
                        
                        HomeSection(
                            title = section.title,
                            items = section.items,
                            onOpenItem = onOpenItem,
                            modifier = Modifier
                                .focusRequester(rowRequester)
                                .focusProperties {
                                    if (index == 0) up = carouselFocusRequester
                                    if (index > 0) up = sectionFocusRequesters[index - 1]
                                    if (index < state.sections.size - 1) down = sectionFocusRequesters[index + 1]
                                },
                        )
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
    val carouselState = rememberCarouselState()
    
    Carousel(
        itemCount = items.size,
        carouselState = carouselState,
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp),
    ) { index ->
        val item = items[index]
        HeroItem(
            item = item,
            sectionCount = sectionCount,
            onMoreInfo = { onMoreInfo(item) },
            onPlay = { onPlay(item.id) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroItem(
    item: HomeCardModel,
    sectionCount: Int,
    onMoreInfo: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val performanceProfile = LocalPerformanceProfile.current
    val spacing = LocalCinefinSpacing.current
    val playButtonRequester = remember { FocusRequester() }

    Box(modifier = modifier.clip(RoundedCornerShape(spacing.cornerContainer))) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.backdropUrl ?: item.imageUrl)
                .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
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
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.chipGap)) {
                CinefinChip(label = "Featured", strong = true)
                CinefinChip(label = "$sectionCount Collections")
                item.officialRating?.let { CinefinChip(label = it) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.elementGap)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playButtonRequester)
                ) {
                    Text("Play Now")
                }
                Button(onClick = onMoreInfo) {
                    Text("More Info")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeSection(
    title: String,
    items: List<HomeCardModel>,
    onOpenItem: (HomeCardModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val firstItemRequester = remember { FocusRequester() }
    
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = spacing.elementGap),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TvMediaCard(
                    title = item.title,
                    subtitle = item.subtitle ?: item.year?.toString(),
                    imageUrl = item.imageUrl,
                    onClick = { onOpenItem(item) },
                    watchStatus = item.watchStatus,
                    unwatchedCount = item.unwatchedCount,
                    playbackProgress = item.playbackProgress,
                    modifier = if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier
                )
            }
        }
    }
}
