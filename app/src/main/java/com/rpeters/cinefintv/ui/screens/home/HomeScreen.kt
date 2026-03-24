package com.rpeters.cinefintv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import androidx.tv.material3.Button
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import com.rpeters.cinefintv.utils.coerceAlpha

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenPaused by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> hasBeenPaused = true
                Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                    hasBeenPaused = false
                    viewModel.refreshWatchStatus()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeScreenContent(
        uiState = uiState,
        onOpenItem = onOpenItem,
        onPlayItem = onPlayItem,
        onRetry = viewModel::refresh,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val spacing = LocalCinefinSpacing.current

    val primaryContentRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }

    when (val state = uiState) {
        HomeUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(HomeTestTags.Loading),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Loading home...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        is HomeUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(spacing.gutter)
                    .testTag(HomeTestTags.Error),
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
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag(HomeTestTags.RetryButton),
                ) {
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
                    contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
                ) {
                    if (state.featuredItems.isNotEmpty()) {
                        item {
                            FeaturedCarousel(
                                items = state.featuredItems,
                                onMoreInfo = onOpenItem,
                                onPlay = onPlayItem,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 0.dp, start = spacing.gutter, end = spacing.gutter)
                                    .focusRequester(primaryContentRequester)
                                    .focusProperties {
                                        if (state.sections.isNotEmpty()) {
                                            down = firstRowFocusRequester
                                        }
                                    },
                            )
                        }
                    }

                    itemsIndexed(
                        state.sections,
                        key = { _, section -> section.title }
                    ) { index, section ->
                        val rowRequester = if (index == 0) {
                            if (state.featuredItems.isEmpty()) primaryContentRequester else firstRowFocusRequester
                        } else {
                            sectionFocusRequesters[index]
                        }

                        val upRequester = when {
                            index == 0 && state.featuredItems.isNotEmpty() -> primaryContentRequester
                            index == 0 -> null
                            index == 1 -> if (state.featuredItems.isEmpty()) primaryContentRequester else firstRowFocusRequester
                            else -> sectionFocusRequesters[index - 1]
                        }

                        HomeSection(
                            sectionIndex = index,
                            title = section.title,
                            items = section.items,
                            onOpenItem = onOpenItem,
                            firstItemFocusRequester = rowRequester,
                            upRequester = upRequester,
                            downRequester = sectionFocusRequesters.getOrNull(index + 1),
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
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState()
    val seedColorCache = remember { mutableStateMapOf<String, Color>() }
    
    Carousel(
        itemCount = items.size,
        carouselState = carouselState,
        autoScrollDurationMillis = 15000L,
        modifier = modifier
            .testTag(HomeTestTags.FeaturedCarousel)
            .fillMaxWidth()
            .height(360.dp),
    ) { index ->
        val item = items[index]
        HeroItem(
            item = item,
            seedColorCache = seedColorCache,
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
    seedColorCache: MutableMap<String, Color>,
    onMoreInfo: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val performanceProfile = LocalPerformanceProfile.current
    val spacing = LocalCinefinSpacing.current
    val themeController = LocalCinefinThemeController.current
    val playButtonRequester = remember { FocusRequester() }
    val heroImageUrl = item.backdropUrl ?: item.imageUrl
    val shouldExtractPalette = performanceProfile.tier == DevicePerformanceProfile.Tier.HIGH
    val cachedSeedColor = heroImageUrl?.let(seedColorCache::get)

    LaunchedEffect(heroImageUrl, cachedSeedColor) {
        if (cachedSeedColor != null) {
            themeController.updateSeedColor(cachedSeedColor)
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(spacing.cornerContainer))) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(heroImageUrl)
                .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                .allowHardware(!shouldExtractPalette)
                .size(1280, 720)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                if (!shouldExtractPalette || heroImageUrl == null || seedColorCache.containsKey(heroImageUrl)) {
                    return@AsyncImage
                }

                val bitmap = state.result.image.toBitmap()
                Palette.from(bitmap).generate { palette ->
                    val color = palette?.vibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb
                    color?.let {
                        val seedColor = androidx.compose.ui.graphics.Color(it)
                        seedColorCache[heroImageUrl] = seedColor
                        themeController.updateSeedColor(seedColor)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to expressiveColors.surfaceContainerHighest.coerceAlpha(0.98f),
                            0.28f to Color.Black.copy(alpha = 0.78f),
                            0.62f to expressiveColors.surfaceContainer.coerceAlpha(0.4f),
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
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag(HomeTestTags.FeaturedTitle),
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
                text = item.mediaQuality ?: item.description ?: "Featured title from your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.elementGap)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(playButtonRequester)
                        .testTag(HomeTestTags.FeaturedPlayButton)
                ) {
                    Text("Play")
                }
                OutlinedButton(
                    onClick = onMoreInfo,
                    modifier = Modifier.testTag(HomeTestTags.FeaturedDetailsButton),
                ) {
                    Text("Details")
                }
            }
        }
    }
}

@Composable
private fun HomeSection(
    sectionIndex: Int,
    title: String,
    items: List<HomeCardModel>,
    onOpenItem: (HomeCardModel) -> Unit,
    firstItemFocusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val visibleItems = items.take(12)

    Column(
        modifier = modifier.testTag(HomeTestTags.section(sectionIndex)),
    ) {
        CinefinShelfTitle(
            title = title,
            eyebrow = null,
            modifier = Modifier
                .padding(bottom = spacing.elementGap, start = spacing.gutter, end = spacing.gutter),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = ((maxWidth - (spacing.gutter * 2) - (spacing.cardGap * 2)) / 3f)
                .coerceIn(220.dp, 420.dp)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                contentPadding = PaddingValues(horizontal = spacing.gutter),
            ) {
                itemsIndexed(visibleItems, key = { _, item -> item.id }) { index, item ->
                    TvMediaCard(
                        title = item.title,
                        subtitle = item.subtitle ?: item.year?.toString(),
                        imageUrl = item.imageUrl,
                        onClick = { onOpenItem(item) },
                        watchStatus = item.watchStatus,
                        unwatchedCount = item.unwatchedCount,
                        playbackProgress = item.playbackProgress,
                        aspectRatio = 16f / 9f,
                        cardWidth = cardWidth,
                        modifier = if (index == 0) {
                            Modifier
                                .testTag(HomeTestTags.sectionItem(sectionIndex, index))
                                .focusRequester(firstItemFocusRequester)
                                .focusProperties {
                                    upRequester?.let { up = it }
                                    downRequester?.let { down = it }
                                }
                        } else {
                            Modifier.testTag(HomeTestTags.sectionItem(sectionIndex, index))
                        }
                    )
                }
            }
        }
    }
}
