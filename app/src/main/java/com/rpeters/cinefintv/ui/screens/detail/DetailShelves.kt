package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.CinefinShelfTitle
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailShelves(
    state: DetailUiState.Content,
    onOpenItem: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onFocusedDescriptionChange: (String?) -> Unit,
    onFocusedPreviewImageChange: (String?) -> Unit,
    playButtonRequester: FocusRequester,
    primaryShelfRequester: FocusRequester,
    castShelfRequester: FocusRequester,
    relatedShelfRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val spacing = LocalCinefinSpacing.current
    val episodes = state.episodesBySeasonId.values.flatten()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap)
    ) {
        if (state.seasons.isNotEmpty()) {
            ShelfRow(
                title = "Seasons",
                items = state.seasons,
                key = { it.id },
                onItemClick = { onOpenItem(it.id) },
                onItemFocus = { onFocusedDescriptionChange(it.overview) },
                itemContent = { season, itemModifier ->
                    TvMediaCard(
                        title = season.title,
                        subtitle = season.subtitle,
                        imageUrl = season.imageUrl,
                        onClick = { onOpenItem(season.id) },
                        onFocus = {
                            onFocusedDescriptionChange(season.overview)
                            onFocusedPreviewImageChange(season.imageUrl)
                        },
                        watchStatus = season.watchStatus,
                        playbackProgress = season.playbackProgress,
                        unwatchedCount = season.unwatchedCount,
                        aspectRatio = 2f / 3f,
                        cardWidth = 168.dp,
                        modifier = itemModifier
                            .then(if (season == state.seasons.first()) Modifier.focusRequester(primaryShelfRequester) else Modifier)
                            .focusProperties {
                                up = playButtonRequester
                                if (state.cast.isNotEmpty()) {
                                    down = castShelfRequester
                                } else if (state.related.isNotEmpty()) {
                                    down = relatedShelfRequester
                                }
                            },
                    )
                }
            )
        }

        if (state.seasons.isEmpty() && episodes.isNotEmpty()) {
            ShelfRow(
                title = "Episodes",
                items = episodes,
                key = { it.id },
                onItemClick = { onOpenItem(it.id) },
                onItemFocus = { onFocusedDescriptionChange(it.overview) },
                itemContent = { episode, itemModifier ->
                    TvMediaCard(
                        title = episode.title,
                        subtitle = episode.subtitle,
                        imageUrl = episode.imageUrl,
                        onClick = { onOpenItem(episode.id) },
                        onFocus = {
                            onFocusedDescriptionChange(episode.overview)
                            onFocusedPreviewImageChange(episode.imageUrl)
                        },
                        watchStatus = episode.watchStatus,
                        playbackProgress = episode.playbackProgress,
                        unwatchedCount = episode.unwatchedCount,
                        aspectRatio = 16f / 9f,
                        cardWidth = 260.dp,
                        modifier = itemModifier
                            .then(if (episode == episodes.first()) Modifier.focusRequester(primaryShelfRequester) else Modifier)
                            .focusProperties {
                                up = playButtonRequester
                                if (state.cast.isNotEmpty()) {
                                    down = castShelfRequester
                                } else if (state.related.isNotEmpty()) {
                                    down = relatedShelfRequester
                                }
                            },
                    )
                }
            )
        }

        if (state.cast.isNotEmpty()) {
            ShelfRow(
                title = "Cast & Crew",
                items = state.cast,
                key = { it.id + it.role },
                onItemClick = { onOpenPerson(it.id) },
                onItemFocus = { onFocusedDescriptionChange(null) },
                itemContent = { person, itemModifier ->
                    TvPersonCard(
                        name = person.name,
                        role = person.role,
                        imageUrl = person.imageUrl,
                        onClick = { onOpenPerson(person.id) },
                        onFocus = {
                            onFocusedDescriptionChange(null)
                            onFocusedPreviewImageChange(person.imageUrl)
                        },
                        modifier = itemModifier
                            .then(if (person == state.cast.first()) Modifier.focusRequester(castShelfRequester) else Modifier)
                            .focusProperties {
                                up = if (state.seasons.isNotEmpty() || episodes.isNotEmpty()) {
                                    primaryShelfRequester
                                } else {
                                    playButtonRequester
                                }
                                if (state.related.isNotEmpty()) {
                                    down = relatedShelfRequester
                                }
                            },
                    )
                }
            )
        }

        if (state.related.isNotEmpty()) {
            ShelfRow(
                title = "More Like This",
                items = state.related,
                key = { it.id },
                onItemClick = { onOpenItem(it.id) },
                onItemFocus = { onFocusedDescriptionChange(it.overview) },
                itemContent = { related, itemModifier ->
                    TvMediaCard(
                        title = related.title,
                        subtitle = related.subtitle,
                        imageUrl = related.imageUrl,
                        onClick = { onOpenItem(related.id) },
                        onFocus = {
                            onFocusedDescriptionChange(related.overview)
                            onFocusedPreviewImageChange(related.backdropUrl ?: related.imageUrl)
                        },
                        watchStatus = related.watchStatus,
                        playbackProgress = related.playbackProgress,
                        unwatchedCount = related.unwatchedCount,
                        aspectRatio = 2f / 3f,
                        cardWidth = 168.dp,
                        modifier = itemModifier
                            .then(if (related == state.related.first()) Modifier.focusRequester(relatedShelfRequester) else Modifier)
                            .focusProperties {
                                up = when {
                                    state.cast.isNotEmpty() -> castShelfRequester
                                    state.seasons.isNotEmpty() || episodes.isNotEmpty() -> primaryShelfRequester
                                    else -> playButtonRequester
                                }
                            },
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> ShelfRow(
    title: String,
    items: List<T>,
    key: (T) -> String,
    onItemClick: (T) -> Unit,
    onItemFocus: (T) -> Unit,
    itemContent: @Composable (T, Modifier) -> Unit
) {
    val spacing = LocalCinefinSpacing.current

    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CinefinShelfTitle(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap)
        ) {
            items(items, key = key) { item ->
                itemContent(item, Modifier)
            }
        }
    }
}
