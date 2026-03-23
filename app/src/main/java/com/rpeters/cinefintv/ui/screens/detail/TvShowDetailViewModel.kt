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
    val airedDate: String?,
    val endedDate: String?,
    val rating: String?,
    val secondaryRating: String?,
    val officialRating: String?,
    val status: String?,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val logoUrl: String?,
    val genres: List<String>,
    val videoQuality: String?,
    val audioLabel: String?,
    val creators: List<String>,
    val networks: List<String>,
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
        val selectedSeasonIndex: Int = 0,
        val episodes: List<EpisodeModel> = emptyList(),
        val resumeEpisodeIndex: Int = -1,
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
                        role = person.role ?: person.type.toString(),
                        imageUrl = repositories.stream.getImageUrl(
                            itemId = person.id.toString(),
                            tag = person.primaryImageTag
                        )
                    )
                } ?: emptyList()

                val initialContent = TvShowDetailUiState.Content(
                    show = seriesDto.toDetailModel(nextUp),
                    seasons = seasons,
                    cast = cast,
                    similarShows = similarShows,
                    selectedSeasonIndex = 0,
                    episodes = emptyList(),
                    resumeEpisodeIndex = -1,
                )
                _uiState.value = initialContent
                // Load episodes for the first season
                if (seasons.isNotEmpty()) {
                    loadEpisodesForSeason(seasons[0].id, 0)
                }
            } else if (seriesResult is ApiResult.Error) {
                _uiState.value = TvShowDetailUiState.Error(seriesResult.message)
            }
        }
    }

    fun selectSeason(index: Int) {
        val content = _uiState.value as? TvShowDetailUiState.Content ?: return
        if (index < 0 || index >= content.seasons.size) return
        if (index == content.selectedSeasonIndex) return
        _uiState.value = content.copy(selectedSeasonIndex = index, episodes = emptyList())
        viewModelScope.launch {
            loadEpisodesForSeason(content.seasons[index].id, index)
        }
    }

    private suspend fun loadEpisodesForSeason(seasonId: String, seasonIndex: Int) {
        val result = repositories.media.getEpisodesForSeason(seasonId)
        if (result is ApiResult.Success) {
            val episodes = result.data.map { it.toEpisodeModel() }
            val resumeIndex = episodes.indexOfFirst { it.playbackProgress != null && (it.playbackProgress ?: 0f) > 0f }
            val latestContent = _uiState.value as? TvShowDetailUiState.Content ?: return
            // Only update if still viewing the same season
            if (latestContent.selectedSeasonIndex == seasonIndex) {
                _uiState.value = latestContent.copy(
                    episodes = episodes,
                    resumeEpisodeIndex = resumeIndex,
                )
            }
        }
    }

    // Note: show.nextUpEpisodeId and show.nextUpTitle are not refreshed here to minimize
    // network calls. They will be updated on the next full load() call (e.g., screen re-entry).
    fun refreshWatchStatus() {
        _uiState.value as? TvShowDetailUiState.Content ?: return
        viewModelScope.launch {
            val seasonsResult = repositories.media.getSeasonsForSeries(seriesId)
            if (seasonsResult is ApiResult.Success) {
                val updatedSeasons = seasonsResult.data.map { it.toSeasonModel() }
                // Re-read state after the suspension point to avoid overwriting concurrent mutations
                val latestState = _uiState.value as? TvShowDetailUiState.Content ?: return@launch
                _uiState.value = latestState.copy(seasons = updatedSeasons)
            }
            // no-op on error — stale data is better than a flicker
        }
    }

    private fun BaseItemDto.toDetailModel(nextUp: BaseItemDto?): TvShowDetailModel {
        return TvShowDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            yearRange = getYearRange(),
            airedDate = premiereDate?.toString()?.substringBefore("T"),
            endedDate = endDate?.toString()?.substringBefore("T"),
            rating = communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
            secondaryRating = criticRating?.takeIf { it > 0 }?.toString(),
            officialRating = normalizeOfficialRating(officialRating),
            status = status,
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            logoUrl = repositories.stream.getLogoUrl(this),
            genres = genres ?: emptyList(),
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
            creators = people
                ?.filter {
                    it.type.toString().equals("Director", ignoreCase = true) ||
                        it.type.toString().equals("Writer", ignoreCase = true)
                }
                ?.mapNotNull { it.name }
                ?.distinct()
                ?: emptyList(),
            networks = studios?.mapNotNull { it.name } ?: emptyList(),
            nextUpEpisodeId = nextUp?.id?.toString(),
            nextUpTitle = nextUp?.getDisplayTitle(),
            seasonCount = childCount ?: 0
        )
    }

    private fun BaseItemDto.toSeasonModel(): SeasonModel {
        return SeasonModel(
            id = id.toString(),
            title = getDisplayTitle(),
            imageUrl = repositories.stream.getWideCardImageUrl(this),
            episodeCount = childCount,
            unwatchedCount = userData?.unplayedItemCount ?: 0
        )
    }

    private fun BaseItemDto.toEpisodeModel(): EpisodeModel {
        return EpisodeModel(
            id = id.toString(),
            title = getDisplayTitle(),
            number = indexNumber,
            overview = overview,
            imageUrl = repositories.stream.getBackdropUrl(this),
            duration = getFormattedDuration(),
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
            isWatched = isWatched(),
            playbackProgress = (getWatchedPercentage() / 100.0).toFloat(),
            episodeCode = getEpisodeCode()
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
