package com.rpeters.cinefintv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.getMediaQualityLabel
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isEpisode
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.toMediaCardPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class HomeCardModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val backdropUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val runtime: String? = null,
    val rating: String? = null,
    val officialRating: String? = null,
    val itemType: String? = null,
    val collectionType: String? = null,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
    val mediaQuality: String? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
)

data class HomeSectionModel(
    val id: HomeSectionId,
    val title: String,
    val items: List<HomeCardModel>,
)

enum class HomeSectionId(val displayTitle: String) {
    LIBRARIES("My Libraries"),
    CONTINUE_WATCHING("Continue Watching"),
    NEXT_EPISODES("Next Episodes"),
    RECENT_EPISODES("Recently Added TV Episodes"),
    RECENT_MOVIES("Recently Added Movies"),
    RECENT_MUSIC("Recently Added Music"),
    RECENT_COLLECTIONS("Recently Added Collections"),
}

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Error(val message: String) : HomeUiState()
    data class Content(
        val featuredItems: List<HomeCardModel>,
        val sections: List<HomeSectionModel>,
    ) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()

    init {
        loadCachedData()
        viewModelScope.launch {
            // Give session restoration a moment to fully propagate across all flows
            delay(100)
            refresh(silent = true)
        }
        observeUpdateEvents()
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            val cachedLibraries = repositories.media.getCachedLibraries()
            val cachedContinueWatching = repositories.media.getCachedContinueWatching()
            val cachedNextUp = repositories.media.getCachedNextUp()
            val cachedRecentlyAdded = repositories.media.getCachedRecentlyAdded()

            if (cachedLibraries != null || cachedContinueWatching != null || cachedNextUp != null || cachedRecentlyAdded != null) {
                val sections = buildList {
                    cachedLibraries?.let { libs ->
                        val filteredLibraries = libs.filter {
                            it.collectionType?.toString() != "playlists" && it.name?.contains("Playlists", ignoreCase = true) != true
                        }
                        if (filteredLibraries.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.LIBRARIES,
                                    title = HomeSectionId.LIBRARIES.displayTitle,
                                    items = filteredLibraries.take(12).map(::toCardModel),
                                )
                            )
                        }
                    }

                    cachedContinueWatching?.let { items ->
                        val continueWatchingItems = items
                            .filter { it.canResume() == true }
                            .take(12)
                            .map(::toCardModel)
                        if (continueWatchingItems.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.CONTINUE_WATCHING,
                                    title = HomeSectionId.CONTINUE_WATCHING.displayTitle,
                                    items = continueWatchingItems,
                                )
                            )
                        }
                    }

                    cachedNextUp?.let { items ->
                        val nextUpItems = buildNextEpisodeSectionItems(ApiResult.Success(items))
                        if (nextUpItems.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.NEXT_EPISODES,
                                    title = HomeSectionId.NEXT_EPISODES.displayTitle,
                                    items = nextUpItems,
                                )
                            )
                        }
                    }

                    // For simplicity, we just use the first available recently added cache for both movies and episodes if applicable
                    cachedRecentlyAdded?.let { items ->
                        val movieItems = items.filter { it.isMovie() }.take(12).map(::toCardModel)
                        if (movieItems.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.RECENT_MOVIES,
                                    title = HomeSectionId.RECENT_MOVIES.displayTitle,
                                    items = movieItems,
                                )
                            )
                        }

                        val episodeItems = items.filter { it.isEpisode() }.take(12).map(::toCardModel)
                        if (episodeItems.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.RECENT_EPISODES,
                                    title = HomeSectionId.RECENT_EPISODES.displayTitle,
                                    items = episodeItems,
                                )
                            )
                        }
                    }
                }

                if (sections.isNotEmpty()) {
                    _uiState.value = HomeUiState.Content(
                        featuredItems = emptyList(), // Featured usually requires fresh context
                        sections = sections
                    )
                }
            }
        }
    }

    private fun observeUpdateEvents() {
        viewModelScope.launch {
            updateBus.events.collect { event ->
                when (event) {
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshItem -> {
                        refreshWatchStatus()
                    }
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                        refresh(silent = true)
                    }
                }
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            refreshMutex.withLock {
                val hasContent = _uiState.value is HomeUiState.Content
                if (!silent || !hasContent) {
                    _uiState.value = HomeUiState.Loading
                }

                val librariesDeferred = async { repositories.media.getUserLibraries() }
                val continueDeferred = async { repositories.media.getContinueWatching(limit = 24) }
                val nextUpDeferred = async { repositories.media.getNextUp(limit = 12) }
                val moviesDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12) }
                val episodesDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12) }
                val videosDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12) }
                val musicDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12) }

                val results = awaitAll(
                    librariesDeferred,
                    continueDeferred,
                    nextUpDeferred,
                    moviesDeferred,
                    episodesDeferred,
                    videosDeferred,
                    musicDeferred,
                )

                val nextEpisodesSectionItems = buildNextEpisodeSectionItems(results[2])
                val continueWatchingItems = (results[1] as? ApiResult.Success)?.data
                    ?.filter { it.canResume() }
                    ?.take(12)
                    ?.map(::toCardModel)
                    .orEmpty()

                val sections = buildList {
                    val librariesResult = results[0]
                    if (librariesResult is ApiResult.Success) {
                        val filteredLibraries = librariesResult.data.filter {
                            it.collectionType?.toString() != "playlists" && it.name?.contains("Playlists", ignoreCase = true) != true
                        }
                        if (filteredLibraries.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.LIBRARIES,
                                    title = HomeSectionId.LIBRARIES.displayTitle,
                                    items = filteredLibraries.take(12).map(::toCardModel),
                                )
                            )
                        }
                    }
                    if (continueWatchingItems.isNotEmpty()) {
                        add(
                            HomeSectionModel(
                                id = HomeSectionId.CONTINUE_WATCHING,
                                title = HomeSectionId.CONTINUE_WATCHING.displayTitle,
                                items = continueWatchingItems,
                            )
                        )
                    }
                    if (nextEpisodesSectionItems.isNotEmpty()) {
                        add(
                            HomeSectionModel(
                                id = HomeSectionId.NEXT_EPISODES,
                                title = HomeSectionId.NEXT_EPISODES.displayTitle,
                                items = nextEpisodesSectionItems,
                            )
                        )
                    }
                    addSection(HomeSectionId.RECENT_EPISODES, results[4])
                    addSection(HomeSectionId.RECENT_MOVIES, results[3])
                    addSection(HomeSectionId.RECENT_MUSIC, results[6])
                    addSection(HomeSectionId.RECENT_COLLECTIONS, results[5])
                }

                val featuredItems = (results[3] as? ApiResult.Success<List<BaseItemDto>>)
                    ?.data?.take(6)?.map { toCardModel(it) }
                    ?: emptyList()

                val newState = if (sections.isEmpty() && featuredItems.isEmpty()) {
                    val errorMessage = results.filterIsInstance<ApiResult.Error<List<BaseItemDto>>>()
                        .firstOrNull()
                        ?.message
                        ?: "No content is available yet."
                    HomeUiState.Error(errorMessage)
                } else {
                    HomeUiState.Content(
                        featuredItems = featuredItems,
                        sections = sections,
                    ).stabilizeAgainst(_uiState.value as? HomeUiState.Content)
                }

                if (_uiState.value != newState) {
                    _uiState.value = newState
                }
            }
        }
    }

    fun refreshWatchStatus() {
        val currentState = _uiState.value as? HomeUiState.Content ?: return
        viewModelScope.launch {
            // Give server a moment to process the stop report
            delay(500)
            val continueDeferred = async { repositories.media.getContinueWatching(limit = 24) }
            val nextUpDeferred = async { repositories.media.getNextUp(limit = 12) }

            when (val continueResult = continueDeferred.await()) {
                is ApiResult.Success -> {
                    // Re-read state after the suspension point to avoid overwriting concurrent mutations
                    val latestState = _uiState.value as? HomeUiState.Content ?: return@launch
                    val nextEpisodeItems = buildNextEpisodeSectionItems(nextUpDeferred.await())
                    
                    val continueWatchingItems = continueResult.data
                        .filter { it.canResume() }
                        .take(12)
                        .map(::toCardModel)

                    val updatedSections = buildList {
                        for (section in latestState.sections) {
                            when (section.id) {
                                HomeSectionId.CONTINUE_WATCHING -> {
                                    if (continueWatchingItems.isNotEmpty()) {
                                        add(section.copy(items = continueWatchingItems))
                                    }
                                    // If empty, omit the section
                                }
                                HomeSectionId.NEXT_EPISODES -> {
                                    // Will be re-added after the loop
                                }
                                else -> add(section)
                            }
                        }
                    }.toMutableList()

                    val myLibrariesIdx = updatedSections.indexOfFirst { it.id == HomeSectionId.LIBRARIES }
                    val insertAfter = if (myLibrariesIdx >= 0) myLibrariesIdx else -1

                    val withoutCW = updatedSections.filter {
                        it.id != HomeSectionId.CONTINUE_WATCHING && it.id != HomeSectionId.NEXT_EPISODES
                    }.toMutableList()

                    val toInsert = buildList {
                        if (continueWatchingItems.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.CONTINUE_WATCHING,
                                    title = HomeSectionId.CONTINUE_WATCHING.displayTitle,
                                    items = continueWatchingItems,
                                )
                            )
                        }
                        if (nextEpisodeItems.isNotEmpty()) {
                            add(
                                HomeSectionModel(
                                    id = HomeSectionId.NEXT_EPISODES,
                                    title = HomeSectionId.NEXT_EPISODES.displayTitle,
                                    items = nextEpisodeItems,
                                )
                            )
                        }
                    }

                    val finalSections = if (toInsert.isNotEmpty()) {
                        withoutCW.toMutableList().apply {
                            addAll(insertAfter + 1, toInsert)
                        }
                    } else {
                        withoutCW
                    }

                    val stabilizedState = latestState
                        .copy(sections = latestState.stabilizeSections(finalSections))
                        .stabilizeAgainst(latestState)

                    if (_uiState.value != stabilizedState) {
                        _uiState.value = stabilizedState
                    }
                }
                else -> { /* no-op on error — stale data is better than a flicker */ }
            }
        }
    }

    private fun MutableList<HomeSectionModel>.addSection(
        sectionId: HomeSectionId,
        result: ApiResult<List<BaseItemDto>>,
    ) {
        if (result is ApiResult.Success && result.data.isNotEmpty()) {
            add(
                HomeSectionModel(
                    id = sectionId,
                    title = sectionId.displayTitle,
                    items = result.data
                        .take(12)
                        .map(::toCardModel),
                ),
            )
        }
    }

    private suspend fun buildNextEpisodeSectionItems(
        nextUpResult: ApiResult<List<BaseItemDto>>,
    ): List<HomeCardModel> {
        val nextUpItems = (nextUpResult as? ApiResult.Success<List<BaseItemDto>>)
            ?.data
            .orEmpty()

        return nextUpItems
            .filter { it.isEpisode() }
            // Filter out episodes from shows that haven't been started.
            // A show is considered "started" if we're past the first episode (S1:E1)
            // or if we have already made progress on the first episode itself.
            .filter { item ->
                val isFirstEpisode = (item.parentIndexNumber ?: 1) <= 1 && (item.indexNumber ?: 1) <= 1
                val hasProgress = (item.userData?.playedPercentage ?: 0.0) > 0.0 || item.userData?.played == true
                
                !isFirstEpisode || hasProgress
            }
            .distinctBy { it.id.toString() }
            .take(12)
            .map(::toCardModel)
    }

    private fun toCardModel(item: BaseItemDto): HomeCardModel {
        val id = item.id.toString()
        val presentation = item.toMediaCardPresentation()

        return HomeCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = presentation.subtitle,
            imageUrl = repositories.stream.getLandscapeImageUrl(item),
            backdropUrl = repositories.stream.getBackdropUrl(item),
            description = item.overview?.take(140),
            year = item.getYear(),
            runtime = item.getFormattedDuration(),
            rating = item.communityRating
                ?.takeIf { it > 0.0 }
                ?.let { String.format(java.util.Locale.US, "%.1f", it) },
            officialRating = item.officialRating?.takeIf { it.isNotBlank() },
            itemType = item.getItemTypeString(),
            collectionType = item.collectionType?.toString(),
            watchStatus = presentation.watchStatus,
            playbackProgress = presentation.playbackProgress,
            unwatchedCount = presentation.unwatchedCount,
            mediaQuality = item.getMediaQualityLabel(),
            seriesId = item.seriesId?.toString(),
            seasonId = item.parentId?.toString(),
        )
    }

    private fun HomeUiState.Content.stabilizeAgainst(
        previous: HomeUiState.Content?,
    ): HomeUiState.Content {
        previous ?: return this

        val stabilizedFeaturedItems = if (previous.featuredItems == featuredItems) {
            previous.featuredItems
        } else {
            featuredItems
        }
        val stabilizedSections = previous.stabilizeSections(sections)

        return if (
            stabilizedFeaturedItems === previous.featuredItems &&
            stabilizedSections === previous.sections
        ) {
            previous
        } else {
            copy(
                featuredItems = stabilizedFeaturedItems,
                sections = stabilizedSections,
            )
        }
    }

    private fun HomeUiState.Content.stabilizeSections(
        incomingSections: List<HomeSectionModel>,
    ): List<HomeSectionModel> {
        if (sections == incomingSections) {
            return sections
        }

        val previousById = sections.associateBy(HomeSectionModel::id)
        val stabilizedSections = incomingSections.map { incomingSection ->
            previousById[incomingSection.id]?.takeIf { it == incomingSection } ?: incomingSection
        }

        return if (stabilizedSections == sections) {
            sections
        } else {
            stabilizedSections
        }
    }
}
