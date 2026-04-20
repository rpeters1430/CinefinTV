@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.CastModel
import com.rpeters.cinefintv.ui.screens.detail.DetailAnchor
import com.rpeters.cinefintv.ui.screens.detail.DetailShelfPanel
import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.flow.first
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
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
    onSimilarClick: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    topFocusRequester: FocusRequester = remember { FocusRequester() },
    drawerFocusRequester: FocusRequester? = null,
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val firstCastFocusRequester = remember { FocusRequester() }
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val firstContentFocusRequester = when {
        castItems.isNotEmpty() -> firstCastFocusRequester
        similarItems.isNotEmpty() -> firstSimilarFocusRequester
        else -> null
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.gutter),
    ) {
        item {
            DetailAnchor(
                focusRequester = topFocusRequester,
                downFocusRequester = primaryActionFocusRequester,
                onFocused = {},
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
                onDownNavigation = if (firstContentFocusRequester != null) {
                    {
                        if (listState.isScrollInProgress) return@FlatDetailHero
                        coroutineScope.launch {
                            listState.scrollToItemAndAwaitLayout(1)
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
                            PersonCircleCard(
                                name = person.name,
                                role = person.role,
                                imageUrl = person.imageUrl,
                                modifier = if (person.id == castItems.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstCastFocusRequester)
                                        .focusProperties {
                                            up = primaryActionFocusRequester
                                            down = if (similarItems.isNotEmpty()) firstSimilarFocusRequester else firstCastFocusRequester
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
                                                similarItems.isNotEmpty() &&
                                                    nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    coroutineScope.launch {
                                                        listState.scrollToItemAndAwaitLayout(2)
                                                        runCatching { firstSimilarFocusRequester.requestFocus() }
                                                    }
                                                    true
                                                }
                                                else -> false
                                            }
                                        }
                                        .testTag(DetailTestTags.FirstCastItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onCastClick(person.id) },
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
                            TvMediaCard(
                                title = mediaItem.title,
                                imageUrl = mediaItem.imageUrl,
                                watchStatus = mediaItem.watchStatus,
                                playbackProgress = mediaItem.playbackProgress,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                modifier = if (mediaItem.id == similarItems.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstSimilarFocusRequester)
                                        .focusProperties {
                                            up = if (castItems.isNotEmpty()) firstCastFocusRequester else primaryActionFocusRequester
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                coroutineScope.launch {
                                                    if (castItems.isNotEmpty()) {
                                                        listState.scrollToItemAndAwaitLayout(1)
                                                        runCatching { firstCastFocusRequester.requestFocus() }
                                                    } else {
                                                        listState.scrollToItemAndAwaitLayout(0)
                                                        runCatching { primaryActionFocusRequester.requestFocus() }
                                                    }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .testTag(DetailTestTags.FirstSimilarItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onSimilarClick(mediaItem.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollToItemAndAwaitLayout(index: Int) {
    if (!isIndexVisible(index)) {
        scrollToItem(index)
        snapshotFlow { isIndexVisible(index) }.first { it }
    }
}

private fun LazyListState.isIndexVisible(index: Int): Boolean =
    layoutInfo.visibleItemsInfo.any { it.index == index }
