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

data class SeasonDetailModel(
    val id: String,
    val title: String,
    val seriesName: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
)

data class EpisodeModel(
    val id: String,
    val title: String,
    val number: Int?,
    val overview: String?,
    val imageUrl: String?,
    val duration: String?,
    val videoQuality: String?,
    val audioLabel: String?,
    val isWatched: Boolean,
    val playbackProgress: Float?,
    val episodeCode: String?,
)

sealed class SeasonUiState {
    data object Loading : SeasonUiState()
    data class Error(val message: String) : SeasonUiState()
    data class Content(
        val season: SeasonDetailModel,
        val episodes: List<EpisodeModel>,
    ) : SeasonUiState()
}

@HiltViewModel
class SeasonViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seasonId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<SeasonUiState>(SeasonUiState.Loading)
    val uiState: StateFlow<SeasonUiState> = _uiState.asStateFlow()

    init {
        if (seasonId.isBlank()) {
            _uiState.value = SeasonUiState.Error("Invalid season ID")
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
                        // Refresh watch status if any item was updated
                        refreshWatchStatus()
                    }
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> {
                        load(silent = true)
                    }
                }
            }
        }
    }

    fun refreshWatchStatus() {
        _uiState.value as? SeasonUiState.Content ?: return
        viewModelScope.launch {
            val episodesResult = repositories.media.getEpisodesForSeason(seasonId)
            if (episodesResult is ApiResult.Success) {
                val latestState = _uiState.value as? SeasonUiState.Content ?: return@launch
                _uiState.value = latestState.copy(
                    episodes = episodesResult.data.map { it.toEpisodeModel() }
                )
            }
        }
    }

    fun markEpisodeWatched(episodeId: String) {
        viewModelScope.launch {
            if (repositories.user.markAsWatched(episodeId) is ApiResult.Success) {
                updateBus.refreshItem(episodeId)
                refreshWatchStatus()
            }
        }
    }

    fun markEpisodeUnwatched(episodeId: String) {
        viewModelScope.launch {
            if (repositories.user.markAsUnwatched(episodeId) is ApiResult.Success) {
                updateBus.refreshItem(episodeId)
                refreshWatchStatus()
            }
        }
    }

    fun deleteEpisode(episodeId: String) {
        viewModelScope.launch {
            if (repositories.user.deleteItemAsAdmin(episodeId) is ApiResult.Success) {
                updateBus.refreshAll()
                refreshWatchStatus()
            }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value is SeasonUiState.Content
            if (!silent || !hasContent) {
                _uiState.value = SeasonUiState.Loading
            }

            val seasonResult = repositories.media.getItemDetails(seasonId)
            val episodesResult = repositories.media.getEpisodesForSeason(seasonId)

            if (seasonResult is ApiResult.Success) {
                val seasonDto = seasonResult.data
                val seriesDto = seasonDto.seriesId?.let { 
                    when (val seriesResult = repositories.media.getSeriesDetails(it.toString())) {
                        is ApiResult.Success -> seriesResult.data
                        else -> null
                    }
                }
                
                val episodes = if (episodesResult is ApiResult.Success) {
                    episodesResult.data.map { it.toEpisodeModel() }
                } else {
                    emptyList()
                }

                _uiState.value = SeasonUiState.Content(
                    season = seasonDto.toSeasonDetailModel(seriesDto),
                    episodes = episodes
                )
            } else if (seasonResult is ApiResult.Error) {
                _uiState.value = SeasonUiState.Error(seasonResult.message)
            }
        }
    }

    private fun BaseItemDto.toSeasonDetailModel(seriesDto: BaseItemDto?): SeasonDetailModel {
        return SeasonDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            seriesName = seriesName,
            overview = overview,
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            backdropUrl = repositories.stream.getBackdropUrlWithFallback(this, seriesDto)
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
}
