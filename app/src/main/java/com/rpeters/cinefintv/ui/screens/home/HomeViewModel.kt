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
import com.rpeters.cinefintv.data.common.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()

    init {
        loadCachedData()
        viewModelScope.launch {
            repositories.auth.currentServer.first { it != null }
            refresh(silent = true)
        }
        observeUpdateEvents()
    }

    private fun loadCachedData() {
        viewModelScope.launch {
            val cachedLibraries = repositories.media.getCachedLibraries()
            val cachedContinueWatching = repositories.media.getCachedContinueWatching()
            val cachedNextUp = repositories.media.getCachedNextUp()
            val cachedMovies = repositories.media.getCachedRecentlyAddedMovies()
            val cachedEpisodes = repositories.media.getCachedRecentlyAddedEpisodes()

            if (cachedLibraries != null || cachedContinueWatching != null || cachedNextUp != null || cachedMovies != null || cachedEpisodes != null) {
                // Offload heavy mapping to background thread
                val sections = withContext(dispatchers.default) {
                    buildList<HomeSectionModel> {
                        cachedLibraries?.let { libs ->
                            val filteredLibraries = libs.filter {
                                it.collectionType?.toString() != "playlists" && it.name?.contains("Playlists", ignoreCase = true) != true
                            }
                            if (filteredLibraries.isNotEmpty()) {
                                add(
                                    HomeSectionModel(
                                        id = HomeSectionId.LIBRARIES,
                                        title = HomeSectionId.LIBRARIES.displayTitle,
                                        items = filteredLibraries.take(12).map { toCardModel(it) },
                                    )
                                )
                            }
                        }

                        cachedContinueWatching?.let { items ->
                            val continueWatchingItems = items
                                .filter { it.canResume() == true }
                                .take(12)
                                .map { toCardModel(it) }
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

                        cachedMovies?.let { items ->
                            val movieItems = items.take(12).map { toCardModel(it) }
                            if (movieItems.isNotEmpty()) {
                                add(
                                    HomeSectionModel(
                                        id = HomeSectionId.RECENT_MOVIES,
                                        title = HomeSectionId.RECENT_MOVIES.displayTitle,
                                        items = movieItems,
                                    )
                                )
                            }
                        }

                        cachedEpisodes?.let { items ->
                            val episodeItems = items.take(12).map { toCardModel(it) }
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
                try {
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
                    val librariesResult = librariesDeferred.await()
                    val continueResult = continueDeferred.await()
                    val nextUpResult = nextUpDeferred.await()
                    val moviesResult = moviesDeferred.await()
                    val episodesResult = episodesDeferred.await()
                    val videosResult = videosDeferred.await()
                    val musicResult = musicDeferred.await()

                    // Offload intensive data processing to background thread
                    val (sections, featuredItems) = withContext(dispatchers.default) {
                        val nextEpisodesSectionItems = buildNextEpisodeSectionItems(nextUpResult)
                        val continueWatchingItems = (continueResult as? ApiResult.Success)?.data
                            ?.filter { it.canResume() }
                            ?.take(12)
                            ?.map { toCardModel(it) }
                            .orEmpty()

                        val builtSections = buildList<HomeSectionModel> {
                            if (librariesResult is ApiResult.Success) {
                                val filteredLibraries = librariesResult.data.filter {
                                    it.collectionType?.toString() != "playlists" && it.name?.contains("Playlists", ignoreCase = true) != true
                                }
                                if (filteredLibraries.isNotEmpty()) {
                                    add(
                                        HomeSectionModel(
                                            id = HomeSectionId.LIBRARIES,
                                            title = HomeSectionId.LIBRARIES.displayTitle,
                                            items = filteredLibraries.take(12).map { toCardModel(it) },
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
                            addSection(HomeSectionId.RECENT_EPISODES, episodesResult)
                            addSection(HomeSectionId.RECENT_MOVIES, moviesResult)
                            addSection(HomeSectionId.RECENT_MUSIC, musicResult)
                            addSection(HomeSectionId.RECENT_COLLECTIONS, videosResult)
                        }

                        val builtFeaturedItems = (moviesResult as? ApiResult.Success<List<BaseItemDto>>)
                            ?.data?.take(6)?.map { toCardModel(it) }
                            ?: emptyList()

                        builtSections to builtFeaturedItems
                    }

                    val newState = if (sections.isEmpty() && featuredItems.isEmpty()) {
                        val errorMessage = listOf(
                            librariesResult,
                            continueResult,
                            nextUpResult,
                            moviesResult,
                            episodesResult,
                            videosResult,
                            musicResult,
                        ).firstNotNullOfOrNull { result ->
                            (result as? ApiResult.Error)?.message
                        }
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
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val errorMessage = "Failed to load home content: ${e.message ?: "Unknown error"}"
                    android.util.Log.e("HomeViewModel", errorMessage, e)
                    _uiState.value = HomeUiState.Error(errorMessage)
                }
            }
        }
    }

    fun refreshWatchStatus() {
        _uiState.value as? HomeUiState.Content ?: return
        viewModelScope.launch {
            // Give server a moment to process the stop report
            delay(500)
            val continueDeferred = async { repositories.media.getContinueWatching(limit = 24) }
            val nextUpDeferred = async { repositories.media.getNextUp(limit = 12) }

            val continueResult = continueDeferred.await()
            val nextUpResult = nextUpDeferred.await()

            if (continueResult !is ApiResult.Success && nextUpResult !is ApiResult.Success) {
                return@launch
            }

            // Re-read state after the suspension point to avoid overwriting concurrent mutations
            val latestState = _uiState.value as? HomeUiState.Content ?: return@launch
            val continueWatchingItems = (continueResult as? ApiResult.Success)?.data
                ?.filter { it.canResume() }
                ?.take(12)
                ?.map { toCardModel(it) }
            val nextEpisodeItems = if (nextUpResult is ApiResult.Success) {
                buildNextEpisodeSectionItems(nextUpResult)
            } else {
                null
            }

            val finalSections = rebuildWatchSections(
                baseSections = latestState.sections,
                continueWatchingItems = continueWatchingItems,
                nextEpisodeItems = nextEpisodeItems,
            )

            val stabilizedState = latestState
                .copy(sections = latestState.stabilizeSections(finalSections))
                .stabilizeAgainst(latestState)

            if (_uiState.value != stabilizedState) {
                _uiState.value = stabilizedState
            }
        }
    }

    private fun rebuildWatchSections(
        baseSections: List<HomeSectionModel>,
        continueWatchingItems: List<HomeCardModel>?,
        nextEpisodeItems: List<HomeCardModel>?,
    ): List<HomeSectionModel> {
        val nonWatchSections = baseSections.filter {
            it.id != HomeSectionId.CONTINUE_WATCHING && it.id != HomeSectionId.NEXT_EPISODES
        }.toMutableList()
        val librariesIndex = nonWatchSections.indexOfFirst { it.id == HomeSectionId.LIBRARIES }
        val insertIndex = if (librariesIndex >= 0) librariesIndex + 1 else 0

        val refreshedWatchSections = buildList {
            when {
                continueWatchingItems != null && continueWatchingItems.isNotEmpty() -> {
                    add(
                        HomeSectionModel(
                            id = HomeSectionId.CONTINUE_WATCHING,
                            title = HomeSectionId.CONTINUE_WATCHING.displayTitle,
                            items = continueWatchingItems,
                        )
                    )
                }
                continueWatchingItems == null -> {
                    baseSections.firstOrNull { it.id == HomeSectionId.CONTINUE_WATCHING }?.let(::add)
                }
            }

            when {
                nextEpisodeItems != null && nextEpisodeItems.isNotEmpty() -> {
                    add(
                        HomeSectionModel(
                            id = HomeSectionId.NEXT_EPISODES,
                            title = HomeSectionId.NEXT_EPISODES.displayTitle,
                            items = nextEpisodeItems,
                        )
                    )
                }
                nextEpisodeItems == null -> {
                    baseSections.firstOrNull { it.id == HomeSectionId.NEXT_EPISODES }?.let(::add)
                }
            }
        }

        nonWatchSections.addAll(insertIndex, refreshedWatchSections)
        return nonWatchSections
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
