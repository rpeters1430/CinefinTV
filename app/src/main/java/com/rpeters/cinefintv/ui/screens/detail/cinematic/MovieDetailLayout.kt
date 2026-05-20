@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.ImmersiveBackground
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailShelfPanel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.screens.detail.TrailerModel
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.screens.detail.scrollToItemAndAwaitLayout
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.launch

@Composable
fun MovieDetailLayout(
    backdropUrl: String?,
    posterUrl: String?,
    title: String,
    metadataItems: List<String>,
    qualityBadges: List<String>,
    genres: List<String>,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    primaryActionFocusRequester: FocusRequester,
    description: String,
    heroSecondaryActions: List<HeroSecondaryAction>,
    castItems: List<CastModel>,
    trailers: List<TrailerModel>,
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onTrailerClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
    drawerFocusRequester: FocusRequester? = null,
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstTrailerFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    
    var focusedBackdropUrl by remember(backdropUrl) { mutableStateOf(backdropUrl) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        ImmersiveBackground(backdropUrl = focusedBackdropUrl)
        
        val castSectionIndex = if (castItems.isNotEmpty()) 1 else -1
        val trailerSectionIndex = if (trailers.isNotEmpty()) 1 + (if (castItems.isNotEmpty()) 1 else 0) else -1
        val similarSectionIndex = if (similarItems.isNotEmpty()) 1 + (if (castItems.isNotEmpty()) 1 else 0) + (if (trailers.isNotEmpty()) 1 else 0) else -1

        val firstContentFocusRequester = when {
            castItems.isNotEmpty() -> firstCastFocusRequester
            trailers.isNotEmpty() -> firstTrailerFocusRequester
            similarItems.isNotEmpty() -> firstSimilarFocusRequester
            else -> null
        }
        val firstContentIndex = listOf(castSectionIndex, trailerSectionIndex, similarSectionIndex)
            .firstOrNull { it >= 0 } ?: -1
        
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

            if (castItems.isNotEmpty()) {
                item {
                    DetailShelfPanel(
                        modifier = Modifier
                            .padding(top = spacing.rowGap.div(1.5f))
                            .testTag(DetailTestTags.MovieCastSection),
                        title = "Cast",
                        subtitle = "",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(castItems) { person ->
                                val isFirstCastItem = person.id == castItems.firstOrNull()?.id
                                PersonCircleCard(
                                    name = person.name,
                                    role = person.role,
                                    imageUrl = person.imageUrl,
                                    modifier = Modifier
                                        .blockBringIntoView()
                                        .then(if (isFirstCastItem) Modifier.focusRequester(firstCastFocusRequester) else Modifier)
                                        .focusProperties {
                                            up = primaryActionFocusRequester
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
                                            when {
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    coroutineScope.launch {
                                                        listState.scrollToItemAndAwaitLayout(0)
                                                        runCatching { primaryActionFocusRequester.requestFocus() }
                                                    }
                                                    true
                                                }
                                                isFirstCastItem &&
                                                    (trailers.isNotEmpty() || similarItems.isNotEmpty()) &&
                                                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        if (trailers.isNotEmpty()) {
                                                            listState.scrollToItemAndAwaitLayout(trailerSectionIndex)
                                                            runCatching { firstTrailerFocusRequester.requestFocus() }
                                                        } else {
                                                            listState.scrollToItemAndAwaitLayout(similarSectionIndex)
                                                            runCatching { firstSimilarFocusRequester.requestFocus() }
                                                        }
                                                    }
                                                    true
                                                }
                                                else -> false
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
                                            up = if (castItems.isNotEmpty()) firstCastFocusRequester else primaryActionFocusRequester
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
                                                        if (castItems.isNotEmpty()) {
                                                            listState.scrollToItemAndAwaitLayout(castSectionIndex)
                                                            runCatching { firstCastFocusRequester.requestFocus() }
                                                        } else {
                                                            listState.scrollToItemAndAwaitLayout(0)
                                                            runCatching { primaryActionFocusRequester.requestFocus() }
                                                        }
                                                    }
                                                    true
                                                }
                                                isFirst &&
                                                    similarItems.isNotEmpty() &&
                                                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        listState.scrollToItemAndAwaitLayout(similarSectionIndex)
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
                        modifier = Modifier.testTag(DetailTestTags.MovieSimilarSection),
                        title = "More Like This",
                        subtitle = "",
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap.div(1.2f)),
                        ) {
                            items(similarItems, key = { it.id }) { mediaItem ->
                                val isFirstSimilarItem = mediaItem.id == similarItems.firstOrNull()?.id
                                TvMediaCard(
                                    title = mediaItem.title,
                                    imageUrl = mediaItem.imageUrl,
                                    watchStatus = mediaItem.watchStatus,
                                    playbackProgress = mediaItem.playbackProgress,
                                    aspectRatio = 16f / 9f,
                                    cardWidth = 200.dp,
                                    modifier = Modifier
                                        .blockBringIntoView()
                                        .then(if (isFirstSimilarItem) Modifier.focusRequester(firstSimilarFocusRequester) else Modifier)
                                        .focusProperties {
                                            up = when {
                                                trailers.isNotEmpty() -> firstTrailerFocusRequester
                                                castItems.isNotEmpty() -> firstCastFocusRequester
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
                                                            listState.scrollToItemAndAwaitLayout(trailerSectionIndex)
                                                            runCatching { firstTrailerFocusRequester.requestFocus() }
                                                        }
                                                        castItems.isNotEmpty() -> {
                                                            listState.scrollToItemAndAwaitLayout(castSectionIndex)
                                                            runCatching { firstCastFocusRequester.requestFocus() }
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
                                        }
                                        .then(if (isFirstSimilarItem) Modifier.testTag(DetailTestTags.FirstSimilarItem) else Modifier),
                                    onClick = { onSimilarClick(mediaItem.id) },
                                    onFocus = { focusedBackdropUrl = mediaItem.imageUrl },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
