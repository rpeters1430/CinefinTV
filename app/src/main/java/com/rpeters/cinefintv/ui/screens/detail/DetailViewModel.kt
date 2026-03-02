package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class DetailHeroModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val genres: List<String>,
    val imageUrl: String?,
    val backdropUrl: String?,
)

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Error(val message: String) : DetailUiState()
    data class Content(
        val item: DetailHeroModel,
        val related: List<DetailHeroModel>,
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
                    val relatedItems = loadRelated(item)
                    _uiState.value = DetailUiState.Content(
                        item = toHeroModel(item),
                        related = relatedItems.map(this@DetailViewModel::toHeroModel),
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = DetailUiState.Error(detailResult.message)
                }
                is ApiResult.Loading -> Unit
            }
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

    private fun toHeroModel(item: BaseItemDto): DetailHeroModel {
        val subtitleParts = listOfNotNull(
            item.getYear()?.toString(),
            item.getFormattedDuration(),
            item.officialRating,
        )

        return DetailHeroModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = subtitleParts.joinToString(" - ").ifBlank { null },
            overview = item.overview?.takeIf { it.isNotBlank() },
            genres = item.genres.orEmpty().take(4),
            imageUrl = repositories.stream.getSeriesImageUrl(item),
            backdropUrl = repositories.stream.getBackdropUrl(item),
        )
    }
}
