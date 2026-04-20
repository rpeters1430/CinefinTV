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
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
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
    similarItems: List<SimilarMovieModel>,
    onCastClick: (String) -> Unit,
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
    val firstSimilarFocusRequester = remember { FocusRequester() }
    val hasNextUp = !nextUpTitle.isNullOrBlank() && onNextUpClick != null

    var indexCursor = 1
    val seasonIndex = if (seasons.isNotEmpty()) indexCursor++ else -1
    val nextUpIndex = if (hasNextUp) indexCursor++ else -1
    val castIndex = if (castItems.isNotEmpty()) indexCursor++ else -1
    val similarIndex = if (similarItems.isNotEmpty()) indexCursor else -1

    val firstContentFocusRequester = when {
        seasons.isNotEmpty() -> seasonFocusRequester
        hasNextUp -> nextUpFocusRequester
        castItems.isNotEmpty() -> firstCastFocusRequester
        similarItems.isNotEmpty() -> firstSimilarFocusRequester
        else -> null
    }
    val firstContentIndex = listOf(seasonIndex, nextUpIndex, castIndex, similarIndex).firstOrNull { it >= 0 } ?: -1

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
                onDownNavigation = if (firstContentFocusRequester != null && firstContentIndex >= 0) {
                    {
                        if (listState.isScrollInProgress) return@FlatDetailHero
                        coroutineScope.launch {
                            listState.scrollToItem(firstContentIndex)
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
                        items(seasons) { season ->
                            SeasonShowcaseCard(
                                season = season,
                                modifier = if (season.id == seasons.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(seasonFocusRequester)
                                        .focusProperties {
                                            up = primaryActionFocusRequester
                                            down = when {
                                                hasNextUp -> nextUpFocusRequester
                                                castItems.isNotEmpty() -> firstCastFocusRequester
                                                similarItems.isNotEmpty() -> firstSimilarFocusRequester
                                                else -> seasonFocusRequester
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val nativeEvent = keyEvent.nativeKeyEvent
                                            if (
                                                nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                                nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                coroutineScope.launch {
                                                    listState.scrollToItem(0)
                                                    runCatching { primaryActionFocusRequester.requestFocus() }
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .testTag(DetailTestTags.FirstSeasonItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onSeasonClick(season) },
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
                        similarItems.isNotEmpty() -> firstSimilarFocusRequester
                        else -> nextUpFocusRequester
                    },
                    modifier = Modifier
                        .padding(top = spacing.rowGap.div(1.5f))
                        .padding(horizontal = spacing.gutter)
                        .testTag(DetailTestTags.TvNextUpPanel),
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
                                            up = when {
                                                hasNextUp -> nextUpFocusRequester
                                                seasons.isNotEmpty() -> seasonFocusRequester
                                                else -> primaryActionFocusRequester
                                            }
                                            down = if (similarItems.isNotEmpty()) firstSimilarFocusRequester else firstCastFocusRequester
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
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                watchStatus = item.watchStatus,
                                playbackProgress = item.playbackProgress,
                                aspectRatio = 16f / 9f,
                                cardWidth = 200.dp,
                                modifier = if (item.id == similarItems.firstOrNull()?.id) {
                                    Modifier
                                        .blockBringIntoView()
                                        .focusRequester(firstSimilarFocusRequester)
                                        .focusProperties {
                                            up = if (castItems.isNotEmpty()) firstCastFocusRequester else if (hasNextUp) nextUpFocusRequester else if (seasons.isNotEmpty()) seasonFocusRequester else primaryActionFocusRequester
                                        }
                                        .testTag(DetailTestTags.FirstSimilarItem)
                                } else {
                                    Modifier
                                },
                                onClick = { onSimilarClick(item.id) },
                            )
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
                    .focusProperties {
                        up = upFocusRequester
                        down = downFocusRequester
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
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { state -> isFocused = state.isFocused || state.hasFocus },
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
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = season.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (season.unwatchedCount > 0) "${season.unwatchedCount} unwatched" else "Open season",
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
