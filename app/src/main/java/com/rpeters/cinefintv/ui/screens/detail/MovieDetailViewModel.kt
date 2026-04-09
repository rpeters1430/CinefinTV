package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getMediaQualityLabel
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import com.rpeters.cinefintv.utils.normalizeOfficialRating
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
    val premieredDate: String?,
    val rating: String?,
    val secondaryRating: String?,
    val officialRating: String?,
    val duration: String?,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val logoUrl: String?,
    val genres: List<String>,
    val studios: List<String>,
    val videoQuality: String?,
    val audioLabel: String?,
    val directors: List<String>,
    val isWatched: Boolean,
    val playbackProgress: Float?,
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
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
) : ViewModel() {

    private var movieId: String = ""

    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    fun init(id: String) {
        if (movieId == id) return
        movieId = id
        if (movieId.isBlank()) {
            _uiState.value = MovieDetailUiState.Error("Invalid movie ID")
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
                        if (event.itemId == movieId) {
                            refreshSilently()
                        }
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
            val movieResult = repositories.media.getMovieDetails(movieId)
            if (movieResult is ApiResult.Success) {
                val movieDto = movieResult.data
                val currentState = _uiState.value
                if (currentState is MovieDetailUiState.Content) {
                    _uiState.value = currentState.copy(
                        movie = movieDto.toDetailModel()
                    )
                }
            }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value is MovieDetailUiState.Content
            if (!silent || !hasContent) {
                _uiState.value = MovieDetailUiState.Loading
            }

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
                        role = person.role ?: person.type.toString(),
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
            premieredDate = premiereDate?.toString()?.substringBefore("T"),
            rating = communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
            secondaryRating = criticRating?.takeIf { it > 0 }?.toString(),
            officialRating = normalizeOfficialRating(officialRating),
            duration = getFormattedDuration(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            logoUrl = repositories.stream.getLogoUrl(this),
            genres = genres ?: emptyList(),
            studios = studios?.mapNotNull { it.name } ?: emptyList(),
            videoQuality = getMediaQualityLabel(),
            audioLabel = mediaSources
                ?.firstOrNull()
                ?.mediaStreams
                ?.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }
                ?.firstOrNull()
                ?.let { stream ->
                    val codec = when (stream.codec?.uppercase()) {
                        "EAC3", "E-AC3" -> "EAC3"
                        "AC3" -> "AC3"
                        "TRUEHD" -> "TrueHD"
                        "DTS" -> "DTS"
                        "AAC" -> "AAC"
                        "FLAC" -> "FLAC"
                        "OPUS" -> "Opus"
                        else -> stream.codec?.uppercase()
                    }
                    val channels = when (stream.channels) {
                        2 -> "Stereo"
                        6 -> "5.1"
                        8 -> "7.1"
                        else -> stream.channels?.let { "$it ch" }
                    }
                    listOfNotNull(codec, channels).joinToString(" ").ifBlank { null }
                },
            directors = people
                ?.filter { it.type.toString().equals("Director", ignoreCase = true) }
                ?.mapNotNull { it.name }
                ?: emptyList(),
            isWatched = isWatched(),
            playbackProgress = if (canResume()) (getWatchedPercentage() / 100.0).toFloat() else null,
        )
    }

    fun refreshWatchStatus() {
        _uiState.value as? MovieDetailUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repositories.media.getMovieDetails(movieId)) {
                is ApiResult.Success -> {
                    val dto = result.data
                    // Re-read state after the suspension point to avoid overwriting concurrent mutations
                    val latestState = _uiState.value as? MovieDetailUiState.Content ?: return@launch
                    _uiState.value = latestState.copy(
                        movie = latestState.movie.copy(
                            isWatched = dto.isWatched(),
                            playbackProgress = if (dto.canResume()) (dto.getWatchedPercentage() / 100.0).toFloat() else null,
                        )
                    )
                }
                else -> { /* no-op on error — stale data is better than a flicker */ }
            }
        }
    }

    fun markWatched() {
        viewModelScope.launch {
            if (repositories.user.markAsWatched(movieId) is ApiResult.Success) {
                updateBus.refreshItem(movieId)
                refreshWatchStatus()
            }
        }
    }

    fun markUnwatched() {
        viewModelScope.launch {
            if (repositories.user.markAsUnwatched(movieId) is ApiResult.Success) {
                updateBus.refreshItem(movieId)
                refreshWatchStatus()
            }
        }
    }

    fun deleteMovie(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (repositories.user.deleteItemAsAdmin(movieId) is ApiResult.Success) {
                updateBus.refreshAll()
                onDeleted()
            }
        }
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
