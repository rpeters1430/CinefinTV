package com.rpeters.cinefintv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.getMediaQualityLabel
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isEpisode
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.toMediaCardPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val title: String,
    val items: List<HomeCardModel>,
)

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

    init {
        refresh()
        observeUpdateEvents()
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
            val hasContent = _uiState.value is HomeUiState.Content
            if (!silent || !hasContent) {
                _uiState.value = HomeUiState.Loading
            }

            val librariesDeferred = async { repositories.media.getUserLibraries() }
            val continueDeferred = async { repositories.media.getContinueWatching(limit = 12) }
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

            val sections = buildList {
                val librariesResult = results[0]
                if (librariesResult is ApiResult.Success) {
                    val filteredLibraries = librariesResult.data.filter { 
                        it.collectionType?.toString() != "playlists" && it.name?.contains("Playlists", ignoreCase = true) != true
                    }
                    if (filteredLibraries.isNotEmpty()) {
                        add(
                            HomeSectionModel(
                                title = "My Libraries",
                                items = filteredLibraries.take(12).map(::toCardModel),
                            )
                        )
                    }
                }
                
                addSection("Continue Watching", results[1])
                if (nextEpisodesSectionItems.isNotEmpty()) {
                    add(HomeSectionModel(title = "Next Episodes", items = nextEpisodesSectionItems))
                }
                addSection("Recently Added TV Episodes", results[4])
                addSection("Recently Added Movies", results[3])
                addSection("Recently Added Music", results[6])
                addSection("Recently Added Collections", results[5])
            }

            val featuredItems = (results[3] as? ApiResult.Success<List<BaseItemDto>>)
                ?.data?.take(6)?.map { toCardModel(it) }
                ?: emptyList()

            if (sections.isEmpty() && featuredItems.isEmpty()) {
                val errorMessage = results.filterIsInstance<ApiResult.Error<List<BaseItemDto>>>()
                    .firstOrNull()
                    ?.message
                    ?: "No content is available yet."
                _uiState.value = HomeUiState.Error(errorMessage)
            } else {
                _uiState.value = HomeUiState.Content(
                    featuredItems = featuredItems,
                    sections = sections,
                )
            }
        }
    }

    fun refreshWatchStatus() {
        val currentState = _uiState.value as? HomeUiState.Content ?: return
        viewModelScope.launch {
            // Give server a moment to process the stop report
            delay(500)
            val continueDeferred = async { repositories.media.getContinueWatching(limit = 12) }
            val nextUpDeferred = async { repositories.media.getNextUp(limit = 12) }

            when (val continueResult = continueDeferred.await()) {
                is ApiResult.Success -> {
                    // Re-read state after the suspension point to avoid overwriting concurrent mutations
                    val latestState = _uiState.value as? HomeUiState.Content ?: return@launch
                    val nextEpisodeItems = buildNextEpisodeSectionItems(nextUpDeferred.await())
                    val updatedSections = buildList {
                        for (section in latestState.sections) {
                            when (section.title) {
                                "Continue Watching" -> {
                                    if (continueResult.data.isNotEmpty()) {
                                        add(section.copy(items = continueResult.data.take(12).map(::toCardModel)))
                                    }
                                    // If empty, omit the section (nothing to continue watching)
                                }
                                "Next Episodes" -> {
                                    // Will be re-added after the loop if non-empty
                                }
                                else -> add(section)
                            }
                        }
                    }.toMutableList()

                    // Insert Continue Watching + Next Episodes at correct positions
                    // They were not added inside the loop if section didn't exist before;
                    // find the insertion index (after "My Libraries" if present)
                    val continueWatchingItems = continueResult.data.take(12).map(::toCardModel)
                    val myLibrariesIdx = updatedSections.indexOfFirst { it.title == "My Libraries" }
                    val insertAfter = if (myLibrariesIdx >= 0) myLibrariesIdx else -1

                    // Remove any existing Continue Watching / Next Episodes (shouldn't be there but just in case)
                    val withoutCW = updatedSections.filter {
                        it.title != "Continue Watching" && it.title != "Next Episodes"
                    }.toMutableList()

                    val toInsert = buildList {
                        if (continueWatchingItems.isNotEmpty()) {
                            add(HomeSectionModel(title = "Continue Watching", items = continueWatchingItems))
                        }
                        if (nextEpisodeItems.isNotEmpty()) {
                            add(HomeSectionModel(title = "Next Episodes", items = nextEpisodeItems))
                        }
                    }

                    val finalSections = if (toInsert.isNotEmpty()) {
                        withoutCW.toMutableList().apply {
                            addAll(insertAfter + 1, toInsert)
                        }
                    } else {
                        withoutCW
                    }

                    _uiState.value = latestState.copy(sections = finalSections)
                }
                else -> { /* no-op on error — stale data is better than a flicker */ }
            }
        }
    }

    private fun MutableList<HomeSectionModel>.addSection(
        title: String,
        result: ApiResult<List<BaseItemDto>>,
    ) {
        if (result is ApiResult.Success && result.data.isNotEmpty()) {
            add(
                HomeSectionModel(
                    title = title,
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
}
