@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun SeasonScreen(
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
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
            is SeasonUiState.Loading -> DetailLoadingState()
            is SeasonUiState.Error -> DetailErrorState(
                message = state.message,
                onRetry = { viewModel.load() },
            )
            is SeasonUiState.Content -> SeasonContent(
                season = state.season,
                episodes = state.episodes,
                onOpenEpisode = onOpenEpisode,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun SeasonContent(
    season: SeasonDetailModel,
    episodes: List<EpisodeModel>,
    onOpenEpisode: (String) -> Unit,
    viewModel: SeasonViewModel,
) {
    val spacing = LocalCinefinSpacing.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val topFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val overviewFocusRequester = remember { FocusRequester() }
    val firstEpisodeFocusRequester = remember { FocusRequester() }
    val episodesSectionIndex = 2
    var selectedEpisode by remember { mutableStateOf<EpisodeModel?>(null) }
    var pendingDeleteEpisode by remember { mutableStateOf<EpisodeModel?>(null) }

    val resumeEpisode = remember(episodes) {
        episodes.firstOrNull { (it.playbackProgress ?: 0f) > 0f && !it.isWatched }
            ?: episodes.firstOrNull { !it.isWatched }
    }
    
    val factItems = remember(season, episodes) {
        buildList {
            season.seriesName?.let {
                add(DetailLabeledMetaItem(Icons.Default.Subscriptions, "Series", it))
            }
            add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Episodes", episodes.size.toString()))
            resumeEpisode?.episodeCode?.let {
                add(DetailLabeledMetaItem(Icons.Default.VideoLibrary, "Continue With", it))
            }
        }
    }

    LaunchedEffect(season.id) {
        focusDetailScreenAtTop(
            listState = listState,
            initialFocusRequester = primaryActionFocusRequester,
            anchorFocusRequester = topFocusRequester,
        )
    }

    selectedEpisode?.let { episode ->
        MediaActionDialog(
            title = episode.title,
            actions = buildList {
                add(
                    MediaActionDialogItem(
                        label = "Play",
                        supportingText = "Start playback immediately.",
                        onClick = { onOpenEpisode(episode.id) },
                    )
                )
                add(
                    MediaActionDialogItem(
                        label = if (episode.isWatched) "Mark unwatched" else "Mark watched",
                        supportingText = "Update the watched state for this episode.",
                        onClick = {
                            if (episode.isWatched) {
                                viewModel.markEpisodeUnwatched(episode.id)
                            } else {
                                viewModel.markEpisodeWatched(episode.id)
                            }
                        },
                    )
                )
                add(
                    MediaActionDialogItem(
                        label = "Delete",
                        supportingText = "Remove this episode from the library.",
                        isDestructive = true,
                        onClick = { pendingDeleteEpisode = episode },
                    )
                )
            },
            onDismissRequest = { selectedEpisode = null },
        )
    }

    pendingDeleteEpisode?.let { episode ->
        ConfirmDeleteDialog(
            title = "Delete ${episode.title}?",
            message = "This will remove the episode from your Jellyfin library.",
            onDismissRequest = { pendingDeleteEpisode = null },
            onConfirmDelete = {
                pendingDeleteEpisode = null
                selectedEpisode = null
                viewModel.deleteEpisode(episode.id)
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.gutter * 2),
    ) {
        item {
            Column(
                modifier = Modifier.blockBringIntoView()
            ) {
                DetailAnchor(
                    focusRequester = topFocusRequester,
                    downFocusRequester = primaryActionFocusRequester,
                    onFocused = {},
                )
                CinematicHero(
                    backdropUrl = season.backdropUrl,
                    logoUrl = null,
                    title = season.title,
                    eyebrow = listOfNotNull(season.seriesName, "${episodes.size} episodes").joinToString(" · "),
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = if (resumeEpisode != null) "▶ Resume" else "▶ Play",
                    onPrimaryAction = {
                        val target = resumeEpisode ?: episodes.firstOrNull()
                        if (target != null) onOpenEpisode(target.id)
                    },
                    secondaryActions = if (episodes.isNotEmpty())
                        listOf("Start From Episode 1" to { onOpenEpisode(episodes.first().id) })
                    else emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    primaryActionDownFocusRequester = overviewFocusRequester,
                    upFocusRequester = topFocusRequester,
                    listState = listState,
                )
            }
        }

        item {
            DetailOverviewSection(
                title = season.title,
                posterUrl = season.posterUrl,
                description = season.overview.orEmpty(),
                factItems = factItems,
                chips = emptyList(),
                focusRequester = overviewFocusRequester,
                upFocusRequester = primaryActionFocusRequester,
                downFocusRequester = null,
                modifier = Modifier
                    .padding(top = spacing.rowGap)
                    .onPreviewKeyEvent { event ->
                        if (
                            episodes.isNotEmpty() &&
                            event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            coroutineScope.launch {
                                // Anchor to the section header before moving focus so the first
                                // episode enters in a stable position without a second nudge.
                                listState.scrollToItem(episodesSectionIndex)
                                yield()
                                firstEpisodeFocusRequester.requestFocus()
                            }
                            true
                        } else {
                            false
                        }
                    },
            )
        }

        item {
            DetailContentSection(
                title = "Episodes",
                eyebrow = "${episodes.count { !it.isWatched }} unwatched",
                icon = Icons.Default.VideoLibrary,
            ) {}
        }

        items(episodes, key = { it.id }) { episode ->
            EpisodeListRow(
                episode = episode,
                isNext = episode.id == resumeEpisode?.id,
                modifier = if (episode.id == episodes.firstOrNull()?.id) {
                    Modifier
                        .focusRequester(firstEpisodeFocusRequester)
                        .focusProperties { up = overviewFocusRequester }
                } else {
                    Modifier
                },
                onMenuAction = { selectedEpisode = episode },
                onClick = { onOpenEpisode(episode.id) },
            )
        }
    }
}
