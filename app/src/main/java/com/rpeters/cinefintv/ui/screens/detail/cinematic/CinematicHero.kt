@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.screens.detail.DetailActionRow
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.theme.ThemeSeedColorCache
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full-bleed cinematic hero section shared by MovieDetailLayout and TvShowDetailLayout.
 * Left-docked composition so the screen reads like a detail page, not a carousel slide.
 */
@Composable
fun CinematicHero(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    eyebrow: String,
    ratingText: String?,
    genres: List<String>,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
    primaryActionFocusRequester: FocusRequester = remember { FocusRequester() },
    primaryActionDownFocusRequester: FocusRequester? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    val themeController = LocalCinefinThemeController.current
    val context = LocalContext.current
    val performanceProfile = LocalPerformanceProfile.current
    val panelWidth = (screenWidth * 0.44f).coerceIn(420.dp, 760.dp)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val shouldExtractPalette = performanceProfile.tier == DevicePerformanceProfile.Tier.HIGH
    val cachedSeedColor = ThemeSeedColorCache.getCached(backdropUrl)
    val currentListState by rememberUpdatedState(listState)
    val manualDownNavigation = rememberUpdatedState<(() -> Unit)?>(newValue = if (listState != null && primaryActionDownFocusRequester != null) {
        {
            val activeListState = currentListState
            if (activeListState != null) {
                coroutineScope.launch {
                    activeListState.animateScrollToItem(1)
                    snapshotFlow {
                        val layoutInfo = activeListState.layoutInfo
                        layoutInfo.viewportEndOffset > layoutInfo.viewportStartOffset &&
                            layoutInfo.visibleItemsInfo.any { it.index == 1 }
                    }.first { it }
                    runCatching { primaryActionDownFocusRequester.requestFocus() }
                }
            }
        }
    } else null)

    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
    var logoFailed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { themeController.updateSeedColor(null) }
    }

    androidx.compose.runtime.LaunchedEffect(backdropUrl, cachedSeedColor) {
        cachedSeedColor?.let(themeController::updateSeedColor)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = screenHeight * 0.62f),
    ) {
        // Backdrop
        if (backdropUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .allowHardware(!shouldExtractPalette)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onSuccess = { state ->
                    if (!shouldExtractPalette) {
                        return@AsyncImage
                    }
                    if (ThemeSeedColorCache.getCached(backdropUrl) != null) {
                        return@AsyncImage
                    }

                    coroutineScope.launch {
                        ThemeSeedColorCache.getOrExtract(backdropUrl) {
                            state.result.image.toBitmap()
                        }?.let(themeController::updateSeedColor)
                    }
                },
            )
        }

        // Gradients for legibility
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.4f to expressiveColors.detailHeroScrimEnd.copy(alpha = 0.34f),
                        1.0f to expressiveColors.detailHeroScrimEnd,
                    )
                )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to expressiveColors.detailHeroScrimStart,
                        0.3f to expressiveColors.detailHeroScrimStart.copy(alpha = 0.72f),
                        0.6f to expressiveColors.detailHeroScrimEnd.copy(alpha = 0.22f),
                        1.0f to Color.Transparent,
                    )
                )
        )

        // Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = spacing.gutter, vertical = spacing.gutter),
            contentAlignment = Alignment.TopStart,
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(0.dp),
                    onSuccess = { logoLoaded = true },
                    onError = { logoFailed = true; logoLoaded = true },
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = 48.dp)
                    .requiredWidthIn(max = panelWidth)
                    .wrapContentHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                expressiveColors.detailHeroPanel,
                                expressiveColors.detailPanel,
                            )
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    .border(
                        width = 1.dp,
                        color = expressiveColors.borderSubtle.copy(alpha = 0.58f),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(42.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, expressiveColors.titleAccent)
                                ),
                                shape = RectangleShape,
                            )
                    )
                    Text(
                        text = eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (logoUrl != null && logoLoaded && !logoFailed) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .testTag(DetailTestTags.HeroLogo)
                            .heightIn(max = 120.dp)
                            .wrapContentWidth(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag(DetailTestTags.HeroTitle),
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
                    verticalArrangement = Arrangement.spacedBy(spacing.chipGap),
                ) {
                    ratingText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = expressiveColors.titleAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                                    shape = MaterialTheme.shapes.large,
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    genres.take(4).forEach { genre ->
                        CinefinChip(label = genre)
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.elementGap),
                    verticalArrangement = Arrangement.spacedBy(spacing.elementGap),
                ) {
                    DetailActionRow(
                        primaryLabel = primaryActionLabel,
                        onPrimaryClick = onPrimaryAction,
                        secondaryActions = secondaryActions,
                        primaryFocusRequester = primaryActionFocusRequester,
                        primaryDownFocusRequester = if (listState == null) primaryActionDownFocusRequester else null,
                        onDownNavigation = manualDownNavigation.value,
                        primaryButtonModifier = Modifier
                            .testTag(DetailTestTags.PrimaryAction)
                            .onFocusChanged {
                                val activeListState = currentListState
                                if (it.isFocused && activeListState != null) {
                                    coroutineScope.launch {
                                        val needsTopReset =
                                            activeListState.firstVisibleItemIndex != 0 ||
                                                activeListState.firstVisibleItemScrollOffset > 0
                                        if (needsTopReset) {
                                            activeListState.scrollToItem(0)
                                        }
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
