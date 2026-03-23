@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvPersonCard
import com.rpeters.cinefintv.ui.components.TvMediaCard

@Composable
fun TvShowDetailScreen(
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvShowDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

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

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is TvShowDetailUiState.Loading -> DetailLoadingState()
            is TvShowDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is TvShowDetailUiState.Content -> TvShowDetailContent(
                show = state.show,
                seasons = state.seasons,
                cast = state.cast,
                similarShows = state.similarShows,
                onPlayEpisode = onPlayEpisode,
                onOpenSeason = onOpenSeason,
                onOpenShow = onOpenShow,
            )
        }
    }
}

@Composable
private fun TvShowDetailContent(
    show: TvShowDetailModel,
    seasons: List<SeasonModel>,
    cast: List<CastModel>,
    similarShows: List<SimilarMovieModel>,
    onPlayEpisode: (String) -> Unit,
    onOpenSeason: (String) -> Unit,
    onOpenShow: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val anchorFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val primaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val seasonsEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val castEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val similarEntryFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    var lastFocusedSeasonId by rememberSaveable { mutableStateOf<String?>(seasons.firstOrNull()?.id) }
    var lastFocusedCastId by rememberSaveable { mutableStateOf<String?>(cast.firstOrNull()?.id) }
    var lastFocusedSimilarId by rememberSaveable { mutableStateOf<String?>(similarShows.firstOrNull()?.id) }

    LaunchedEffect(show.id) {
        // Request initial focus on the top anchor
        anchorFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            DetailAnchor(
                focusRequester = anchorFocusRequester,
                onFocused = {
                    scope.launch {
                        listState.scrollToItem(0)
                        primaryActionFocusRequester.requestFocus()
                    }
                }
            )
        }
        item {
            DetailHeroBox(
                backdropUrl = show.backdropUrl,
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 56.dp, vertical = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DetailPosterArt(
                        imageUrl = show.posterUrl,
                        title = show.title,
                        modifier = Modifier
                            .width(210.dp)
                            .height(315.dp),
                    )
                    DetailGlassPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        DetailTitleLogo(
                            logoUrl = show.logoUrl,
                            title = show.title,
                        )
                        Text(
                            text = show.title,
                            style = if (show.logoUrl != null) {
                                MaterialTheme.typography.headlineLarge
                            } else {
                                MaterialTheme.typography.displayMedium
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            color = if (show.logoUrl != null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        DetailMetaLine(
                            items = buildList {
                                show.rating?.let { add(DetailMetaItem(Icons.Default.Star, "IMDb $it")) }
                                show.secondaryRating?.let { add(DetailMetaItem(Icons.Default.Tv, "Critic $it")) }
                                show.airedDate?.let { add(DetailMetaItem(Icons.Default.CalendarToday, it)) }
                                    ?: show.yearRange?.let { add(DetailMetaItem(Icons.Default.CalendarToday, it)) }
                                show.officialRating?.let { add(DetailMetaItem(Icons.Default.Verified, it)) }
                                show.videoQuality?.let { add(DetailMetaItem(Icons.Default.HighQuality, it)) }
                                show.audioLabel?.let { add(DetailMetaItem(Icons.Default.SurroundSound, it)) }
                            }
                        )
                        DetailMetaLabelLine(
                            items = buildList {
                                if (show.genres.isNotEmpty()) {
                                    add(
                                        DetailLabeledMetaItem(
                                            Icons.Default.LocalOffer,
                                            "Tags",
                                            show.genres.take(4).joinToString(", "),
                                        )
                                    )
                                }
                                if (show.seasonCount > 0) {
                                    add(
                                        DetailLabeledMetaItem(
                                            Icons.Default.VideoLibrary,
                                            "Seasons",
                                            show.seasonCount.toString(),
                                        )
                                    )
                                }
                                show.endedDate?.let {
                                    add(DetailLabeledMetaItem(Icons.Default.Event, "Ends", it))
                                }
                                show.status?.let {
                                    add(DetailLabeledMetaItem(Icons.Default.Tv, "Status", it))
                                }
                                if (show.creators.isNotEmpty()) {
                                    add(
                                        DetailLabeledMetaItem(
                                            Icons.Default.Edit,
                                            "Created by",
                                            show.creators.take(2).joinToString(", "),
                                        )
                                    )
                                }
                                if (show.networks.isNotEmpty()) {
                                    add(
                                        DetailLabeledMetaItem(
                                            Icons.Default.CastConnected,
                                            "Network",
                                            show.networks.take(2).joinToString(", "),
                                        )
                                    )
                                }
                            }
                        )
                        show.overview?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                            )
                        }
                        DetailActionRow(
                            primaryLabel = if (show.nextUpEpisodeId != null) {
                                "Play ${show.nextUpTitle ?: "Next Up"}"
                            } else {
                                "Browse Seasons"
                            },
                            onPrimaryClick = {
                                if (show.nextUpEpisodeId != null) {
                                    onPlayEpisode(show.nextUpEpisodeId)
                                } else {
                                    seasons.firstOrNull()?.let { onOpenSeason(it.id) }
                                }
                            },
                            secondaryLabel = if (seasons.isNotEmpty()) "Season Guide" else null,
                            onSecondaryClick = if (seasons.isNotEmpty()) {
                                { onOpenSeason(seasons.first().id) }
                            } else {
                                null
                            },
                            primaryFocusRequester = primaryActionFocusRequester,
                            primaryDownFocusRequester = if (seasons.isNotEmpty() || cast.isNotEmpty() || similarShows.isNotEmpty()) {
                                when {
                                    seasons.isNotEmpty() -> seasonsEntryFocusRequester
                                    cast.isNotEmpty() -> castEntryFocusRequester
                                    else -> similarEntryFocusRequester
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (seasons.isNotEmpty()) {
            item {
                DetailContentSection(
                    title = "Seasons",
                    eyebrow = "${seasons.size} available",
                    icon = Icons.Default.VideoLibrary,
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(seasons) { season ->
                            TvMediaCard(
                                title = season.title,
                                subtitle = season.episodeCount?.let { "$it Episodes" },
                                imageUrl = season.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 152.dp,
                                unwatchedCount = season.unwatchedCount,
                                modifier = Modifier
                                    .then(
                                        if (season.id == lastFocusedSeasonId) Modifier.focusRequester(seasonsEntryFocusRequester) else Modifier
                                    )
                                    .then(
                                        if (season.id == seasons.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = primaryActionFocusRequester
                                                if (cast.isNotEmpty()) down = castEntryFocusRequester
                                                else if (similarShows.isNotEmpty()) down = similarEntryFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedSeasonId = season.id },
                                onClick = { onOpenSeason(season.id) },
                            )
                        }
                    }
                }
            }
        }

        if (cast.isNotEmpty()) {
            item {
                DetailContentSection(
                    title = "Cast & Crew",
                    eyebrow = "${cast.size} people",
                    icon = Icons.Default.Groups,
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(cast) { person ->
                            TvPersonCard(
                                name = person.name,
                                role = person.role,
                                imageUrl = person.imageUrl,
                                modifier = Modifier
                                    .then(
                                        if (person.id == lastFocusedCastId) {
                                            Modifier.focusRequester(castEntryFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (person.id == cast.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = if (seasons.isNotEmpty()) seasonsEntryFocusRequester else primaryActionFocusRequester
                                                if (similarShows.isNotEmpty()) down = similarEntryFocusRequester
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedCastId = person.id },
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }

        if (similarShows.isNotEmpty()) {
            item {
                DetailContentSection(
                    title = "More Like This",
                    eyebrow = "Recommended next",
                    icon = Icons.Default.Tv,
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(similarShows) { item ->
                            TvMediaCard(
                                title = item.title,
                                imageUrl = item.imageUrl,
                                aspectRatio = 2f / 3f,
                                cardWidth = 152.dp,
                                modifier = Modifier
                                    .then(
                                        if (item.id == lastFocusedSimilarId) {
                                            Modifier.focusRequester(similarEntryFocusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .then(
                                        if (item.id == similarShows.firstOrNull()?.id) {
                                            Modifier.focusProperties {
                                                up = when {
                                                    cast.isNotEmpty() -> castEntryFocusRequester
                                                    seasons.isNotEmpty() -> seasonsEntryFocusRequester
                                                    else -> primaryActionFocusRequester
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedSimilarId = item.id },
                                onClick = { onOpenShow(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
