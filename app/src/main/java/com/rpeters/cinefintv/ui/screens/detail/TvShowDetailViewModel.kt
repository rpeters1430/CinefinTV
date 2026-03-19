package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
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
    val rating: String?,
    val officialRating: String?,
    val status: String?,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val genres: List<String>,
    val nextUpEpisodeId: String?,
    val nextUpTitle: String?,
    val seasonCount: Int,
)

data class SeasonModel(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val episodeCount: Int?,
    val unwatchedCount: Int,
)

sealed class TvShowDetailUiState {
    data object Loading : TvShowDetailUiState()
    data class Error(val message: String) : TvShowDetailUiState()
    data class Content(
        val show: TvShowDetailModel,
        val seasons: List<SeasonModel>,
        val cast: List<CastModel>,
        val similarShows: List<SimilarMovieModel>, // Reusing SimilarMovieModel for simplicity
    ) : TvShowDetailUiState()
}

@HiltViewModel
class TvShowDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<TvShowDetailUiState>(TvShowDetailUiState.Loading)
    val uiState: StateFlow<TvShowDetailUiState> = _uiState.asStateFlow()

    init {
        if (seriesId.isBlank()) {
            _uiState.value = TvShowDetailUiState.Error("Invalid series ID")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = TvShowDetailUiState.Loading

            val seriesResult = repositories.media.getSeriesDetails(seriesId)
            val seasonsResult = repositories.media.getSeasonsForSeries(seriesId)
            val similarResult = repositories.media.getSimilarSeries(seriesId)
            val nextUpResult = repositories.media.getNextUpForSeries(seriesId)

            if (seriesResult is ApiResult.Success) {
                val seriesDto = seriesResult.data
                val seasons = if (seasonsResult is ApiResult.Success) {
                    seasonsResult.data.map { it.toSeasonModel() }
                } else {
                    emptyList()
                }

                val similarShows = if (similarResult is ApiResult.Success) {
                    similarResult.data.map { it.toSimilarModel() }
                } else {
                    emptyList()
                }

                val nextUp = (nextUpResult as? ApiResult.Success)?.data

                val cast = seriesDto.people?.map { person ->
                    CastModel(
                        id = person.id.toString(),
                        name = person.name ?: "Unknown",
                        role = person.role ?: person.type?.toString(),
                        imageUrl = repositories.stream.getImageUrl(
                            itemId = person.id.toString(),
                            tag = person.primaryImageTag
                        )
                    )
                } ?: emptyList()

                _uiState.value = TvShowDetailUiState.Content(
                    show = seriesDto.toDetailModel(nextUp),
                    seasons = seasons,
                    cast = cast,
                    similarShows = similarShows
                )
            } else if (seriesResult is ApiResult.Error) {
                _uiState.value = TvShowDetailUiState.Error(seriesResult.message)
            }
        }
    }

    private fun BaseItemDto.toDetailModel(nextUp: BaseItemDto?): TvShowDetailModel {
        return TvShowDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            yearRange = getYearRange(),
            rating = communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
            officialRating = officialRating,
            status = status,
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            genres = genres ?: emptyList(),
            nextUpEpisodeId = nextUp?.id?.toString(),
            nextUpTitle = nextUp?.getDisplayTitle(),
            seasonCount = childCount ?: 0
        )
    }

    private fun BaseItemDto.toSeasonModel(): SeasonModel {
        return SeasonModel(
            id = id.toString(),
            title = getDisplayTitle(),
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            episodeCount = childCount,
            unwatchedCount = userData?.unplayedItemCount ?: 0
        )
    }

    private fun BaseItemDto.toSimilarModel(): SimilarMovieModel {
        return SimilarMovieModel(
            id = id.toString(),
            title = getDisplayTitle(),
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
        )
    }
}
