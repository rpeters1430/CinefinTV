package com.rpeters.cinefintv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
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
            _uiState.value = HomeUiState.Loading

            val continueDeferred = async { repositories.media.getContinueWatching(limit = 12) }
            val moviesDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 12) }
            val episodesDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 12) }
            val videosDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 12) }
            val musicDeferred = async { repositories.media.getRecentlyAddedByType(BaseItemKind.AUDIO, limit = 12) }

            val results: List<ApiResult<List<BaseItemDto>>> = awaitAll(
                continueDeferred,
                moviesDeferred,
                episodesDeferred,
                videosDeferred,
                musicDeferred,
            )

            val sections = buildList {
                addSection("Continue Watching", results[0])
                addSection("Recently Added TV Episodes", results[2])
                addSection("Recently Added Movies", results[1])
                addSection("Recently Added Music", results[4])
                addSection("Recently Added Videos", results[3])
            }

            val featuredItems = (results[1] as? ApiResult.Success<List<BaseItemDto>>)
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
        val subtitle = when {
            item.canResume() -> {
                val pct = item.getWatchedPercentage()
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
            rating = (item.communityRating as? Number)?.toDouble()
                ?.takeIf { it > 0.0 }
                ?.let { String.format(java.util.Locale.US, "%.1f", it) },
            officialRating = item.officialRating?.takeIf { it.isNotBlank() },
        )
    }
}
