package com.rpeters.cinefintv.ui.screens.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.components.ImmersiveBackground
import com.rpeters.cinefintv.ui.navigation.FocusNavigationCoordinator
import com.rpeters.cinefintv.ui.navigation.LocalFocusNavigationCoordinator
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.CinefinMotion
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HOME_AUTO_REFRESH_INTERVAL_MS = 60_000L
private const val HOME_FOCUS_RESTORE_SETTLE_MS = 1_000L
private const val HOME_RESUME_REFRESH_THRESHOLD_MS = 30_000L

private val NAV_RAIL_SLOT_WIDTH = 216.dp  // 196dp rail + 12dp start padding + 8dp content host start padding (matches CinefinAppScaffold)

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
    var lastPausedAtMs by remember { mutableLongStateOf(0L) }
    var shouldRestoreFocusOnResume by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> {
                    hasBeenPaused = true
                    lastPausedAtMs = SystemClock.elapsedRealtime()
                }
                Lifecycle.Event.ON_RESUME -> if (hasBeenPaused) {
                    hasBeenPaused = false
                    shouldRestoreFocusOnResume = true
                    val pausedDurationMs = SystemClock.elapsedRealtime() - lastPausedAtMs
                    if (pausedDurationMs >= HOME_RESUME_REFRESH_THRESHOLD_MS) {
                        viewModel.refresh(silent = true, forceRefresh = true)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(HOME_AUTO_REFRESH_INTERVAL_MS)
                viewModel.refresh(silent = true, forceRefresh = true)
            }
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onOpenItem = onOpenItem,
        onPlayItem = onPlayItem,
        onOpenSeries = onOpenSeries,
        onOpenSeason = onOpenSeason,
        onRetry = { viewModel.refresh(forceRefresh = true) },
        shouldRestoreFocusOnResume = shouldRestoreFocusOnResume,
        onConsumedRestore = { shouldRestoreFocusOnResume = false }
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
    shouldRestoreFocusOnResume: Boolean,
    onConsumedRestore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val spacing = LocalCinefinSpacing.current
    val density = LocalDensity.current
    val focusVisibilityTopBufferPx = remember(density) { with(density) { 72.dp.roundToPx() } }
    val focusVisibilityBottomBufferPx = remember(density) { with(density) { 128.dp.roundToPx() } }
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
            val destinationFocus = rememberTopLevelDestinationFocus(retryFocusRequester)
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
                        .then(destinationFocus.primaryContentModifier())
                        .testTag(HomeTestTags.RetryButton),
                ) {
                    Text("Retry")
                }
            }
        }

        is HomeUiState.Content -> {
            HomeLoadedContent(
                state = state,
                listState = listState,
                focusVisibilityTopBufferPx = focusVisibilityTopBufferPx,
                focusVisibilityBottomBufferPx = focusVisibilityBottomBufferPx,
                spacing = spacing,
                lifecycleOwner = lifecycleOwner,
                coroutineScope = coroutineScope,
                onOpenItem = onOpenItem,
                onPlayItem = onPlayItem,
                onOpenSeries = onOpenSeries,
                onOpenSeason = onOpenSeason,
                shouldRestoreFocusOnResume = shouldRestoreFocusOnResume,
                onConsumedRestore = onConsumedRestore,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeLoadedContent(
    state: HomeUiState.Content,
    listState: LazyListState,
    focusVisibilityTopBufferPx: Int,
    focusVisibilityBottomBufferPx: Int,
    spacing: com.rpeters.cinefintv.ui.theme.CinefinSpacing,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onOpenItem: (HomeCardModel) -> Unit,
    onPlayItem: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    shouldRestoreFocusOnResume: Boolean,
    onConsumedRestore: () -> Unit,
) {
    val sectionItemKeys = remember(state.sections) {
        state.sections.map { section ->
            section.id to section.items.take(12).map(HomeCardModel::id)
        }
    }
    val sectionItemFocusRequesters = remember(sectionItemKeys) {
        sectionItemKeys.map { (_, itemIds) ->
            List(itemIds.size) { FocusRequester() }
        }
    }
    val featuredPrimaryActionRequester = remember { FocusRequester() }
    var lastFocusedSectionId by rememberSaveable { mutableStateOf<HomeSectionId?>(null) }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEpisodeMenuItem by remember { mutableStateOf<HomeCardModel?>(null) }
    var focusedItem by remember { mutableStateOf<HomeCardModel?>(null) }
    var shouldRestoreFocus by rememberSaveable { mutableStateOf(true) }
    var positionedInitialFeatured by rememberSaveable { mutableStateOf(false) }
    val focusNavigationCoordinator = LocalFocusNavigationCoordinator.current ?: remember(coroutineScope) {
        FocusNavigationCoordinator(coroutineScope)
    }
    val chromeFocusController = LocalAppChromeFocusController.current

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val availableWidth = screenWidth - NAV_RAIL_SLOT_WIDTH
    val rowSpacing = remember(spacing.cardGap) {
        (spacing.cardGap - 8.dp).coerceAtLeast(10.dp)
    }
    val cardWidth = remember(availableWidth, spacing.gutter, rowSpacing) {
        ((availableWidth - (spacing.gutter * 2) - (rowSpacing * 3)) / 4f)
            .coerceIn(220.dp, 320.dp)
    }

    val preferredFocusRequester = when {
        lastFocusedSectionId != null -> {
            val sectionIndex = state.sections.indexOfFirst { it.id == lastFocusedSectionId }
            val itemIndex = state.sections
                .getOrNull(sectionIndex)
                ?.items
                ?.take(12)
                ?.indexOfFirst { it.id == lastFocusedItemId }
                ?.takeIf { it >= 0 }

            sectionItemFocusRequesters
                .getOrNull(sectionIndex)
                ?.getOrNull(itemIndex ?: 0)
        }
        state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
        else -> sectionItemFocusRequesters.firstOrNull()?.firstOrNull()
    }
    val destinationFocus = rememberTopLevelDestinationFocus(preferredFocusRequester)

    LaunchedEffect(shouldRestoreFocusOnResume) {
        if (shouldRestoreFocusOnResume) {
            shouldRestoreFocus = true
            chromeFocusController?.shouldRestoreFocusToContent = true
            onConsumedRestore()
        }
    }

    HomeEpisodeActionDialog(
        selectedItem = selectedEpisodeMenuItem,
        onPlayItem = onPlayItem,
        onOpenSeries = onOpenSeries,
        onOpenSeason = onOpenSeason,
        onOpenItem = onOpenItem,
        onDismissRequest = { selectedEpisodeMenuItem = null },
    )

    DisposableEffect(lifecycleOwner) {
        onDispose {
            focusNavigationCoordinator.cancelActiveJob()
        }
    }

    LaunchedEffect(state.featuredItems.firstOrNull()?.id) {
        if (positionedInitialFeatured || state.featuredItems.isEmpty()) return@LaunchedEffect

        positionedInitialFeatured = true
        lastFocusedSectionId = null
        lastFocusedItemId = null
        shouldRestoreFocus = false
        focusNavigationCoordinator.cancelActiveJob()
        listState.scrollToItemAndAwaitLayout(0)
        requestFocusAfterLayoutSettles(featuredPrimaryActionRequester)
    }

    LaunchedEffect(shouldRestoreFocus, preferredFocusRequester) {
        if (!shouldRestoreFocus) return@LaunchedEffect

        val fallbackRequester = if (state.featuredItems.isNotEmpty()) {
            featuredPrimaryActionRequester
        } else {
            sectionItemFocusRequesters.firstOrNull()?.firstOrNull()
        }
        val requester = preferredFocusRequester ?: fallbackRequester ?: return@LaunchedEffect
        val targetListIndex = when {
            lastFocusedSectionId != null -> {
                state.sections.indexOfFirst { it.id == lastFocusedSectionId }
                    .takeIf { it >= 0 }
                    ?.let { if (state.featuredItems.isNotEmpty()) it + 1 else it }
            }
            state.featuredItems.isNotEmpty() -> 0
            else -> 0
        }

        if (targetListIndex != null) {
            listState.ensureItemComfortablyVisible(
                index = targetListIndex,
                topBufferPx = focusVisibilityTopBufferPx,
                bottomBufferPx = focusVisibilityBottomBufferPx,
            )
        }
        runCatching { requester.requestFocus() }
        delay(HOME_FOCUS_RESTORE_SETTLE_MS)
        shouldRestoreFocus = false
    }

    fun requestFocusAtListIndex(
        requester: FocusRequester,
        listIndex: Int,
    ): () -> Unit = {
        focusNavigationCoordinator.submit {
            listState.ensureItemComfortablyVisible(
                index = listIndex,
                topBufferPx = focusVisibilityTopBufferPx,
                bottomBufferPx = focusVisibilityBottomBufferPx,
            )
            requestFocusAfterLayoutSettles(requester)
        }
    }

    fun ensureSectionVisible(sectionIndex: Int) {
        val listIndex = if (state.featuredItems.isNotEmpty()) sectionIndex + 1 else sectionIndex
        if (
            !listState.isIndexComfortablyVisible(
                index = listIndex,
                topBufferPx = focusVisibilityTopBufferPx,
                bottomBufferPx = focusVisibilityBottomBufferPx,
            )
        ) {
            focusNavigationCoordinator.submit {
                listState.ensureItemComfortablyVisible(
                    index = listIndex,
                    topBufferPx = focusVisibilityTopBufferPx,
                    bottomBufferPx = focusVisibilityBottomBufferPx,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        ImmersiveBackground(backdropUrl = focusedItem?.backdropUrl ?: focusedItem?.imageUrl)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (state.featuredItems.isNotEmpty()) {
                item {
                    val firstSectionRequester = sectionItemFocusRequesters.firstOrNull()?.firstOrNull()
                    FeaturedCarousel(
                        items = state.featuredItems,
                        onMoreInfo = onOpenItem,
                        onPlay = onPlayItem,
                        onItemFocused = { focusedItem = it },
                        destinationFocus = destinationFocus,
                        primaryActionFocusRequester = featuredPrimaryActionRequester,
                        downRequester = firstSectionRequester,
                        onNavigateDown = firstSectionRequester?.let { requester ->
                            requestFocusAtListIndex(requester = requester, listIndex = 1)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp),
                    )
                }
            }

            itemsIndexed(
                state.sections,
                key = { _, section -> section.id }
            ) { index, section ->
                val upFocusRequester = when {
                    index == 0 && state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                    index == 0 -> destinationFocus.drawerFocusRequester
                    else -> sectionItemFocusRequesters.getOrNull(index - 1)?.firstOrNull()
                }
                val previousSectionFocusRequester = sectionItemFocusRequesters
                    .getOrNull(index - 1)
                    ?.firstOrNull()

                HomeSection(
                    sectionIndex = index,
                    title = section.title,
                    items = section.items,
                    cardWidth = cardWidth,
                    rowSpacing = rowSpacing,
                    onOpenItem = onOpenItem,
                    restoredFocusedItemId = if (lastFocusedSectionId == section.id) {
                        lastFocusedItemId
                    } else {
                        null
                    },
                    onItemFocused = { item ->
                        lastFocusedSectionId = section.id
                        lastFocusedItemId = item.id
                        focusedItem = item
                        ensureSectionVisible(index)
                    },
                    onEpisodeMenuRequested = { selectedEpisodeMenuItem = it },
                    itemFocusRequesters = sectionItemFocusRequesters.getOrNull(index) ?: emptyList(),
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = sectionItemFocusRequesters.getOrNull(index + 1)?.firstOrNull(),
                    onNavigateUp = when {
                        index == 0 && state.featuredItems.isNotEmpty() -> {
                            requestFocusAtListIndex(
                                requester = featuredPrimaryActionRequester,
                                listIndex = 0,
                            )
                        }
                        previousSectionFocusRequester != null -> {
                            requestFocusAtListIndex(
                                requester = previousSectionFocusRequester,
                                listIndex = if (state.featuredItems.isNotEmpty()) index else index - 1,
                            )
                        }
                        else -> null
                    },
                    onNavigateDown = sectionItemFocusRequesters.getOrNull(index + 1)?.firstOrNull()?.let { nextRequester ->
                        requestFocusAtListIndex(
                            requester = nextRequester,
                            listIndex = if (state.featuredItems.isNotEmpty()) index + 2 else index + 1,
                        )
                    },
                    destinationFocus = destinationFocus,
                )
            }
        }
    }
}

@Composable
private fun HomeEpisodeActionDialog(
    selectedItem: HomeCardModel?,
    onPlayItem: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenItem: (HomeCardModel) -> Unit,
    onDismissRequest: () -> Unit,
) {
    selectedItem?.let { item ->
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
            onDismissRequest = onDismissRequest,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedCarousel(
    items: List<HomeCardModel>,
    onMoreInfo: (HomeCardModel) -> Unit,
    onPlay: (String) -> Unit,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    primaryActionFocusRequester: FocusRequester,
    onItemFocused: (HomeCardModel) -> Unit,
    downRequester: FocusRequester?,
    onNavigateDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val carouselState = rememberCarouselState()
    val performanceProfile = LocalPerformanceProfile.current
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(carouselState.activeItemIndex, items) {
        items.getOrNull(carouselState.activeItemIndex)?.let {
            onItemFocused(it)
        }
    }

    Carousel(
        itemCount = items.size,
        carouselState = carouselState,
        autoScrollDurationMillis = when {
            isFocused -> Long.MAX_VALUE
            items.size <= 1 -> Long.MAX_VALUE
            performanceProfile.tier == DevicePerformanceProfile.Tier.LOW -> 25000L
            else -> 15000L
        },
        modifier = modifier
            .testTag(HomeTestTags.FeaturedCarousel)
            .fillMaxWidth()
            .height(512.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .onPreviewKeyEvent { keyEvent ->
                consumeDirectionalPress(
                    keyEvent = keyEvent,
                    direction = Key.DirectionDown,
                    onNavigate = onNavigateDown,
                )
            },
        carouselIndicator = {
            CarouselDefaults.IndicatorRow(
                itemCount = items.size,
                activeItemIndex = carouselState.activeItemIndex,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 64.dp, bottom = 44.dp),
            )
        },
    ) { index ->
        val item = items[index]
        val fallbackRequester = remember { FocusRequester() }
        HeroItem(
            item = item,
            onMoreInfo = { onMoreInfo(item) },
            onPlay = { onPlay(item.id) },
            destinationFocus = destinationFocus,
            primaryActionFocusRequester = if (index == carouselState.activeItemIndex) {
                primaryActionFocusRequester
            } else {
                fallbackRequester
            },
            downRequester = downRequester,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroItem(
    item: HomeCardModel,
    onMoreInfo: () -> Unit,
    onPlay: () -> Unit,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    primaryActionFocusRequester: FocusRequester,
    downRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val performanceProfile = LocalPerformanceProfile.current
    val spacing = LocalCinefinSpacing.current
    val detailsButtonRequester = remember { FocusRequester() }
    var playButtonFocused by remember { mutableStateOf<Boolean>(false) }
    var detailsButtonFocused by remember { mutableStateOf<Boolean>(false) }

    val playButtonScale by animateFloatAsState(
        targetValue = if (playButtonFocused) 1.08f else 1f,
        animationSpec = tween(
            durationMillis = CinefinMotion.DurationMedium,
            easing = CinefinMotion.PremiumOvershoot
        ),
        label = "HeroPlayButtonScale"
    )

    val detailsButtonScale by animateFloatAsState(
        targetValue = if (detailsButtonFocused) 1.08f else 1f,
        animationSpec = tween(
            durationMillis = CinefinMotion.DurationMedium,
            easing = CinefinMotion.PremiumOvershoot
        ),
        label = "HeroDetailsButtonScale"
    )

    val heroImageUrl = item.backdropUrl ?: item.imageUrl
    val heroImageRequest = remember(heroImageUrl, performanceProfile.tier, context) {
        ImageRequest.Builder(context)
            .data(heroImageUrl)
            .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
            .size(
                if (performanceProfile.tier == DevicePerformanceProfile.Tier.LOW) 960 else 1280,
                if (performanceProfile.tier == DevicePerformanceProfile.Tier.LOW) 540 else 720,
            )
            .build()
    }
    val carouselMeta = remember(item.year, item.runtime, item.rating) {
        listOfNotNull(
            item.year?.toString(),
            item.runtime,
            item.rating?.let { "★ $it" },
        ).joinToString("  •  ")
    }
    val heroDescription = remember(item.description) {
        item.description ?: "Featured title from your library"
    }

    Box(
        modifier = modifier,
    ) {
        AsyncImage(
            model = heroImageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Horizontal scrim for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.52f),
                            0.3f to Color.Black.copy(alpha = 0.22f),
                            0.6f to Color.Transparent,
                        ),
                    ),
                ),
        )
        // Vertical scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.7f to Color.Black.copy(alpha = 0.22f),
                            1.0f to Color.Black.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, end = 40.dp, bottom = 64.dp)
                .widthIn(max = 660.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CinefinChip(label = "Featured", strong = true)
                item.officialRating?.let { CinefinChip(label = it) }
                item.mediaQuality?.let { CinefinChip(label = it) }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    fontSize = 44.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag(HomeTestTags.FeaturedTitle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (carouselMeta.isNotBlank()) {
                Text(
                    text = carouselMeta,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
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
                text = heroDescription,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .blockBringIntoView()
                        .onFocusChanged { state: androidx.compose.ui.focus.FocusState -> playButtonFocused = state.isFocused }
                        .graphicsLayer {
                            scaleX = playButtonScale
                            scaleY = playButtonScale
                        }
                        .then(destinationFocus.primaryContentModifier(
                            down = downRequester,
                            right = detailsButtonRequester,
                        ))
                        .testTag(HomeTestTags.FeaturedPlayButton),
                    shape = ButtonDefaults.shape(RoundedCornerShape(4.dp)),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    border = ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                Color.White.copy(alpha = 0.5f)
                            )
                        )
                    )
                ) {
                    Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                OutlinedButton(
                    onClick = onMoreInfo,
                    modifier = Modifier
                        .blockBringIntoView()
                        .focusRequester(detailsButtonRequester)
                        .onFocusChanged { state: androidx.compose.ui.focus.FocusState -> detailsButtonFocused = state.isFocused }
                        .graphicsLayer {
                            scaleX = detailsButtonScale
                            scaleY = detailsButtonScale
                        }
                        .then(destinationFocus.drawerEscapeModifier(
                            down = downRequester,
                            left = primaryActionFocusRequester,
                        ))
                        .testTag(HomeTestTags.FeaturedDetailsButton),
                    shape = ButtonDefaults.shape(RoundedCornerShape(4.dp)),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White.copy(alpha = 0.22f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    border = ButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.15f)
                            )
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                Color.White.copy(alpha = 0.8f)
                            )
                        )
                    )
                ) {
                    Text("Details", style = MaterialTheme.typography.titleMedium)
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
    cardWidth: Dp,
    rowSpacing: Dp,
    onOpenItem: (HomeCardModel) -> Unit,
    restoredFocusedItemId: String?,
    onItemFocused: (HomeCardModel) -> Unit,
    onEpisodeMenuRequested: (HomeCardModel) -> Unit,
    itemFocusRequesters: List<FocusRequester>,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    onNavigateUp: (() -> Unit)?,
    onNavigateDown: (() -> Unit)?,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val visibleItems = remember(items) { items.take(12) }
    val restoredFocusIndex = visibleItems.indexOfFirst { it.id == restoredFocusedItemId }
        .takeIf { it >= 0 } ?: 0

    Column(
        modifier = modifier.testTag(HomeTestTags.section(sectionIndex)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CinefinShelfTitle(
            title = title,
            modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
            contentPadding = PaddingValues(horizontal = spacing.gutter),
        ) {
            itemsIndexed(
                items = visibleItems,
                key = { _, item -> item.id },
                contentType = { _, item -> item.itemType ?: "media" },
            ) { index, item ->
                val focusModifier = destinationFocus.drawerEscapeModifier(
                    isLeftEdge = index == 0,
                    up = upFocusRequester,
                    down = downFocusRequester,
                )

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
                    onFocus = { onItemFocused(item) },
                    aspectRatio = 16f / 9f,
                    cardWidth = cardWidth,
                    modifier = Modifier
                        .testTag(HomeTestTags.sectionItem(sectionIndex, index))
                        .then(
                            if (index == 0 || index == restoredFocusIndex) {
                                Modifier.blockBringIntoView()
                            } else {
                                Modifier
                            }
                        )
                        .then(itemFocusRequesters.getOrNull(index)?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .then(focusModifier)
                        .onPreviewKeyEvent { keyEvent ->
                            consumeDirectionalPress(
                                keyEvent = keyEvent,
                                direction = Key.DirectionDown,
                                onNavigate = onNavigateDown,
                            ) || consumeDirectionalPress(
                                keyEvent = keyEvent,
                                direction = Key.DirectionUp,
                                onNavigate = onNavigateUp,
                            )
                        }
                )
            }
        }
    }
}


private fun consumeDirectionalPress(
    keyEvent: KeyEvent,
    direction: Key,
    onNavigate: (() -> Unit)?,
): Boolean {
    if (onNavigate == null || keyEvent.type != KeyEventType.KeyDown || keyEvent.key != direction) {
        return false
    }

    onNavigate()
    return true
}

private suspend fun requestFocusAfterLayoutSettles(focusRequester: FocusRequester) {
    repeat(2) { withFrameNanos { } }
    repeat(3) { attempt ->
        if (runCatching { focusRequester.requestFocus() }.isSuccess) {
            return
        }
        if (attempt < 2) {
            withFrameNanos { }
        }
    }
}

private fun LazyListState.isIndexVisible(index: Int): Boolean =
    layoutInfo.visibleItemsInfo.any { it.index == index }

private fun LazyListState.isIndexComfortablyVisible(
    index: Int,
    topBufferPx: Int,
    bottomBufferPx: Int,
): Boolean {
    val target = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    val viewportTop = layoutInfo.viewportStartOffset + topBufferPx
    val viewportBottom = layoutInfo.viewportEndOffset - bottomBufferPx
    val itemTop = target.offset
    val itemBottom = target.offset + target.size
    return itemTop >= viewportTop && itemBottom <= viewportBottom
}

private suspend fun LazyListState.scrollToItemIfNeeded(index: Int) {
    if (!isIndexVisible(index)) {
        scrollToItem(index)
    }
}

/**
 * Scrolls to [index] if not already visible, then suspends until the item appears
 * in [layoutInfo]. This guarantees the item is laid out before focus is requested,
 * replacing the fragile double-withFrameNanos timing hack.
 */
private suspend fun LazyListState.scrollToItemAndAwaitLayout(index: Int) {
    if (!isIndexVisible(index)) {
        scrollToItem(index)
        snapshotFlow { isIndexVisible(index) }.first { it }
    }
}

private suspend fun LazyListState.ensureItemComfortablyVisible(
    index: Int,
    topBufferPx: Int,
    bottomBufferPx: Int,
) {
    scrollToItemAndAwaitLayout(index)

    val target = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportTop = layoutInfo.viewportStartOffset + topBufferPx
    val viewportBottom = layoutInfo.viewportEndOffset - bottomBufferPx
    val itemTop = target.offset
    val itemBottom = target.offset + target.size

    val scrollDelta = when {
        itemTop < viewportTop -> (itemTop - viewportTop).toFloat()
        itemBottom > viewportBottom -> (itemBottom - viewportBottom).toFloat()
        else -> 0f
    }

    if (scrollDelta != 0f) {
        animateScrollBy(scrollDelta)
    }
}
