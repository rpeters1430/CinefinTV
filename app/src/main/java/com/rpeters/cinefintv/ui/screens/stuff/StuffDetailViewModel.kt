package com.rpeters.cinefintv.ui.screens.stuff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getYear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StuffDetailUiState {
    data object Loading : StuffDetailUiState()
    data class Error(val message: String) : StuffDetailUiState()
    data class Content(
        val item: StuffDetailModel,
        val moreFromStuff: List<StuffItemCardModel>,
    ) : StuffDetailUiState()
}

data class StuffDetailModel(
    val id: String,
    val title: String,
    val overview: String?,
    val metadataLine: String?,
    val imageUrl: String?,
    val backdropUrl: String?,
)

@HiltViewModel
class StuffDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val itemId = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<StuffDetailUiState>(StuffDetailUiState.Loading)
    val uiState: StateFlow<StuffDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (itemId.isBlank()) {
            _uiState.value = StuffDetailUiState.Error("No Stuff item ID was provided.")
            return
        }

        viewModelScope.launch {
            _uiState.value = StuffDetailUiState.Loading

            val detailsResult = repositories.media.getItemDetails(itemId)
            val libraryResult = repositories.media.getLibraryItems(collectionType = "homevideos", limit = 40)

            if (detailsResult !is ApiResult.Success) {
                val message = (detailsResult as? ApiResult.Error)?.message ?: "Unable to load Stuff details."
                _uiState.value = StuffDetailUiState.Error(message)
                return@launch
            }

            val item = detailsResult.data
            val metadata = listOfNotNull(item.getYear()?.toString(), item.getFormattedDuration())
                .joinToString(" • ")
                .ifBlank { null }

            val moreItems = if (libraryResult is ApiResult.Success) {
                libraryResult.data
                    .asSequence()
                    .filter { it.id.toString() != itemId }
                    .take(16)
                    .map {
                        StuffItemCardModel(
                            id = it.id.toString(),
                            title = it.getDisplayTitle(),
                            subtitle = it.getYear()?.toString() ?: it.getFormattedDuration(),
                            imageUrl = repositories.stream.getSeriesImageUrl(it),
                        )
                    }
                    .toList()
            } else {
                emptyList()
            }

            _uiState.value = StuffDetailUiState.Content(
                item = StuffDetailModel(
                    id = itemId,
                    title = item.getDisplayTitle(),
                    overview = item.overview,
                    metadataLine = metadata,
                    imageUrl = repositories.stream.getSeriesImageUrl(item),
                    backdropUrl = repositories.stream.getBackdropUrl(item),
                ),
                moreFromStuff = moreItems,
            )
        }
    }
}
