@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection
import com.rpeters.cinefintv.utils.formatMs

@Composable
fun EpisodeDetailScreen(
    onPlayEpisode: (String, Long?) -> Unit,
    onBack: () -> Unit,
    viewModel: EpisodeDetailViewModel = hiltViewModel(),
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
            is EpisodeDetailUiState.Loading -> DetailLoadingState()
            is EpisodeDetailUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is EpisodeDetailUiState.Content -> EpisodeDetailContent(
                episode = state.episode,
                chapters = state.chapters,
                mediaDetail = state.mediaDetail,
                onPlayEpisode = onPlayEpisode,
            )
        }
    }
}

@Composable
private fun EpisodeDetailContent(
    episode: EpisodeDetailModel,
    chapters: List<ChapterModel>,
    mediaDetail: MediaDetailModel?,
    onPlayEpisode: (String, Long?) -> Unit,
) {
    val listState = rememberLazyListState()
    val primaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val factItems = remember(episode) {
        buildList {
            episode.episodeCode?.let {
                add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Episode", it))
            }
            episode.duration?.let {
                add(DetailLabeledMetaItem(Icons.Default.Timer, "Runtime", it))
            }
            episode.year?.let {
                add(DetailLabeledMetaItem(Icons.Default.CalendarToday, "Year", it.toString()))
            }
        }
    }

    LaunchedEffect(episode.id) {
        listState.scrollToItem(0)
        withFrameNanos {}   // wait one frame for layout to attach the node
        primaryActionFocusRequester.requestFocus()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item {
            CinematicHero(
                backdropUrl = episode.backdropUrl,
                logoUrl = null,
                title = episode.title,
                eyebrow = listOfNotNull(episode.seriesName, episode.episodeCode).joinToString(" · "),
                ratingText = null,
                genres = emptyList(),
                primaryActionLabel = when {
                    episode.playbackProgress != null -> "▶ Resume Episode"
                    episode.isWatched -> "▶ Play Again"
                    else -> "▶ Play Episode"
                },
                onPrimaryAction = { onPlayEpisode(episode.id, null) },
                secondaryActions = if (chapters.isNotEmpty())
                    listOf("Jump to Chapter" to { onPlayEpisode(episode.id, chapters.first().positionMs) })
                else emptyList(),
                primaryActionFocusRequester = primaryActionFocusRequester,
            )
        }

        item {
            DetailOverviewSection(
                title = episode.title,
                description = episode.overview.orEmpty(),
                factItems = factItems,
                chips = listOfNotNull(episode.seriesName, episode.episodeCode),
                modifier = Modifier.padding(top = 28.dp),
            )
        }

        if (chapters.isNotEmpty()) {
            item {
                DetailContentSection(
                    title = "Chapters",
                    eyebrow = "${chapters.size} markers",
                    icon = Icons.Default.Subtitles,
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(chapters, key = { it.id }) { chapter ->
                            TvMediaCard(
                                title = chapter.name,
                                subtitle = formatMs(chapter.positionMs),
                                imageUrl = chapter.imageUrl,
                                aspectRatio = 16f / 9f,
                                cardWidth = 240.dp,
                                modifier = Modifier
                                    .then(
                                        if (chapter.id == chapters.firstOrNull()?.id) {
                                            Modifier.focusProperties { up = primaryActionFocusRequester }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onClick = { onPlayEpisode(episode.id, chapter.positionMs) },
                            )
                        }
                    }
                }
            }
        }

        if (mediaDetail != null && (mediaDetail.video != null || mediaDetail.audioStreams.isNotEmpty())) {
            item {
                DetailContentSection(
                    title = "Media Details",
                    icon = Icons.Default.VideoLibrary,
                ) {
                    mediaDetail.video?.let { video ->
                        val videoChips = listOfNotNull(
                            video.resolution,
                            video.codec,
                            video.hdr,
                            video.bitrateKbps?.let { "${it} kbps" },
                            mediaDetail.container,
                        )
                        if (videoChips.isNotEmpty()) {
                            DetailMetaLine(
                                items = videoChips.map { DetailMetaItem(Icons.Default.HighQuality, it) },
                                modifier = Modifier.padding(horizontal = 56.dp),
                            )
                        }
                    }
                    mediaDetail.audioStreams.forEach { audio ->
                        val audioLabel = listOfNotNull(
                            audio.codec,
                            audio.channels,
                            audio.language,
                        ).joinToString("  ")
                        if (audioLabel.isNotBlank()) {
                            DetailMetaLine(
                                items = listOf(DetailMetaItem(Icons.Default.GraphicEq, audioLabel)),
                                modifier = Modifier.padding(horizontal = 56.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
