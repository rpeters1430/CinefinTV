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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.RegisterPrimaryScreenFocus
import com.rpeters.cinefintv.ui.components.ScrollFocusAnchor
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
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
    val coroutineScope = rememberCoroutineScope()
    val spacing = LocalCinefinSpacing.current
    
    // Key focus anchors for logical navigation
    val topAnchorRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }

    RegisterPrimaryScreenFocus(
        route = NavRoutes.HOME,
        requester = if ((uiState as? HomeUiState.Content)?.featuredItems?.isNotEmpty() == true) {
            carouselFocusRequester
        } else {
            firstRowFocusRequester
        },
    )

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
                    contentPadding = PaddingValues(top = 8.dp, bottom = spacing.rowGap),
                    verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
                ) {
                    item {
                        ScrollFocusAnchor(
                            modifier = Modifier
                                .focusRequester(topAnchorRequester)
                                .focusProperties {
                                    down = if (state.featuredItems.isNotEmpty()) {
                                        carouselFocusRequester
                                    } else {
                                        firstRowFocusRequester
                                    }
                                },
                            onFocused = {
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                            },
                        )
                    }

                    if (state.featuredItems.isNotEmpty()) {
                        item {
                            FeaturedCarousel(
                                items = state.featuredItems,
                                onMoreInfo = onOpenItem,
                                onPlay = onPlayItem,
                                sectionCount = state.sections.size,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, start = spacing.gutter, end = spacing.gutter)
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
                            firstItemFocusRequester = rowRequester,
                            modifier = Modifier
                                .focusProperties {
                                    if (index == 0) up = topAnchorRequester
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
        autoScrollDurationMillis = 15000L,
        modifier = modifier
            .fillMaxWidth()
            .height(440.dp),
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
                            0.0f to expressiveColors.heroStart.copy(alpha = 0.98f),
                            0.28f to Color.Black.copy(alpha = 0.78f),
                            0.62f to expressiveColors.heroEnd.copy(alpha = 0.4f),
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
                            0.72f to Color.Black.copy(alpha = 0.24f),
                            1.0f to Color.Black.copy(alpha = 0.76f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 40.dp, end = 40.dp, top = 36.dp, bottom = 36.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.chipGap)) {
                CinefinChip(label = "Featured")
                item.officialRating?.let { CinefinChip(label = it) }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            val carouselMeta = listOfNotNull(
                item.year?.toString(),
                item.runtime,
                item.rating?.let { "★ $it" },
            ).joinToString("  •  ")
            if (carouselMeta.isNotBlank()) {
                Text(
                    text = carouselMeta,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = expressiveColors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.description ?: "Browse featured titles from your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.elementGap)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playButtonRequester)
                ) {
                    Text("Play")
                }
                OutlinedButton(onClick = onMoreInfo) {
                    Text("Details")
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<HomeCardModel>,
    onOpenItem: (HomeCardModel) -> Unit,
    firstItemFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current

    Column(modifier = modifier) {
        CinefinShelfTitle(
            title = title,
            eyebrow = null,
            modifier = Modifier
                .padding(bottom = spacing.elementGap, start = spacing.gutter, end = spacing.gutter),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
            contentPadding = PaddingValues(
                start = spacing.gutter + 24.dp,
                end = spacing.gutter + 24.dp,
            ),
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
                    modifier = if (index == 0) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}
