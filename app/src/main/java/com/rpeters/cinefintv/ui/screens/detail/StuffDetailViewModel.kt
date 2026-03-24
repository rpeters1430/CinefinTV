package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class StuffDetailModel(
    val id: String,
    val title: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val isCollection: Boolean,
    val type: String?,
    val playbackProgress: Float?,
)

data class StuffItemModel(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val itemType: String?,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
)

sealed class StuffDetailUiState {
    data object Loading : StuffDetailUiState()
    data class Error(val message: String) : StuffDetailUiState()
    data class Content(
        val stuff: StuffDetailModel,
        val items: List<StuffItemModel>,
    ) : StuffDetailUiState()
}

@HiltViewModel
class StuffDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<StuffDetailUiState>(StuffDetailUiState.Loading)
    val uiState: StateFlow<StuffDetailUiState> = _uiState.asStateFlow()

    init {
        if (itemId.isBlank()) {
            _uiState.value = StuffDetailUiState.Error("Invalid item ID")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = StuffDetailUiState.Loading

            val itemResult = repositories.media.getItemDetails(itemId)

            if (itemResult is ApiResult.Success) {
                val itemDto = itemResult.data
                val isCollection = itemDto.type == BaseItemKind.BOX_SET || 
                                 itemDto.type == BaseItemKind.COLLECTION_FOLDER ||
                                 itemDto.type == BaseItemKind.USER_VIEW
                
                val items = if (isCollection) {
                    val contentResult = repositories.media.getLibraryItems(parentId = itemId)
                    if (contentResult is ApiResult.Success) {
                        contentResult.data.map { it.toStuffItemModel() }
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                _uiState.value = StuffDetailUiState.Content(
                    stuff = itemDto.toStuffDetailModel(isCollection),
                    items = items
                )
            } else if (itemResult is ApiResult.Error) {
                _uiState.value = StuffDetailUiState.Error(itemResult.message)
            }
        }
    }

    private fun BaseItemDto.toStuffDetailModel(isCollection: Boolean): StuffDetailModel {
        return StuffDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            isCollection = isCollection,
            type = getItemTypeString(),
            playbackProgress = if (canResume()) (getWatchedPercentage() / 100f).toFloat() else null,
        )
    }

    private fun BaseItemDto.toStuffItemModel(): StuffItemModel {
        val watchedPercentage = getWatchedPercentage()
        val watchStatus = when {
            isWatched() -> WatchStatus.WATCHED
            canResume() -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (canResume()) watchedPercentage.toFloat() / 100f else null

        return StuffItemModel(
            id = id.toString(),
            title = getDisplayTitle(),
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            itemType = getItemTypeString(),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress
        )
    }
}
