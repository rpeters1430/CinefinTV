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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
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
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.RegisterPrimaryContentFocusRequester
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.theme.ThemeSeedColorCache
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import com.rpeters.cinefintv.utils.coerceAlpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HOME_AUTO_REFRESH_INTERVAL_MS = 60_000L

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
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
                    viewModel.refresh(silent = true)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(HOME_AUTO_REFRESH_INTERVAL_MS)
            viewModel.refresh(silent = true)
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onOpenItem = onOpenItem,
        onPlayItem = onPlayItem,
        onOpenSeries = onOpenSeries,
        onOpenSeason = onOpenSeason,
        onRetry = { viewModel.refresh() },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val spacing = LocalCinefinSpacing.current
    val chromeFocusController = LocalAppChromeFocusController.current
    val navUpRequester = chromeFocusController?.topNavFocusRequester
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

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
            val retryFocusRequester = remember { FocusRequester() }
            RegisterPrimaryContentFocusRequester(retryFocusRequester)
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
                    modifier = Modifier
                        .focusRequester(retryFocusRequester)
                        .testTag(HomeTestTags.RetryButton),
                ) {
                    Text("Retry")
                }
            }
        }

        is HomeUiState.Content -> {
            val sectionFocusRequesters = remember(state.sections.size) {
                List(state.sections.size) { FocusRequester() }
            }
            val featuredPrimaryActionRequester = remember { FocusRequester() }
            var lastFocusedSectionTitle by rememberSaveable { mutableStateOf<String?>(null) }
            var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedEpisodeMenuItem by remember { mutableStateOf<HomeCardModel?>(null) }
            var shouldRestoreFocus by rememberSaveable { mutableStateOf(true) }

            val preferredFocusRequester = when {
                lastFocusedSectionTitle != null -> {
                    state.sections.indexOfFirst { it.title == lastFocusedSectionTitle }
                        .takeIf { it >= 0 }
                        ?.let(sectionFocusRequesters::get)
                }
                state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                else -> sectionFocusRequesters.firstOrNull()
            }

            selectedEpisodeMenuItem?.let { item ->
                MediaActionDialog(
                    title = item.title,
                    actions = buildList {
                        add(
                            MediaActionDialogItem(
                                label = "Play episode",
                                supportingText = "Start playback immediately.",
                                onClick = { onPlayItem(item.id) },
                            )
                        )
                        item.seriesId?.let { seriesId ->
                            add(
                                MediaActionDialogItem(
                                    label = "Open show",
                                    supportingText = "Go to the TV show detail screen.",
                                    onClick = { onOpenSeries(seriesId) },
                                )
                            )
                        }
                        item.seasonId?.let { seasonId ->
                            add(
                                MediaActionDialogItem(
                                    label = "Open season",
                                    supportingText = "Go to the season detail screen.",
                                    onClick = { onOpenSeason(seasonId) },
                                )
                            )
                        }
                        add(
                            MediaActionDialogItem(
                                label = "Open item",
                                supportingText = "Open the default destination for this card.",
                                onClick = { onOpenItem(item) },
                            )
                        )
                    },
                    onDismissRequest = { selectedEpisodeMenuItem = null },
                )
            }

            RegisterPrimaryContentFocusRequester(
                when {
                    state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                    sectionFocusRequesters.isNotEmpty() -> sectionFocusRequesters.first()
                    else -> null
                }
            )

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        shouldRestoreFocus = true
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(
                shouldRestoreFocus,
                preferredFocusRequester,
                state.featuredItems.size,
                state.sections.size,
            ) {
                if (!shouldRestoreFocus || preferredFocusRequester == null) {
                    return@LaunchedEffect
                }

                withFrameNanos { }
                runCatching { preferredFocusRequester.requestFocus() }
                shouldRestoreFocus = false
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
                ) {
                    val navigateToFirstSection: (() -> Unit)? =
                        if (sectionFocusRequesters.isNotEmpty()) {
                            {
                                coroutineScope.launch {
                                    val targetIndex = if (state.featuredItems.isNotEmpty()) 1 else 0
                                    listState.animateScrollToItem(targetIndex)
                                    withFrameNanos {}
                                    runCatching { sectionFocusRequesters[0].requestFocus() }
                                }
                            }
                        } else null

                    if (state.featuredItems.isNotEmpty()) {
                        item {
                            FeaturedCarousel(
                                items = state.featuredItems,
                                onMoreInfo = onOpenItem,
                                onPlay = onPlayItem,
                                primaryActionFocusRequester = featuredPrimaryActionRequester,
                                upRequester = navUpRequester,
                                onNavigateDown = navigateToFirstSection,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 0.dp, start = spacing.gutter, end = spacing.gutter),
                            )
                        }
                    }

                    itemsIndexed(
                        state.sections,
                        key = { _, section -> section.title }
                    ) { index, section ->
                        val upRequester = when {
                            index == 0 && state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                            index == 0 -> navUpRequester
                            else -> sectionFocusRequesters[index - 1]
                        }

                        HomeSection(
                            sectionIndex = index,
                            title = section.title,
                            items = section.items,
                            onOpenItem = onOpenItem,
                            restoredFocusedItemId = if (lastFocusedSectionTitle == section.title) {
                                lastFocusedItemId
                            } else {
                                null
                            },
                            onItemFocused = { itemId ->
                                lastFocusedSectionTitle = section.title
                                lastFocusedItemId = itemId
                                val listIndex = if (state.featuredItems.isNotEmpty()) index + 1 else index
                                val isSectionVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIndex }
                                if (!isSectionVisible) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(listIndex)
                                    }
                                }
                            },
                            onEpisodeMenuRequested = { selectedEpisodeMenuItem = it },
                            firstItemFocusRequester = sectionFocusRequesters[index],
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
    primaryActionFocusRequester: FocusRequester,
    upRequester: FocusRequester?,
    onNavigateDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState()

    Carousel(
        itemCount = items.size,
        carouselState = carouselState,
        autoScrollDurationMillis = 15000L,
        modifier = modifier
            .testTag(HomeTestTags.FeaturedCarousel)
            .fillMaxWidth()
            .height(376.dp),
    ) { index ->
        val item = items[index]
        HeroItem(
            item = item,
            onMoreInfo = { onMoreInfo(item) },
            onPlay = { onPlay(item.id) },
            primaryActionFocusRequester = primaryActionFocusRequester,
            upRequester = upRequester,
            onNavigateDown = onNavigateDown,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroItem(
    item: HomeCardModel,
    onMoreInfo: () -> Unit,
    onPlay: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    upRequester: FocusRequester?,
    onNavigateDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val performanceProfile = LocalPerformanceProfile.current
    val spacing = LocalCinefinSpacing.current
    val themeController = LocalCinefinThemeController.current
    val detailsButtonRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val heroImageUrl = item.backdropUrl ?: item.imageUrl
    val shouldExtractPalette = performanceProfile.tier == DevicePerformanceProfile.Tier.HIGH
    val cachedSeedColor = ThemeSeedColorCache.getCached(heroImageUrl)

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
                if (!shouldExtractPalette || heroImageUrl == null) {
                    return@AsyncImage
                }
                if (ThemeSeedColorCache.getCached(heroImageUrl) != null) {
                    return@AsyncImage
                }

                coroutineScope.launch {
                    ThemeSeedColorCache.getOrExtract(heroImageUrl) {
                        state.result.image.toBitmap()
                    }?.let(themeController::updateSeedColor)
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
                            0.0f to Color.Black.copy(alpha = 0.85f),
                            0.35f to Color.Black.copy(alpha = 0.45f),
                            0.65f to Color.Transparent,
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
                            0.65f to Color.Black.copy(alpha = 0.35f),
                            1.0f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )
        // Content glass panel
        val onBackgroundColor = MaterialTheme.colorScheme.onBackground
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
                .widthIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                contentColor = onBackgroundColor
            ),
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    Color.White.copy(alpha = 0.15f)
                )
            ),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CinefinChip(label = "Featured", strong = true)
                    item.officialRating?.let { CinefinChip(label = it) }
                    item.mediaQuality?.let { 
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = expressiveColors.titleAccent.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag(HomeTestTags.FeaturedTitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val carouselMeta = listOfNotNull(
                    item.year?.toString(),
                    item.runtime,
                    item.rating?.let { "★ $it" },
                ).joinToString("  •  ")
                if (carouselMeta.isNotBlank()) {
                    Text(
                        text = carouselMeta,
                        style = MaterialTheme.typography.titleMedium,
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
                    text = item.description ?: "Featured title from your library",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing.elementGap)
                ) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier
                            .focusRequester(primaryActionFocusRequester)
                            .focusProperties {
                                upRequester?.let { up = it }
                                right = detailsButtonRequester
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    onNavigateDown?.invoke()
                                    onNavigateDown != null
                                } else false
                            }
                            .testTag(HomeTestTags.FeaturedPlayButton),
                        scale = ButtonDefaults.scale(focusedScale = 1.1f),
                    ) {
                        Text("Play", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = onMoreInfo,
                        modifier = Modifier
                            .focusRequester(detailsButtonRequester)
                            .focusProperties {
                                upRequester?.let { up = it }
                                left = primaryActionFocusRequester
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                    onNavigateDown?.invoke()
                                    onNavigateDown != null
                                } else false
                            }
                            .testTag(HomeTestTags.FeaturedDetailsButton),
                        scale = ButtonDefaults.scale(focusedScale = 1.1f),
                    ) {
                        Text("Details", style = MaterialTheme.typography.titleMedium)
                    }
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
    restoredFocusedItemId: String?,
    onItemFocused: (String) -> Unit,
    onEpisodeMenuRequested: (HomeCardModel) -> Unit,
    firstItemFocusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val visibleItems = items.take(12)
    val restoredFocusIndex = visibleItems.indexOfFirst { it.id == restoredFocusedItemId }
        .takeIf { it >= 0 } ?: 0

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
                    val focusModifier = Modifier.focusProperties {
                        upRequester?.let { up = it }
                        downRequester?.let { down = it }
                    }
                    TvMediaCard(
                        title = item.title,
                        subtitle = item.subtitle ?: item.year?.toString(),
                        imageUrl = item.imageUrl,
                        onClick = { onOpenItem(item) },
                        onMenuAction = if (item.itemType == "Episode") {
                            { onEpisodeMenuRequested(item) }
                        } else {
                            null
                        },
                        watchStatus = item.watchStatus,
                        unwatchedCount = item.unwatchedCount,
                        playbackProgress = item.playbackProgress,
                        onFocus = { onItemFocused(item.id) },
                        aspectRatio = 16f / 9f,
                        cardWidth = cardWidth,
                        modifier = if (index == restoredFocusIndex) {
                            Modifier
                                .testTag(HomeTestTags.sectionItem(sectionIndex, index))
                                .focusRequester(firstItemFocusRequester)
                                .then(focusModifier)
                        } else {
                            Modifier
                                .testTag(HomeTestTags.sectionItem(sectionIndex, index))
                                .then(focusModifier)
                        }
                    )
                }
            }
        }
    }
}
