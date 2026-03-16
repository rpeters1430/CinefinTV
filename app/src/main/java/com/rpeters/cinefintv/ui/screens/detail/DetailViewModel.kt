package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeason
import com.rpeters.cinefintv.utils.isSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private var lastSuccessfulRefreshAtMs: Long = 0L

    init {
        load()
    }

    fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastSuccessfulRefreshAtMs < REFRESH_STALE_AFTER_MS) return
        refresh()
    }

    fun refresh() {
        if (itemId.isBlank()) return

        viewModelScope.launch { fetchAndBuildState(showLoading = _uiState.value !is DetailUiState.Content) }
    }

    fun load() {
        if (itemId.isBlank()) {
            _uiState.value = DetailUiState.Error("No item ID was provided.")
            return
        }

        viewModelScope.launch {
            fetchAndBuildState(showLoading = true)
        }
    }

    private suspend fun fetchAndBuildState(showLoading: Boolean) {
        if (showLoading) {
            _uiState.value = DetailUiState.Loading
        }

        when (val detailResult = repositories.media.getItemDetails(itemId)) {
            is ApiResult.Success -> {
                val item = detailResult.data
                val seasonsAndEpisodes = loadSeasonsAndEpisodes(item)
                val relatedItems = loadRelated(item)
                val playbackTarget = DetailPlaybackResolver.resolvePlaybackTarget(item, seasonsAndEpisodes.second)

                val parentForBackdrop: BaseItemDto? = if (item.isSeason()) {
                    item.seriesId?.toString()?.let { seriesId ->
                        (repositories.media.getItemDetails(seriesId) as? ApiResult.Success)?.data
                    }
                } else {
                    null
                }

                val heroModel = DetailMappers.toHeroModel(
                    item = item,
                    streamRepository = repositories.stream,
                    seasons = seasonsAndEpisodes.first,
                    episodesBySeasonId = seasonsAndEpisodes.second,
                    parentForBackdrop = parentForBackdrop,
                )

                _uiState.value = DetailUiState.Content(
                    item = heroModel,
                    seasons = seasonsAndEpisodes.first,
                    episodesBySeasonId = seasonsAndEpisodes.second,
                    related = relatedItems.map { DetailMappers.toHeroModel(it, repositories.stream) },
                    cast = heroModel.cast,
                    playableItemId = playbackTarget?.id,
                    playButtonLabel = playbackTarget?.label ?: "Play",
                    refreshErrorMessage = null,
                )
                lastSuccessfulRefreshAtMs = System.currentTimeMillis()
            }
            is ApiResult.Error -> {
                val currentState = _uiState.value
                if (!showLoading && currentState is DetailUiState.Content) {
                    _uiState.value = currentState.copy(refreshErrorMessage = detailResult.message)
                } else {
                    _uiState.value = DetailUiState.Error(detailResult.message)
                }
            }
            is ApiResult.Loading -> Unit
        }
    }

    fun requestDelete() {
        val state = _uiState.value as? DetailUiState.Content ?: return
        _uiState.value = state.copy(
            isDeleteConfirmationVisible = true,
            actionErrorMessage = null,
        )
    }

    fun cancelDelete() {
        val state = _uiState.value as? DetailUiState.Content ?: return
        if (state.isDeleting) return

        _uiState.value = state.copy(
            isDeleteConfirmationVisible = false,
            actionErrorMessage = null,
        )
    }

    fun dismissActionError() {
        val state = _uiState.value as? DetailUiState.Content ?: return
        _uiState.value = state.copy(actionErrorMessage = null)
    }

    fun confirmDelete() {
        val state = _uiState.value as? DetailUiState.Content ?: return
        if (state.isDeleting) return

        _uiState.value = state.copy(
            isDeleting = true,
            actionErrorMessage = null,
        )

        viewModelScope.launch {
            when (val result = repositories.user.deleteItem(state.item.id)) {
                is ApiResult.Success -> {
                    val latestState = _uiState.value as? DetailUiState.Content ?: return@launch
                    _uiState.value = latestState.copy(
                        isDeleting = false,
                        isDeleteConfirmationVisible = false,
                        isDeleted = true,
                    )
                }
                is ApiResult.Error -> {
                    val latestState = _uiState.value as? DetailUiState.Content ?: return@launch
                    _uiState.value = latestState.copy(
                        isDeleting = false,
                        actionErrorMessage = result.message,
                    )
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    private suspend fun loadSeasonsAndEpisodes(
        item: BaseItemDto,
    ): Pair<List<DetailSeasonModel>, Map<String, List<DetailEpisodeModel>>> = coroutineScope {
        return@coroutineScope when {
            item.isSeries() -> {
                val seasonsResult = repositories.media.getSeasonsForSeries(item.id.toString())
                val seasons = when (seasonsResult) {
                    is ApiResult.Success -> seasonsResult.data
                    else -> emptyList()
                }
                val episodesBySeason = seasons.map { season ->
                    async {
                        val episodeModels = when (
                            val episodesResult = repositories.media.getEpisodesForSeason(season.id.toString())
                        ) {
                            is ApiResult.Success -> episodesResult.data.map { DetailMappers.toEpisodeModel(it, repositories.stream) }
                            else -> emptyList()
                        }
                        season to episodeModels
                    }
                }.awaitAll()
                val allEpisodes = mutableListOf<DetailEpisodeModel>()
                val seasonModels = episodesBySeason.map { (season, episodeModels) ->
                    allEpisodes += episodeModels
                    val seasonWatchStatus = when {
                        episodeModels.isNotEmpty() && episodeModels.all { it.isWatched } -> WatchStatus.WATCHED
                        episodeModels.any { it.isWatched || it.canResume } -> WatchStatus.IN_PROGRESS
                        else -> WatchStatus.NONE
                    }
                    DetailSeasonModel(
                        id = season.id.toString(),
                        title = season.getDisplayTitle(),
                        subtitle = DetailMappers.buildSeasonSubtitle(season, episodeModels.size),
                        overview = season.overview?.takeIf { it.isNotBlank() },
                        imageUrl = repositories.stream.getPosterCardImageUrl(season, parentItem = item),
                        episodeCount = episodeModels.size.takeIf { it > 0 } ?: (season.childCount ?: 0),
                        watchStatus = seasonWatchStatus,
                        unwatchedCount = season.userData?.unplayedItemCount
                            ?: episodeModels.count { !it.isWatched }.takeIf { it > 0 },
                    )
                }
                seasonModels to mapOf(item.id.toString() to allEpisodes)
            }
            item.isSeason() -> {
                val episodesResult = repositories.media.getEpisodesForSeason(item.id.toString())
                val episodes = when (episodesResult) {
                    is ApiResult.Success -> episodesResult.data.map { DetailMappers.toEpisodeModel(it, repositories.stream) }
                    else -> emptyList()
                }
                emptyList<DetailSeasonModel>() to mapOf(item.id.toString() to episodes)
            }
            else -> emptyList<DetailSeasonModel>() to emptyMap()
        }
    }

    private suspend fun loadRelated(item: BaseItemDto): List<BaseItemDto> {
        val result = when {
            item.isMovie() -> repositories.media.getSimilarMovies(item.id.toString(), limit = 12)
            item.isSeries() -> repositories.media.getSimilarSeries(item.id.toString(), limit = 12)
            else -> return emptyList()
        }

        return when (result) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }
    }

    private companion object {
        private const val REFRESH_STALE_AFTER_MS = 5 * 60 * 1000L
    }
}
