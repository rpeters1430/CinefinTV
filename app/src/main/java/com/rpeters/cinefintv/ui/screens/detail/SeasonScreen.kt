@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.components.ConfirmDeleteDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialog
import com.rpeters.cinefintv.ui.components.MediaActionDialogItem
import com.rpeters.cinefintv.ui.rememberTopLevelDestinationFocus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailStripTitle
import com.rpeters.cinefintv.ui.screens.detail.cinematic.FlatDetailHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroSecondaryAction
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SeasonScreen(
    itemId: String,
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) {
        viewModel.init(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasBeenPaused by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasBeenPaused = true
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
                onBack = onBack,
                showDeleteDialog = showDeleteDialog,
                onShowDeleteDialogChange = { showDeleteDialog = it },
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
    onBack: () -> Unit,
    showDeleteDialog: Boolean,
    onShowDeleteDialogChange: (Boolean) -> Unit,
    viewModel: SeasonViewModel,
) {
    val spacing = LocalCinefinSpacing.current
    val coroutineScope = rememberCoroutineScope()
    val chromeFocusController = LocalAppChromeFocusController.current
    val listState = rememberLazyListState()
    val topFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }
    val destinationFocus = rememberTopLevelDestinationFocus(primaryActionFocusRequester)
    val firstEpisodeFocusRequester = remember { FocusRequester() }
    var selectedEpisode by remember { mutableStateOf<EpisodeModel?>(null) }
    var pendingDeleteEpisode by remember { mutableStateOf<EpisodeModel?>(null) }
    var showSeasonMenu by remember { mutableStateOf(false) }

    val resumeEpisode = remember(episodes) {
        episodes.firstOrNull { (it.playbackProgress ?: 0f) > 0f && !it.isWatched }
            ?: episodes.firstOrNull { !it.isWatched }
    }

    var didInitialFocus by remember(season.id) { mutableStateOf(false) }
    LaunchedEffect(season.id) {
        if (!didInitialFocus) {
            focusDetailScreenAtTop(
                listState = listState,
                initialFocusRequester = primaryActionFocusRequester,
                anchorFocusRequester = topFocusRequester,
            )
            didInitialFocus = true
        }
    }

    if (showSeasonMenu) {
        MediaActionDialog(
            title = season.title,
            actions = buildList {
                if (episodes.isNotEmpty()) {
                    add(
                        MediaActionDialogItem(
                            label = "Play From Episode 1",
                            supportingText = "Start playback from the beginning of this season.",
                            onClick = { onOpenEpisode(episodes.first().id) },
                        )
                    )
                }
                add(
                    MediaActionDialogItem(
                        label = "Delete Season",
                        supportingText = "Remove this season from the library.",
                        isDestructive = true,
                        onClick = { onShowDeleteDialogChange(true) },
                    )
                )
            },
            onDismissRequest = { showSeasonMenu = false },
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
                            if (episode.isWatched) viewModel.markEpisodeUnwatched(episode.id)
                            else viewModel.markEpisodeWatched(episode.id)
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

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete ${season.title}?",
            message = "This will remove the season from your Jellyfin library.",
            onDismissRequest = { onShowDeleteDialogChange(false) },
            onConfirmDelete = {
                onShowDeleteDialogChange(false)
                chromeFocusController?.shouldRestoreFocusToContent = true
                viewModel.deleteSeason(onBack)
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.gutter),
    ) {
        item {
            DetailAnchor(
                focusRequester = topFocusRequester,
                downFocusRequester = primaryActionFocusRequester,
                onFocused = {},
            )
            FlatDetailHero(
                backdropUrl = season.backdropUrl,
                posterUrl = season.posterUrl,
                title = season.title,
                metadataItems = listOf("${episodes.size} episodes"),
                qualityBadges = emptyList(),
                genres = emptyList(),
                summary = season.overview,
                primaryActionLabel = if (resumeEpisode != null) "Resume" else "Play",
                onPrimaryAction = {
                    val target = resumeEpisode ?: episodes.firstOrNull()
                    if (target != null) onOpenEpisode(target.id)
                },
                secondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = { showSeasonMenu = true })),
                primaryActionFocusRequester = primaryActionFocusRequester,
                primaryActionDownFocusRequester = if (episodes.isNotEmpty()) firstEpisodeFocusRequester else null,
                drawerFocusRequester = destinationFocus.drawerFocusRequester,
                onDownNavigation = if (episodes.isNotEmpty()) {
                    {
                        if (listState.isScrollInProgress) return@FlatDetailHero
                        coroutineScope.launch {
                            listState.scrollToItem(2)
                            runCatching { firstEpisodeFocusRequester.requestFocus() }
                        }
                    }
                } else {
                    null
                },
            )
        }

        item {
            DetailStripTitle(
                title = "Episodes",
                modifier = Modifier
                    .padding(top = spacing.rowGap.div(1.5f))
                    .padding(horizontal = spacing.gutter),
            )
        }

        items(episodes, key = { it.id }) { episode ->
            EpisodeListRow(
                episode = episode,
                isNext = episode.id == resumeEpisode?.id,
                modifier = if (episode.id == episodes.firstOrNull()?.id) {
                    Modifier
                        .focusRequester(firstEpisodeFocusRequester)
                        .focusProperties { up = primaryActionFocusRequester }
                } else {
                    Modifier
                },
                onMenuAction = { selectedEpisode = episode },
                onClick = { onOpenEpisode(episode.id) },
            )
        }
    }
}
