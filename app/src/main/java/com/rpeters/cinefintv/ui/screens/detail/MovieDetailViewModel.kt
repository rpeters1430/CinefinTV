package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject

data class MovieDetailModel(
    val id: String,
    val title: String,
    val year: Int?,
    val rating: String?,
    val officialRating: String?,
    val duration: String?,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val genres: List<String>,
    val studios: List<String>,
    val isWatched: Boolean,
    val playbackProgress: Float?,
)

data class CastModel(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
)

data class SimilarMovieModel(
    val id: String,
    val title: String,
    val imageUrl: String?,
)

sealed class MovieDetailUiState {
    data object Loading : MovieDetailUiState()
    data class Error(val message: String) : MovieDetailUiState()
    data class Content(
        val movie: MovieDetailModel,
        val cast: List<CastModel>,
        val similarMovies: List<SimilarMovieModel>,
    ) : MovieDetailUiState()
}

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val movieId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        if (movieId.isBlank()) {
            _uiState.value = MovieDetailUiState.Error("Invalid movie ID")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = MovieDetailUiState.Loading

            val movieResult = repositories.media.getMovieDetails(movieId)
            val similarResult = repositories.media.getSimilarMovies(movieId)

            if (movieResult is ApiResult.Success) {
                val movieDto = movieResult.data
                val similarMovies = if (similarResult is ApiResult.Success) {
                    similarResult.data.map { it.toSimilarModel() }
                } else {
                    emptyList()
                }

                val cast = movieDto.people?.map { person ->
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

                _uiState.value = MovieDetailUiState.Content(
                    movie = movieDto.toDetailModel(),
                    cast = cast,
                    similarMovies = similarMovies
                )
            } else if (movieResult is ApiResult.Error) {
                _uiState.value = MovieDetailUiState.Error(movieResult.message)
            }
        }
    }

    private fun BaseItemDto.toDetailModel(): MovieDetailModel {
        return MovieDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            year = getYear(),
            rating = communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
            officialRating = officialRating,
            duration = getFormattedDuration(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            genres = genres ?: emptyList(),
            studios = studios?.mapNotNull { it.name } ?: emptyList(),
            isWatched = isWatched(),
            playbackProgress = if (canResume()) (getWatchedPercentage() / 100.0).toFloat() else null,
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
