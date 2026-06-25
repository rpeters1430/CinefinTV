@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailShelfPanel
import com.rpeters.cinefintv.ui.screens.detail.SeasonModel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.screens.detail.TrailerModel
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.screens.detail.scrollToItemAndAwaitLayout
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.components.ImmersiveBackground
import androidx.compose.ui.graphics.Color
import com.rpeters.cinefintv.ui.navigation.safeRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvShowDetailLayout(
    backdropUrl: String?,
    posterUrl: String?,
    title: String,
    metadataItems: List<String>,
    qualityBadges: List<String>,
    genres: List<String>,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    nextUpTitle: String?,
    onNextUpClick: (() -> Unit)?,
    seasons: List<SeasonModel>,
    onSeasonClick: (SeasonModel) -> Unit,
    castItems: List<CastModel>,
    trailers: List<TrailerModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onTrailerClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    description: String,
    heroSecondaryActions: List<HeroSecondaryAction>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
    drawerFocusRequester: FocusRequester? = null,
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val seasonFocusRequester = remember { FocusRequester() }
    val nextUpFocusRequester = remember { FocusRequester() }
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstTrailerFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val hasNextUp = !nextUpTitle.isNullOrBlank() && onNextUpClick != null

    var focusedBackdropUrl by remember(backdropUrl) { mutableStateOf(backdropUrl) }

    val seasonIndex = if (seasons.isNotEmpty()) 1 else -1
    val nextUpIndex = if (hasNextUp) 1 + (if (seasons.isNotEmpty()) 1 else 0) else -1
    val castIndex = if (castItems.isNotEmpty()) 1 + (if (seasons.isNotEmpty()) 1 else 0) + (if (hasNextUp) 1 else 0) else -1
    val trailerIndex = if (trailers.isNotEmpty()) 1 + (if (seasons.isNotEmpty()) 1 else 0) + (if (hasNextUp) 1 else 0) + (if (castItems.isNotEmpty()) 1 else 0) else -1
    val similarIndex = if (similarItems.isNotEmpty()) 1 + (if (seasons.isNotEmpty()) 1 else 0) + (if (hasNextUp) 1 else 0) + (if (castItems.isNotEmpty()) 1 else 0) + (if (trailers.isNotEmpty()) 1 else 0) else -1

    val firstContentFocusRequester = when {
        seasons.isNotEmpty() -> seasonFocusRequester
        hasNextUp -> nextUpFocusRequester
        castItems.isNotEmpty() -> firstCastFocusRequester
        trailers.isNotEmpty() -> firstTrailerFocusRequester
        similarItems.isNotEmpty() -> firstSimilarFocusRequester
        else -> null
    }
    val firstContentIndex = listOf(seasonIndex, nextUpIndex, castIndex, trailerIndex, similarIndex).firstOrNull { it >= 0 } ?: -1

    fun navigateToDetailRow(index: Int, requester: FocusRequester) {
        if (index < 0) return
        coroutineScope.launch {
            listState.scrollToItemAndAwaitLayout(index)
            runCatching { requester.requestFocus() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        ImmersiveBackground(backdropUrl = focusedBackdropUrl)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.gutter),
        ) {
            item {
                DetailAnchor(
                    focusRequester = topFocusRequester,
                    downFocusRequester = primaryActionFocusRequester,
                    onFocused = { focusedBackdropUrl = backdropUrl },
                )
                FlatDetailHero(
                    backdropUrl = backdropUrl,
                    posterUrl = posterUrl,
                    title = title,
                    metadataItems = metadataItems,
                    qualityBadges = qualityBadges,
                    genres = genres,
                    summary = description,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryAction = onPrimaryAction,
                    secondaryActions = heroSecondaryActions,
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    primaryActionDownFocusRequester = firstContentFocusRequester,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocus = { focusedBackdropUrl = backdropUrl },
                    onDownNavigation = if (firstContentFocusRequester != null && firstContentIndex >= 0) {
                        {
                            if (listState.isScrollInProgress) return@FlatDetailHero
                            coroutineScope.launch {
                                listState.scrollToItemAndAwaitLayout(firstContentIndex)
                                runCatching { firstContentFocusRequester.requestFocus() }
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            if (seasons.isNotEmpty()) {
                item {
                    DetailShelfPanel(
                        modifier = Modifier
                            .padding(top = spacing.rowGap.div(1.5f))
                            .testTag(DetailTestTags.TvEpisodesPanel),
                        title = "Seasons",
                        subtitle = "",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = spacing.gutter),
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                        ) {
                            items(seasons, key = { it.id }) { season ->
                                val isFirstSeasonItem = season.id == seasons.firstOrNull()?.id
                                SeasonShowcaseCard(
                                    season = season,
                                    modifier = Modifier
                                        .blockBringIntoView()
                                        .then(if (isFirstSeasonItem) Modifier.focusRequester(seasonFocusRequester) else Modifier)
                                        .focusProperties {
                                            up = primaryActionFocusRequester
                                            if (isFirstSeasonItem) {
                                                down = when {
                                                    hasNextUp -> nextUpFocusRequester
                                                    castItems.isNotEmpty() -> firstCastFocusRequester
                                                    trailers.isNotEmpty() -> firstTrailerFocusRequester
                                                    similarItems.isNotEmpty() -> firstSimilarFocusRequester
                                                    else -> seasonFocusRequester
                                                }
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                coroutineScope.launch {
                                                    listState.scrollToItemAndAwaitLayout(0)
                                                    primaryActionFocusRequester.safeRequestFocus(retries = 2)
                                                }
                                                true
                                            } else if (
                                                isFirstSeasonItem &&
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                            ) {
                                                when {
                                                    hasNextUp -> navigateToDetailRow(nextUpIndex, nextUpFocusRequester)
                                                    castItems.isNotEmpty() -> navigateToDetailRow(castIndex, firstCastFocusRequester)
                                                    trailers.isNotEmpty() -> navigateToDetailRow(trailerIndex, firstTrailerFocusRequester)
                                                    similarItems.isNotEmpty() -> navigateToDetailRow(similarIndex, firstSimilarFocusRequester)
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .then(if (isFirstSeasonItem) Modifier.testTag(DetailTestTags.FirstSeasonItem) else Modifier),
                                    onClick = { onSeasonClick(season) },
                                    onFocus = { focusedBackdropUrl = season.imageUrl },
                                )
                            }
                        }
                    }
                }
            }

            if (hasNextUp) {
                item {
                    NextUpPanel(
                        title = nextUpTitle.orEmpty(),
                        onPlay = onNextUpClick,
                        focusRequester = nextUpFocusRequester,
                        upFocusRequester = if (seasons.isNotEmpty()) seasonFocusRequester else primaryActionFocusRequester,
                        downFocusRequester = when {
                            castItems.isNotEmpty() -> firstCastFocusRequester
                            trailers.isNotEmpty() -> firstTrailerFocusRequester
                            similarItems.isNotEmpty() -> firstSimilarFocusRequester
                            else -> nextUpFocusRequester
                        },
                        onNavigateUp = {
                            val targetIndex = if (seasons.isNotEmpty()) seasonIndex else 0
                            val targetRequester = if (seasons.isNotEmpty()) seasonFocusRequester else primaryActionFocusRequester
                            navigateToDetailRow(targetIndex, targetRequester)
                        },
                        onNavigateDown = {
                            when {
                                castItems.isNotEmpty() -> navigateToDetailRow(castIndex, firstCastFocusRequester)
                                trailers.isNotEmpty() -> navigateToDetailRow(trailerIndex, firstTrailerFocusRequester)
                                similarItems.isNotEmpty() -> navigateToDetailRow(similarIndex, firstSimilarFocusRequester)
                            }
                        },
                        modifier = Modifier
                            .padding(top = spacing.rowGap.div(1.5f))
                            .padding(horizontal = spacing.gutter)
                            .testTag(DetailTestTags.TvNextUpPanel),
                        onFocus = { focusedBackdropUrl = backdropUrl },
                    )
                }
            }

            if (castItems.isNotEmpty()) {
                item {
                    DetailShelfPanel(
                        modifier = Modifier
                            .padding(top = spacing.rowGap.div(1.5f))
                            .testTag(DetailTestTags.TvCastPanel),
                        title = "Cast",
                        subtitle = "",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(castItems, key = { it.id }) { person ->
                                val isFirstCastItem = person.id == castItems.firstOrNull()?.id
                                PersonCircleCard(
                                    name = person.name,
                                    role = person.role,
                                    imageUrl = person.imageUrl,
                                    modifier = Modifier
                                        .blockBringIntoView()
                                        .then(if (isFirstCastItem) Modifier.focusRequester(firstCastFocusRequester) else Modifier)
                                        .focusProperties {
                                            up = when {
                                                hasNextUp -> nextUpFocusRequester
                                                seasons.isNotEmpty() -> seasonFocusRequester
                                                else -> primaryActionFocusRequester
                                            }
                                            if (isFirstCastItem) {
                                                down = when {
                                                    trailers.isNotEmpty() -> firstTrailerFocusRequester
                                                    similarItems.isNotEmpty() -> firstSimilarFocusRequester
                                                    else -> firstCastFocusRequester
                                                }
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                coroutineScope.launch {
                                                    when {
                                                        hasNextUp -> {
                                                            listState.scrollToItemAndAwaitLayout(nextUpIndex)
                                                            runCatching { nextUpFocusRequester.requestFocus() }
                                                        }
                                                        seasons.isNotEmpty() -> {
                                                            listState.scrollToItemAndAwaitLayout(seasonIndex)
                                                            runCatching { seasonFocusRequester.requestFocus() }
                                                        }
                                                        else -> {
                                                            listState.scrollToItemAndAwaitLayout(0)
                                                            runCatching { primaryActionFocusRequester.requestFocus() }
                                                        }
                                                    }
                                                }
                                                true
                                            } else if (
                                                isFirstCastItem &&
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                            ) {
                                                when {
                                                    trailers.isNotEmpty() -> navigateToDetailRow(trailerIndex, firstTrailerFocusRequester)
                                                    similarItems.isNotEmpty() -> navigateToDetailRow(similarIndex, firstSimilarFocusRequester)
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .then(if (isFirstCastItem) Modifier.testTag(DetailTestTags.FirstCastItem) else Modifier),
                                    onClick = { onCastClick(person.id) },
                                    onFocus = { focusedBackdropUrl = backdropUrl },
                                )
                            }
                        }
                    }
                }
            }

            if (trailers.isNotEmpty()) {
                item {
                    DetailShelfPanel(
                        modifier = Modifier.padding(top = spacing.rowGap.div(1.5f)),
                        title = "Trailers & Extras",
                        subtitle = "",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                        ) {
                            items(trailers, key = { it.id }) { trailer ->
                                val isFirst = trailer.id == trailers.firstOrNull()?.id
                                TvMediaCard(
                                    title = trailer.title,
                                    imageUrl = trailer.thumbnailUrl,
                                    aspectRatio = 16f / 9f,
                                    cardWidth = 200.dp,
                                    modifier = Modifier
                                        .blockBringIntoView()
                                        .then(if (isFirst) Modifier.focusRequester(firstTrailerFocusRequester) else Modifier)
                                        .focusProperties {
                                            up = when {
                                                castItems.isNotEmpty() -> firstCastFocusRequester
                                                hasNextUp -> nextUpFocusRequester
                                                seasons.isNotEmpty() -> seasonFocusRequester
                                                else -> primaryActionFocusRequester
                                            }
                                            if (isFirst) {
                                                down = if (similarItems.isNotEmpty()) firstSimilarFocusRequester else firstTrailerFocusRequester
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            when {
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    coroutineScope.launch {
                                                        when {
                                                            castItems.isNotEmpty() -> {
                                                                listState.scrollToItemAndAwaitLayout(castIndex)
                                                                runCatching { firstCastFocusRequester.requestFocus() }
                                                            }
                                                            hasNextUp -> {
                                                                listState.scrollToItemAndAwaitLayout(nextUpIndex)
                                                                runCatching { nextUpFocusRequester.requestFocus() }
                                                            }
                                                            seasons.isNotEmpty() -> {
                                                                listState.scrollToItemAndAwaitLayout(seasonIndex)
                                                                runCatching { seasonFocusRequester.requestFocus() }
                                                            }
                                                            else -> {
                                                                listState.scrollToItemAndAwaitLayout(0)
                                                                runCatching { primaryActionFocusRequester.requestFocus() }
                                                            }
                                                        }
                                                    }
                                                    true
                                                }
                                                isFirst &&
                                                    similarItems.isNotEmpty() &&
                                                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        listState.scrollToItemAndAwaitLayout(similarIndex)
                                                        runCatching { firstSimilarFocusRequester.requestFocus() }
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                    onClick = { onTrailerClick(trailer.id) },
                                    onFocus = { focusedBackdropUrl = trailer.thumbnailUrl },
                                )
                            }
                        }
                    }
                }
            }

            if (similarItems.isNotEmpty()) {
                item {
                    DetailShelfPanel(
                        modifier = Modifier
                            .padding(top = spacing.rowGap.div(1.5f))
                            .testTag(DetailTestTags.TvSimilarPanel),
                        title = "More Like This",
                        subtitle = "",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                        ) {
                            items(similarItems, key = { it.id }) { item ->
                                val isFirstSimilarItem = item.id == similarItems.firstOrNull()?.id
                                TvMediaCard(
                                    title = item.title,
                                    imageUrl = item.imageUrl,
                                    watchStatus = item.watchStatus,
                                    playbackProgress = item.playbackProgress,
                                    aspectRatio = 16f / 9f,
                                    cardWidth = 200.dp,
                                    modifier = Modifier
                                        .blockBringIntoView()
                                        .then(if (isFirstSimilarItem) Modifier.focusRequester(firstSimilarFocusRequester) else Modifier)
                                        .focusProperties {
                                            up = when {
                                                trailers.isNotEmpty() -> firstTrailerFocusRequester
                                                castItems.isNotEmpty() -> firstCastFocusRequester
                                                hasNextUp -> nextUpFocusRequester
                                                seasons.isNotEmpty() -> seasonFocusRequester
                                                else -> primaryActionFocusRequester
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                coroutineScope.launch {
                                                    when {
                                                        trailers.isNotEmpty() -> {
                                                            listState.scrollToItemAndAwaitLayout(trailerIndex)
                                                            runCatching { firstTrailerFocusRequester.requestFocus() }
                                                        }
                                                        castItems.isNotEmpty() -> {
                                                            listState.scrollToItemAndAwaitLayout(castIndex)
                                                            runCatching { firstCastFocusRequester.requestFocus() }
                                                        }
                                                        hasNextUp -> {
                                                            listState.scrollToItemAndAwaitLayout(nextUpIndex)
                                                            runCatching { nextUpFocusRequester.requestFocus() }
                                                        }
                                                        seasons.isNotEmpty() -> {
                                                            listState.scrollToItemAndAwaitLayout(seasonIndex)
                                                            runCatching { seasonFocusRequester.requestFocus() }
                                                        }
                                                        else -> {
                                                            listState.scrollToItemAndAwaitLayout(0)
                                                            runCatching { primaryActionFocusRequester.requestFocus() }
                                                        }
                                                    }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    onClick = { onSimilarClick(item.id) },
                                    onFocus = { focusedBackdropUrl = item.imageUrl },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpPanel(
    title: String,
    onPlay: () -> Unit,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onNavigateUp: () -> Unit,
    onNavigateDown: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = expressiveColors.detailPanel.copy(alpha = 0.82f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            )
            .border(
                width = 1.dp,
                color = expressiveColors.borderSubtle.copy(alpha = 0.55f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "NEXT UP",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .focusProperties {
                        up = upFocusRequester
                        down = downFocusRequester
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val nativeEvent = keyEvent.nativeKeyEvent
                        if (
                            nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                        ) {
                            onNavigateUp()
                            true
                        } else if (
                            nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            onNavigateDown()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag(DetailTestTags.TvNextUpAction),
                scale = ButtonDefaults.scale(focusedScale = 1.03f),
            ) {
                androidx.tv.material3.Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Text(
                    text = "Play Next Episode",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SeasonShowcaseCard(
    season: SeasonModel,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val expressiveColors = LocalCinefinExpressiveColors.current

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .onFocusChanged { state -> 
                isFocused = state.isFocused || state.hasFocus
                if (state.isFocused) onFocus()
            },
        colors = androidx.tv.material3.CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        ),
        shape = androidx.tv.material3.CardDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.03f),
        border = androidx.tv.material3.CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                ),
            ),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                contentAlignment = Alignment.Center,
            ) {
                if (season.imageUrl != null) {
                    AsyncImage(
                        model = season.imageUrl,
                        contentDescription = season.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(expressiveColors.accentSurface.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = season.title.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = season.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (season.unwatchedCount > 0) "${season.unwatchedCount} unwatched" else "Open season",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
