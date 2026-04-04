package com.rpeters.cinefintv.ui.screens.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
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
        hasBeenPaused = hasBeenPaused
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
    hasBeenPaused: Boolean
) {
    val listState = rememberLazyListState()
    val spacing = LocalCinefinSpacing.current
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
            val sectionFocusRequesters = remember(state.sections.size) {
                List(state.sections.size) { FocusRequester() }
            }
            val featuredPrimaryActionRequester = remember { FocusRequester() }
            var lastFocusedSectionTitle by rememberSaveable { mutableStateOf<String?>(null) }
            var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedEpisodeMenuItem by remember { mutableStateOf<HomeCardModel?>(null) }
            var shouldRestoreFocus by rememberSaveable { mutableStateOf(true) }
            val focusNavigationCoordinator = remember(coroutineScope) {
                FocusNavigationCoordinator(coroutineScope)
            }

            val preferredFocusRequester = when {
                lastFocusedSectionTitle != null -> {
                    state.sections.indexOfFirst { it.title == lastFocusedSectionTitle }
                        .takeIf { it >= 0 }
                        ?.let(sectionFocusRequesters::get)
                }
                state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                else -> sectionFocusRequesters.firstOrNull()
            }
            val destinationFocus = rememberTopLevelDestinationFocus(preferredFocusRequester)

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

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && hasBeenPaused) {
                        shouldRestoreFocus = true
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    focusNavigationCoordinator.cancelActiveJob()
                }
            }

            LaunchedEffect(shouldRestoreFocus, preferredFocusRequester) {
                if (!shouldRestoreFocus) {
                    return@LaunchedEffect
                }

                val fallbackRequester = when {
                    state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                    else -> sectionFocusRequesters.firstOrNull()
                }
                val requester = preferredFocusRequester ?: fallbackRequester
                    ?: return@LaunchedEffect

                val targetListIndex = when {
                    lastFocusedSectionTitle != null -> {
                        state.sections.indexOfFirst { it.title == lastFocusedSectionTitle }
                            .takeIf { it >= 0 }
                            ?.let { if (state.featuredItems.isNotEmpty()) it + 1 else it }
                    }
                    state.featuredItems.isNotEmpty() -> 0
                    else -> 0
                }

                if (targetListIndex != null) {
                    listState.scrollToItemAndAwaitLayout(targetListIndex)
                }
                runCatching { requester.requestFocus() }
                delay(HOME_FOCUS_RESTORE_SETTLE_MS)
                shouldRestoreFocus = false
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
                            val firstSectionRequester = sectionFocusRequesters.firstOrNull()
                            FeaturedCarousel(
                                items = state.featuredItems,
                                onMoreInfo = onOpenItem,
                                onPlay = onPlayItem,
                                destinationFocus = destinationFocus,
                                primaryActionFocusRequester = featuredPrimaryActionRequester,
                                downRequester = firstSectionRequester,
                                onNavigateDown = firstSectionRequester?.let {
                                    {
                                        focusNavigationCoordinator.submit {
                                            listState.scrollToItemAndAwaitLayout(1)
                                            it.requestFocus()
                                        }
                                    }
                                },
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
                        val upFocusRequester = when {
                            index == 0 && state.featuredItems.isNotEmpty() -> featuredPrimaryActionRequester
                            index == 0 -> destinationFocus.drawerFocusRequester
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
                                if (!listState.isIndexVisible(listIndex)) {
                                    focusNavigationCoordinator.submit {
                                        listState.animateScrollToItem(listIndex)
                                    }
                                }
                            },
                            onEpisodeMenuRequested = { selectedEpisodeMenuItem = it },
                            firstItemFocusRequester = sectionFocusRequesters[index],
                            upFocusRequester = upFocusRequester,
                            onNavigateUp = when {
                                index == 0 && state.featuredItems.isNotEmpty() -> {
                                    {
                                        focusNavigationCoordinator.submit {
                                            listState.scrollToItemAndAwaitLayout(0)
                                            featuredPrimaryActionRequester.requestFocus()
                                        }
                                    }
                                }
                                index > 0 -> {
                                    {
                                        focusNavigationCoordinator.submit {
                                            val previousListIndex = if (state.featuredItems.isNotEmpty()) index else index - 1
                                            listState.scrollToItemAndAwaitLayout(previousListIndex)
                                            sectionFocusRequesters[index - 1].requestFocus()
                                        }
                                    }
                                }
                                else -> null
                            },
                            onNavigateDown = sectionFocusRequesters.getOrNull(index + 1)?.let { nextRequester ->
                                {
                                    focusNavigationCoordinator.submit {
                                        val nextListIndex = if (state.featuredItems.isNotEmpty()) index + 2 else index + 1
                                        listState.scrollToItemAndAwaitLayout(nextListIndex)
                                        nextRequester.requestFocus()
                                    }
                                }
                            },
                            destinationFocus = destinationFocus,
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
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    primaryActionFocusRequester: FocusRequester,
    downRequester: FocusRequester?,
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
            .height(344.dp)
            .focusGroup()
            .onPreviewKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                if (
                    onNavigateDown != null &&
                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onNavigateDown()
                    true
                } else {
                    false
                }
            },
    ) { index ->
        val item = items[index]
        HeroItem(
            item = item,
            onMoreInfo = { onMoreInfo(item) },
            onPlay = { onPlay(item.id) },
            destinationFocus = destinationFocus,
            primaryActionFocusRequester = primaryActionFocusRequester,
            downRequester = downRequester,
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
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    primaryActionFocusRequester: FocusRequester,
    downRequester: FocusRequester?,
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

    Box(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(heroImageUrl)
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
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 42.dp, end = 24.dp)
                .widthIn(max = 620.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
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
                text = item.description ?: "Featured title from your library",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.84f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.elementGap)
            ) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .blockBringIntoView()
                        .focusRequester(primaryActionFocusRequester)
                        .then(destinationFocus.primaryContentModifier(
                            down = downRequester,
                            right = detailsButtonRequester,
                        ))
                        .testTag(HomeTestTags.FeaturedPlayButton),
                    scale = ButtonDefaults.scale(focusedScale = 1.06f),
                ) {
                    Text("Play", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onMoreInfo,
                    modifier = Modifier
                        .blockBringIntoView()
                        .focusRequester(detailsButtonRequester)
                        .then(destinationFocus.drawerEscapeModifier(
                            down = downRequester,
                            left = primaryActionFocusRequester,
                        ))
                        .testTag(HomeTestTags.FeaturedDetailsButton),
                    scale = ButtonDefaults.scale(focusedScale = 1.06f),
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
    onOpenItem: (HomeCardModel) -> Unit,
    restoredFocusedItemId: String?,
    onItemFocused: (String) -> Unit,
    onEpisodeMenuRequested: (HomeCardModel) -> Unit,
    firstItemFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    onNavigateUp: (() -> Unit)?,
    onNavigateDown: (() -> Unit)?,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val visibleItems = items.take(12)
    val restoredFocusIndex = visibleItems.indexOfFirst { it.id == restoredFocusedItemId }
        .takeIf { it >= 0 } ?: 0

    Column(
        modifier = modifier.testTag(HomeTestTags.section(sectionIndex)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.01).em,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = spacing.gutter, end = spacing.gutter),
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cardWidth = ((maxWidth - (spacing.gutter * 2) - (spacing.cardGap * 2)) / 3f)
                .coerceIn(220.dp, 400.dp)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy((spacing.cardGap - 4.dp).coerceAtLeast(12.dp)),
                contentPadding = PaddingValues(horizontal = spacing.gutter),
            ) {
                itemsIndexed(visibleItems, key = { _, item -> item.id }) { index, item ->
                    val focusModifier = if (upFocusRequester != null) {
                        destinationFocus.drawerEscapeModifier(
                            isLeftEdge = index == 0,
                            up = upFocusRequester,
                        )
                    } else {
                        destinationFocus.drawerEscapeModifier(isLeftEdge = index == 0)
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
                        modifier = Modifier
                            .testTag(HomeTestTags.sectionItem(sectionIndex, index))
                            .then(
                                if (index == 0 || index == restoredFocusIndex) {
                                    Modifier.blockBringIntoView()
                                } else {
                                    Modifier
                                }
                            )
                            .then(if (index == restoredFocusIndex) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .then(focusModifier)
                            .onPreviewKeyEvent { keyEvent ->
                                val nativeEvent = keyEvent.nativeKeyEvent
                                when {
                                    onNavigateDown != null &&
                                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        onNavigateDown()
                                        true
                                    }
                                    onNavigateUp != null &&
                                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        onNavigateUp()
                                        true
                                    }
                                    else -> false
                                }
                            }
                    )
                }
            }
        }
    }
}


private fun LazyListState.isIndexVisible(index: Int): Boolean =
    layoutInfo.visibleItemsInfo.any { it.index == index }

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
