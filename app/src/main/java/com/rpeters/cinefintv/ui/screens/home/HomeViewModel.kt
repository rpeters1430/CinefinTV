package com.rpeters.cinefintv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCardLabel
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCount
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

import com.rpeters.cinefintv.ui.components.WatchStatus

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

            val sections = buildList {
                addSection("My Libraries", results[0])
                addSection("Continue Watching", results[1])
                addSection("Recently Added TV Episodes", results[3])
                addSection("Recently Added Movies", results[2])
                addSection("Recently Added Music", results[5])
                addSection("Recently Added Videos", results[4])
            }

            val featuredItems = (results[2] as? ApiResult.Success<List<BaseItemDto>>)
                ?.data?.take(6)?.mapNotNull { toCardModel(it) }
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

    private fun MutableList<HomeSectionModel>.addSection(
        title: String,
        result: ApiResult<List<BaseItemDto>>,
    ) {
        if (result is ApiResult.Success && result.data.isNotEmpty()) {
            add(
                HomeSectionModel(
                    title = title,
                    items = result.data.mapNotNull(::toCardModel),
                ),
            )
        }
    }

    private fun toCardModel(item: BaseItemDto): HomeCardModel? {
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
            item.isSeries() -> item.getUnwatchedEpisodeCardLabel()
                ?: item.getYear()?.toString()
                ?: item.type.toString().replace('_', ' ')
            item.getYear() != null -> item.getYear().toString()
            item.getFormattedDuration() != null -> item.getFormattedDuration()
            else -> item.type.toString().replace('_', ' ')
        }.let { subtitle ->
            if (subtitle?.contains("Collections", ignoreCase = true) == true) null else subtitle
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
            rating = (item.communityRating as? Number)?.toDouble()
                ?.takeIf { it > 0.0 }
                ?.let { String.format(java.util.Locale.US, "%.1f", it) },
            officialRating = item.officialRating?.takeIf { it.isNotBlank() },
            itemType = item.type.toString(),
            collectionType = item.collectionType?.toString(),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
            unwatchedCount = unwatchedCount,
        )
    }
}
