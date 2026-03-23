@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.withFrameNanos
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
import com.rpeters.cinefintv.ui.components.TvMediaCard
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
    val scope = rememberCoroutineScope()
    val anchorFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val primaryActionFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val firstContentFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    var lastFocusedChapterId by rememberSaveable { mutableStateOf<String?>(chapters.firstOrNull()?.id) }

    LaunchedEffect(episode.id) {
        listState.scrollToItem(0)
        withFrameNanos {}   // wait one frame for layout to attach the anchor node
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
                backdropUrl = episode.backdropUrl,
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
            ) {
                DetailGlassPanel(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(horizontal = 56.dp, vertical = 32.dp),
                ) {
                    DetailMetaLine(
                        items = buildList {
                            add(DetailMetaItem(Icons.Default.PlayCircle, "Episode"))
                            episode.episodeCode?.let { add(DetailMetaItem(Icons.Default.Tv, it)) }
                            episode.year?.let { add(DetailMetaItem(Icons.Default.CalendarToday, "$it")) }
                            episode.duration?.let { add(DetailMetaItem(Icons.Default.Schedule, it)) }
                            if (episode.isWatched) add(DetailMetaItem(Icons.Default.Visibility, "Watched"))
                        }
                    )
                    episode.seriesName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    episode.overview?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                        )
                    }
                    episode.playbackProgress?.let {
                        DetailProgressLabel(progress = it)
                    }
                    DetailActionRow(
                        primaryLabel = when {
                            episode.playbackProgress != null -> "Resume Episode"
                            episode.isWatched -> "Play Again"
                            else -> "Play Episode"
                        },
                        onPrimaryClick = { onPlayEpisode(episode.id, null) },
                        secondaryLabel = if (chapters.isNotEmpty()) "Jump to Chapter" else null,
                        onSecondaryClick = if (chapters.isNotEmpty()) {
                            { onPlayEpisode(episode.id, chapters.first().positionMs) }
                        } else {
                            null
                        },
                        primaryFocusRequester = primaryActionFocusRequester,
                        primaryDownFocusRequester = if (chapters.isNotEmpty()) firstContentFocusRequester else null,
                    )
                }
            }
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
                                        if (chapter.id == lastFocusedChapterId) Modifier.focusRequester(firstContentFocusRequester) else Modifier
                                    )
                                    .then(
                                        if (chapter.id == chapters.firstOrNull()?.id) {
                                            Modifier.focusProperties { up = primaryActionFocusRequester }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onFocus = { lastFocusedChapterId = chapter.id },
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
                    // Video row
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
                    // Audio rows (one per stream)
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
