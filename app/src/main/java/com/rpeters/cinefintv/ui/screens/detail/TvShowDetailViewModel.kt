package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class TvShowDetailModel(
    val id: String,
    val title: String,
    val yearRange: String?,
    val premieredDate: String?,
    val endedDate: String?,
    val rating: String?,
    val officialRating: String?,
    val seasonCount: Int,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val logoUrl: String?,
    val genres: List<String>,
    val networks: List<String>,
    val status: String?,
    val creators: List<String>,
    val nextUpEpisodeId: String?,
    val nextUpTitle: String?,
    val isWatched: Boolean,
)

sealed class TvShowDetailUiState {
    data object Loading : TvShowDetailUiState()
    data class Error(val message: String) : TvShowDetailUiState()
    data class Content(
        val show: TvShowDetailModel,
        val seasons: List<SeasonModel>,
        val episodes: List<EpisodeModel>,
        val cast: List<CastModel>,
        val similarShows: List<SimilarMovieModel>,
    ) : TvShowDetailUiState()
}

@HiltViewModel
class TvShowDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
) : ViewModel() {

    private var showId: String = ""

    private val _uiState = MutableStateFlow<TvShowDetailUiState>(TvShowDetailUiState.Loading)
    val uiState: StateFlow<TvShowDetailUiState> = _uiState.asStateFlow()

    fun init(id: String) {
        if (showId == id) return
        showId = id
        if (showId.isBlank()) {
            _uiState.value = TvShowDetailUiState.Error("Invalid series ID")
        } else {
            load()
            observeUpdateEvents()
        }
    }

    private fun observeUpdateEvents() {
        viewModelScope.launch {
            updateBus.events.collect { event ->
                when (event) {
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshItem -> {
                        if (event.itemId == showId) {
                            refreshWatchStatus()
                        }
                    }
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                        load(silent = true)
                    }
                }
            }
        }
    }

    fun refreshWatchStatus() {
        _uiState.value as? TvShowDetailUiState.Content ?: return
        viewModelScope.launch {
            val showResult = repositories.media.getSeriesDetails(showId)
            val seasonsResult = repositories.media.getSeasonsForSeries(showId)
            val nextUpResult = repositories.media.getNextUpForSeries(showId)
            
            val currentState = _uiState.value
            if (currentState is TvShowDetailUiState.Content) {
                val showDto = (showResult as? ApiResult.Success)?.data
                val nextUpDto = (nextUpResult as? ApiResult.Success)?.data
                val seasons = if (seasonsResult is ApiResult.Success) {
                    seasonsResult.data.map { it.toSeasonModel() }
                } else {
                    currentState.seasons
                }

                _uiState.value = currentState.copy(
                    show = showDto?.toDetailModel(nextUpDto) ?: currentState.show,
                    seasons = seasons
                )
            }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value is TvShowDetailUiState.Content
            if (!silent || !hasContent) {
                _uiState.value = TvShowDetailUiState.Loading
            }

            val showResult = repositories.media.getSeriesDetails(showId)
            val seasonsResult = repositories.media.getSeasonsForSeries(showId)
            val nextUpResult = repositories.media.getNextUpForSeries(showId)
            val similarResult = repositories.media.getSimilarSeries(showId)

            if (showResult is ApiResult.Success) {
                val showDto = showResult.data
                val nextUpDto = (nextUpResult as? ApiResult.Success)?.data
                
                val seasons = if (seasonsResult is ApiResult.Success) {
                    seasonsResult.data.map { it.toSeasonModel() }
                } else {
                    emptyList()
                }

                val episodes = emptyList<EpisodeModel>()

                val similar = if (similarResult is ApiResult.Success) {
                    similarResult.data.map { it.toSimilarModel() }
                } else {
                    emptyList()
                }

                val cast = showDto.people?.map { person ->
                    CastModel(
                        id = person.id.toString(),
                        name = person.name ?: "Unknown",
                        role = person.role ?: person.type.toString(),
                        imageUrl = repositories.stream.getImageUrl(
                            itemId = person.id.toString(),
                            tag = person.primaryImageTag
                        )
                    )
                } ?: emptyList()

                _uiState.value = TvShowDetailUiState.Content(
                    show = showDto.toDetailModel(nextUpDto),
                    seasons = seasons,
                    episodes = episodes,
                    cast = cast,
                    similarShows = similar
                )
            } else if (showResult is ApiResult.Error) {
                _uiState.value = TvShowDetailUiState.Error(showResult.message)
            }
        }
    }

    fun deleteShow(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (repositories.user.deleteItemAsAdmin(showId) is ApiResult.Success) {
                updateBus.refreshAll()
                onDeleted()
            }
        }
    }

    private fun BaseItemDto.toDetailModel(nextUpDto: BaseItemDto?): TvShowDetailModel {
        return TvShowDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            yearRange = getYearRange(),
            premieredDate = premiereDate?.toString()?.substringBefore("T"),
            endedDate = endDate?.toString()?.substringBefore("T"),
            rating = communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
            officialRating = normalizeOfficialRating(officialRating),
            seasonCount = childCount ?: 0,
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            logoUrl = repositories.stream.getLogoUrl(this),
            genres = genres ?: emptyList(),
            networks = studios?.mapNotNull { it.name } ?: emptyList(),
            status = when (status) {
                "Continuing" -> "Airing"
                "Ended" -> "Ended"
                else -> status
            },
            creators = people?.filter { it.type.toString().equals("Creator", ignoreCase = true) }
                ?.mapNotNull { it.name } ?: emptyList(),
            nextUpEpisodeId = nextUpDto?.id?.toString(),
            nextUpTitle = nextUpDto?.getDisplayTitle(),
            isWatched = isWatched(),
        )
    }

    private fun BaseItemDto.toSeasonModel(): SeasonModel {
        val watchedPercentage = getWatchedPercentage()
        val watchStatus = when {
            isWatched() -> WatchStatus.WATCHED
            canResume() -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }

        return SeasonModel(
            id = id.toString(),
            title = getDisplayTitle(),
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            watchStatus = watchStatus,
            playbackProgress = if (canResume()) watchedPercentage.toFloat() / 100f else null,
            unwatchedCount = userData?.unplayedItemCount ?: 0,
        )
    }

    private fun BaseItemDto.toSimilarModel(): SimilarMovieModel {
        val watchedPercentage = getWatchedPercentage()
        val watchStatus = when {
            isWatched() -> WatchStatus.WATCHED
            canResume() -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }

        return SimilarMovieModel(
            id = id.toString(),
            title = getDisplayTitle(),
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            watchStatus = watchStatus,
            playbackProgress = if (canResume()) watchedPercentage.toFloat() / 100f else null,
        )
    }
}
