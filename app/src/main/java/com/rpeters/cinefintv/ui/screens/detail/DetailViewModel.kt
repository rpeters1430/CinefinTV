package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCount
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.getYearRange
import com.rpeters.cinefintv.utils.isEpisode
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeason
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

data class DetailInfoRowModel(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
)

data class DetailPersonModel(
    val id: String,
    val name: String,
    val role: String?,
    val type: String?,
    val imageUrl: String?,
)

data class DetailHeroModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val imageUrl: String?,
    val backdropUrl: String?,
    val metaBadges: List<String>,
    val infoRows: List<DetailInfoRowModel>,
    val cast: List<DetailPersonModel> = emptyList(),
)

data class DetailSeasonModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val imageUrl: String? = null,
    val episodeCount: Int = 0,
)

data class DetailEpisodeModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val imageUrl: String?,
    val canResume: Boolean = false,
    val isWatched: Boolean = false,
)

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Error(val message: String) : DetailUiState()
    data class Content(
        val item: DetailHeroModel,
        val seasons: List<DetailSeasonModel>,
        val episodesBySeasonId: Map<String, List<DetailEpisodeModel>>,
        val related: List<DetailHeroModel>,
        val cast: List<DetailPersonModel>,
        val playableItemId: String?,
        val playButtonLabel: String,
        val isDeleteConfirmationVisible: Boolean = false,
        val isDeleting: Boolean = false,
        val isDeleted: Boolean = false,
        val actionErrorMessage: String? = null,
    ) : DetailUiState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (itemId.isBlank()) {
            _uiState.value = DetailUiState.Error("No item ID was provided.")
            return
        }

        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            when (val detailResult = repositories.media.getItemDetails(itemId)) {
                is ApiResult.Success -> {
                    val item = detailResult.data
                    val seasonsAndEpisodes = loadSeasonsAndEpisodes(item)
                    val relatedItems = loadRelated(item)
                    val playbackTarget = resolvePlaybackTarget(item, seasonsAndEpisodes.second)
                    val heroModel = toHeroModel(
                        item = item,
                        seasons = seasonsAndEpisodes.first,
                        episodesBySeasonId = seasonsAndEpisodes.second,
                    )

                    _uiState.value = DetailUiState.Content(
                        item = heroModel,
                        seasons = seasonsAndEpisodes.first,
                        episodesBySeasonId = seasonsAndEpisodes.second,
                        related = relatedItems.map(this@DetailViewModel::toHeroModel),
                        cast = heroModel.cast,
                        playableItemId = playbackTarget?.id,
                        playButtonLabel = playbackTarget?.label ?: "Play",
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = DetailUiState.Error(detailResult.message)
                }
                is ApiResult.Loading -> Unit
            }
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
    ): Pair<List<DetailSeasonModel>, Map<String, List<DetailEpisodeModel>>> {
        return when {
            item.isSeries() -> {
                val seasonsResult = repositories.media.getSeasonsForSeries(item.id.toString())
                val seasons = when (seasonsResult) {
                    is ApiResult.Success -> seasonsResult.data
                    else -> emptyList()
                }
                val allEpisodes = mutableListOf<DetailEpisodeModel>()
                val seasonModels = seasons.map { season ->
                    val episodeModels = when (
                        val episodesResult = repositories.media.getEpisodesForSeason(season.id.toString())
                    ) {
                        is ApiResult.Success -> episodesResult.data.map(::toEpisodeModel)
                        else -> emptyList()
                    }
                    allEpisodes += episodeModels
                    DetailSeasonModel(
                        id = season.id.toString(),
                        title = season.getDisplayTitle(),
                        subtitle = buildSeasonSubtitle(season, episodeModels.size),
                        overview = season.overview?.takeIf { it.isNotBlank() },
                        imageUrl = repositories.stream.getWideCardImageUrl(season, parentItem = item),
                        episodeCount = episodeModels.size.takeIf { it > 0 } ?: (season.childCount ?: 0),
                    )
                }
                seasonModels to mapOf(item.id.toString() to allEpisodes)
            }
            item.isSeason() -> {
                val episodesResult = repositories.media.getEpisodesForSeason(item.id.toString())
                val episodes = when (episodesResult) {
                    is ApiResult.Success -> episodesResult.data.map(::toEpisodeModel)
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

    private fun toHeroModel(
        item: BaseItemDto,
        seasons: List<DetailSeasonModel> = emptyList(),
        episodesBySeasonId: Map<String, List<DetailEpisodeModel>> = emptyMap(),
    ): DetailHeroModel {
        val totalEpisodeCount = when {
            item.isSeries() -> seasons.sumOf { it.episodeCount }
            item.isSeason() -> episodesBySeasonId.values.flatten().size
            else -> 0
        }
        val subtitleParts = buildList {
            add(item.getYearRange() ?: item.getYear()?.toString())
            if (!item.isSeries() && !item.isSeason()) {
                add(item.getFormattedDuration())
            }
        }.filterNotNull()

        val metaBadges = buildList {
            if (!item.isSeries() && !item.isSeason() && !item.isMovie() && !item.isEpisode()) {
                add(item.getMediaTypeLabel())
            }
            item.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
        }

        val infoRows = buildList {
            if (item.isSeries() && seasons.isNotEmpty()) {
                add(DetailInfoRowModel("Seasons", seasons.size.toString(), Icons.Default.Layers))
            }
            if (item.isSeason() && totalEpisodeCount > 0) {
                add(DetailInfoRowModel("Episodes", totalEpisodeCount.toString(), Icons.AutoMirrored.Filled.FormatListBulleted))
            }
            item.getUnwatchedEpisodeCount()
                .takeIf { it > 0 }
                ?.let { add(DetailInfoRowModel("Unwatched", it.toString(), Icons.Default.VisibilityOff)) }
            
            val yearRange = item.getYearRange()
            if (yearRange != null) {
                add(DetailInfoRowModel("Year", yearRange, Icons.Default.CalendarToday))
            }

            if (!item.isSeries() && !item.isSeason()) {
                item.getFormattedDuration()?.let {
                    add(DetailInfoRowModel("Duration", it, Icons.Default.Timer))
                }
            }

            formatCommunityRating(item)?.let {
                add(DetailInfoRowModel("Rating", it, Icons.Default.Star))
            }

            item.genres.orEmpty()
                .take(3)
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
                ?.let { add(DetailInfoRowModel("Genres", it, Icons.Default.Category)) }
            
            item.studios.orEmpty()
                .firstOrNull()
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { add(DetailInfoRowModel("Studio", it, Icons.Default.Domain)) }
        }

        val cast = item.people.orEmpty().map { person ->
            DetailPersonModel(
                id = person.id.toString(),
                name = person.name ?: "Unknown",
                role = person.role,
                type = person.type.toString(),
                imageUrl = repositories.stream.getImageUrl(
                    itemId = person.id.toString(),
                    imageType = "Primary",
                    tag = person.primaryImageTag
                )
            )
        }

        return DetailHeroModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = subtitleParts.joinToString(" | ").ifBlank { null },
            overview = item.overview?.takeIf { it.isNotBlank() },
            imageUrl = repositories.stream.getLandscapeImageUrl(item),
            backdropUrl = repositories.stream.getBackdropUrl(item),
            metaBadges = metaBadges,
            infoRows = infoRows,
            cast = cast,
        )
    }

    private fun toEpisodeModel(item: BaseItemDto): DetailEpisodeModel {
        val episodeNumber = item.indexNumber?.let { "E$it" }
        val episodeMetadata = listOfNotNull(
            item.parentIndexNumber?.let { "S$it" },
            episodeNumber,
            item.getFormattedDuration(),
        )
            .joinToString(" | ")
            .ifBlank { null }

        return DetailEpisodeModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = episodeMetadata,
            overview = item.overview?.takeIf { it.isNotBlank() },
            imageUrl = repositories.stream.getLandscapeImageUrl(item),
            canResume = item.canResume(),
            isWatched = item.isWatched(),
        )
    }

    private fun resolvePlaybackTarget(
        item: BaseItemDto,
        episodesBySeasonId: Map<String, List<DetailEpisodeModel>>,
    ): PlaybackTarget? {
        return when {
            item.isMovie() || item.isEpisode() -> PlaybackTarget(
                id = item.id.toString(),
                label = if (item.canResume()) "Resume" else "Play",
            )
            item.isSeason() -> {
                val firstEpisode = episodesBySeasonId.values.flatten().firstOrNull() ?: return null
                PlaybackTarget(firstEpisode.id, "Play Season")
            }
            item.isSeries() -> {
                val episodes = episodesBySeasonId[item.id.toString()].orEmpty()
                val targetEpisode = episodes.firstOrNull { it.canResume }
                    ?: episodes.firstOrNull { !it.isWatched }
                    ?: episodes.firstOrNull()
                    ?: return null
                PlaybackTarget(
                    id = targetEpisode.id,
                    label = if (targetEpisode.canResume) "Resume Show" else "Play Show",
                )
            }
            else -> null
        }
    }

    private fun buildSeasonSubtitle(item: BaseItemDto, loadedEpisodeCount: Int): String? {
        val episodeCount = (loadedEpisodeCount.takeIf { it > 0 } ?: (item.childCount ?: 0))
            .takeIf { it > 0 }
            ?.let { count -> if (count == 1) "1 episode" else "$count episodes" }
        val unwatchedCount = item.userData?.unplayedItemCount
            ?.takeIf { it > 0 }
            ?.let { count -> if (count == 1) "1 left" else "$count left" }

        return listOfNotNull(episodeCount, unwatchedCount)
            .joinToString(" | ")
            .ifBlank { null }
    }

    private fun formatCommunityRating(item: BaseItemDto): String? {
        val rating = (item.communityRating as? Number)?.toDouble() ?: return null
        if (rating <= 0.0) return null
        return String.format(Locale.US, "%.1f/10", rating)
    }

    private fun BaseItemDto.getMediaTypeLabel(): String =
        when (type) {
            BaseItemKind.MOVIE -> "Movie"
            BaseItemKind.SERIES -> "TV Show"
            BaseItemKind.SEASON -> "Season"
            BaseItemKind.EPISODE -> "Episode"
            BaseItemKind.AUDIO -> "Track"
            BaseItemKind.MUSIC_ALBUM -> "Album"
            BaseItemKind.MUSIC_ARTIST -> "Artist"
            else -> "Library Item"
        }

    private data class PlaybackTarget(
        val id: String,
        val label: String,
    )
}
