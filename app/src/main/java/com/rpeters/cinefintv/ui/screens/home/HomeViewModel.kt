package com.rpeters.cinefintv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getEpisodeCardDetailLine
import com.rpeters.cinefintv.utils.getEpisodeCode
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.getMediaQualityLabel
import com.rpeters.cinefintv.utils.getSeriesCardDetailLine
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCount
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isEpisode
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Only show loading if we don't have content yet
            if (_uiState.value !is HomeUiState.Content) {
                _uiState.value = HomeUiState.Loading
            }

            val librariesDeferred = async { repositories.media.getUserLibraries() }
            val continueDeferred = async { repositories.media.getContinueWatching(limit = 12) }
            val moviesDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12) }
            val episodesDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12) }
            val videosDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12) }
            val musicDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12) }

            val results = awaitAll(
                librariesDeferred,
                continueDeferred,
                moviesDeferred,
                episodesDeferred,
                videosDeferred,
                musicDeferred,
            )

            val continueWatchingResult = results[1]
            val nextEpisodesSectionItems = buildNextEpisodeSectionItems(continueWatchingResult)

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
                addSection("Recently Added TV Episodes", results[3])
                addSection("Recently Added Movies", results[2])
                addSection("Recently Added Music", results[5])
                addSection("Recently Added Videos", results[4])
            }

            val featuredItems = (results[2] as? ApiResult.Success<List<BaseItemDto>>)
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
            when (val result = repositories.media.getContinueWatching(limit = 12)) {
                is ApiResult.Success -> {
                    val nextEpisodeItems = buildNextEpisodeSectionItems(result)
                    val updatedSections = buildList {
                        for (section in currentState.sections) {
                            when (section.title) {
                                "Continue Watching" -> {
                                    if (result.data.isNotEmpty()) {
                                        add(section.copy(items = result.data.take(12).map(::toCardModel)))
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
                    val continueWatchingItems = result.data.take(12).map(::toCardModel)
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

                    _uiState.value = currentState.copy(sections = finalSections)
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
        continueWatchingResult: ApiResult<List<BaseItemDto>>,
    ): List<HomeCardModel> = coroutineScope {
        val continueItems = (continueWatchingResult as? ApiResult.Success<List<BaseItemDto>>)
            ?.data
            .orEmpty()

        if (continueItems.isEmpty()) return@coroutineScope emptyList()

        val uniqueContinueEpisodes = continueItems
            .filter { it.isEpisode() }
            .distinctBy { it.seriesId?.toString() ?: it.id.toString() }
            .take(12)

        val seenEpisodeIds = mutableSetOf<String>()

        val nextEpisodeResults = uniqueContinueEpisodes.map { currentEpisode ->
            async {
                when (val result = repositories.media.getNextEpisode(currentEpisode.id.toString())) {
                    is ApiResult.Success -> result.data
                    else -> null
                }
            }
        }.awaitAll()

        nextEpisodeResults.mapNotNull { nextEpisode ->
            nextEpisode?.takeIf { seenEpisodeIds.add(it.id.toString()) }?.let(::toCardModel)
        }
    }

    private fun toCardModel(item: BaseItemDto): HomeCardModel {
        val id = item.id.toString()
        val watchedPercentage = item.getWatchedPercentage()
        val isResumable = item.canResume()
        val isWatched = item.isWatched()
        
        val watchStatus = when {
            isWatched -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) watchedPercentage.toFloat() / 100f else null
        val unwatchedCount = if (item.isSeries()) item.getUnwatchedEpisodeCount().takeIf { it > 0 } else null

        val subtitle = when {
            item.type == org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER -> null
            isResumable -> {
                val pct = watchedPercentage
                val ticks = item.runTimeTicks
                if (ticks != null && ticks > 0) {
                    val remainingTicks = (ticks * (1.0 - pct / 100.0)).toLong()
                    val totalSeconds = remainingTicks / 10_000_000L
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    when {
                        hours > 0 -> "${hours}h ${minutes}m left"
                        minutes > 0 -> "${minutes}m left"
                        else -> "< 1m left"
                    }
                } else {
                    "${pct.toInt()}% watched"
                }
            }
            item.isEpisode() -> {
                val series = item.seriesName?.takeIf { it.isNotBlank() }
                val code = item.getEpisodeCode()?.replace(" · ", " ") // S1 E2
                listOfNotNull(series, code).joinToString("  ·  ").ifBlank { null }
            }
            item.isSeries() -> item.getSeriesCardDetailLine()
                ?: item.getYear()?.toString()
                ?: item.type.toString().replace('_', ' ')
            item.getYear() != null -> item.getYear().toString()
            item.getFormattedDuration() != null -> item.getFormattedDuration()
            else -> item.type.toString().replace('_', ' ')
        }

        return HomeCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = subtitle,
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
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
            unwatchedCount = unwatchedCount,
            mediaQuality = item.getMediaQualityLabel(),
        )
    }
}
