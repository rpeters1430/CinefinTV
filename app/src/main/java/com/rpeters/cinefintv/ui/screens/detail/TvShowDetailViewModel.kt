package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYearRange
import com.rpeters.cinefintv.utils.isWatched
import com.rpeters.cinefintv.utils.normalizeOfficialRating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.SeriesStatus
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

data class SeasonModel(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
    val unwatchedCount: Int,
)

data class EpisodeModel(
    val id: String,
    val title: String,
    val number: Int?,
    val episodeCode: String?,
    val duration: String?,
    val overview: String?,
    val imageUrl: String?,
    val videoQuality: String?,
    val audioLabel: String?,
    val isWatched: Boolean,
    val playbackProgress: Float?,
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val showId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<TvShowDetailUiState>(TvShowDetailUiState.Loading)
    val uiState: StateFlow<TvShowDetailUiState> = _uiState.asStateFlow()

    init {
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
                        // Refresh if the updated item is this series, or if it might be an episode of this series
                        // For simplicity, we refresh the Next Up and potentially seasons/episodes
                        refreshSilently()
                    }
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                        load(silent = true)
                    }
                }
            }
        }
    }

    private fun refreshSilently() {
        viewModelScope.launch {
            // Re-fetch essential status data for the series to update next-up or watched status
            val showResult = repositories.media.getSeriesDetails(showId)
            if (showResult is ApiResult.Success) {
                val showDto = showResult.data
                val currentState = _uiState.value
                if (currentState is TvShowDetailUiState.Content) {
                    // Update only the series metadata
                    _uiState.value = currentState.copy(
                        show = showDto.toDetailModel()
                    )
                }
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
            val seasonsResult = repositories.media.getSeasons(showId)
            val episodesResult = repositories.media.getEpisodes(showId) // Get all for first-play fallback
            val similarResult = repositories.media.getSimilarMovies(showId)

            if (showResult is ApiResult.Success) {
                val showDto = showResult.data
                val seasons = if (seasonsResult is ApiResult.Success) {
                    seasonsResult.data.map { it.toSeasonModel() }
                } else {
                    emptyList()
                }

                val episodes = if (episodesResult is ApiResult.Success) {
                    episodesResult.data.map { it.toEpisodeModel() }
                } else {
                    emptyList()
                }

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
                    show = showDto.toDetailModel(),
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

    private fun BaseItemDto.toDetailModel(): TvShowDetailModel {
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
                SeriesStatus.CONTINUING -> "Airing"
                SeriesStatus.ENDED -> "Ended"
                else -> status?.toString()
            },
            creators = people?.filter { it.type.toString().equals("Creator", ignoreCase = true) }
                ?.mapNotNull { it.name } ?: emptyList(),
            nextUpEpisodeId = nextUpEpisodeId,
            nextUpTitle = nextUpTitle,
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
            unwatchedCount = userData?.unwatchedItemCount ?: 0,
        )
    }

    private fun BaseItemDto.toEpisodeModel(): EpisodeModel {
        return EpisodeModel(
            id = id.toString(),
            title = getDisplayTitle(),
            number = indexNumber,
            episodeCode = "S${parentIndexNumber} E${indexNumber}",
            duration = com.rpeters.cinefintv.utils.getFormattedDuration(this),
            overview = overview,
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            videoQuality = com.rpeters.cinefintv.utils.getMediaQualityLabel(this),
            audioLabel = null, // simplified
            isWatched = isWatched(),
            playbackProgress = if (canResume()) (getWatchedPercentage() / 100.0).toFloat() else null,
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
