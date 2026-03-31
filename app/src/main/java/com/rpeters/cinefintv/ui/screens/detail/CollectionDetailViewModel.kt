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
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.getMediaQualityLabel
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import com.rpeters.cinefintv.utils.toMediaCardPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class CollectionDetailModel(
    val id: String,
    val title: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
    val isCollection: Boolean,
    val type: String?,
    val year: Int?,
    val runtime: String?,
    val mediaQuality: String?,
    val addedDate: String?,
    val itemCount: Int?,
    val isWatched: Boolean,
    val playbackProgress: Float?,
)

data class CollectionItemModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val itemType: String?,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
    val unwatchedCount: Int?,
)

sealed class CollectionDetailUiState {
    data object Loading : CollectionDetailUiState()
    data class Error(val message: String) : CollectionDetailUiState()
    data class Content(
        val stuff: CollectionDetailModel,
        val items: List<CollectionItemModel>,
    ) : CollectionDetailUiState()
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<CollectionDetailUiState>(CollectionDetailUiState.Loading)
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    init {
        if (itemId.isBlank()) {
            _uiState.value = CollectionDetailUiState.Error("Invalid item ID")
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
                        if (event.itemId == itemId) {
                            load()
                        }
                    }
                    is com.rpeters.cinefintv.data.common.MediaUpdateEvent.RefreshAll -> load()
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CollectionDetailUiState.Loading

            val itemResult = repositories.media.getItemDetails(itemId)

            if (itemResult is ApiResult.Success) {
                val itemDto = itemResult.data
                val isCollection = itemDto.type == BaseItemKind.BOX_SET || 
                                 itemDto.type == BaseItemKind.COLLECTION_FOLDER ||
                                 itemDto.type == BaseItemKind.USER_VIEW
                
                val items = if (isCollection) {
                    val contentResult = repositories.media.getLibraryItems(parentId = itemId)
                    if (contentResult is ApiResult.Success) {
                        contentResult.data.map { it.toCollectionItemModel() }
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                _uiState.value = CollectionDetailUiState.Content(
                    stuff = itemDto.toCollectionDetailModel(isCollection),
                    items = items
                )
            } else if (itemResult is ApiResult.Error) {
                _uiState.value = CollectionDetailUiState.Error(itemResult.message)
            }
        }
    }

    fun markWatched() {
        viewModelScope.launch {
            if (repositories.user.markAsWatched(itemId) is ApiResult.Success) {
                updateBus.refreshItem(itemId)
                load()
            }
        }
    }

    fun markUnwatched() {
        viewModelScope.launch {
            if (repositories.user.markAsUnwatched(itemId) is ApiResult.Success) {
                updateBus.refreshItem(itemId)
                load()
            }
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (repositories.user.deleteItemAsAdmin(itemId) is ApiResult.Success) {
                updateBus.refreshAll()
                onDeleted()
            }
        }
    }

    private fun BaseItemDto.toCollectionDetailModel(isCollection: Boolean): CollectionDetailModel {
        return CollectionDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
            isCollection = isCollection,
            type = getItemTypeString(),
            year = getYear(),
            runtime = getFormattedDuration(),
            mediaQuality = getMediaQualityLabel(),
            addedDate = dateCreated?.toString()?.substringBefore("T"),
            itemCount = childCount,
            isWatched = isWatched(),
            playbackProgress = if (canResume()) (getWatchedPercentage() / 100f).toFloat() else null,
        )
    }

    private fun BaseItemDto.toCollectionItemModel(): CollectionItemModel {
        val presentation = toMediaCardPresentation()

        return CollectionItemModel(
            id = id.toString(),
            title = getDisplayTitle(),
            subtitle = presentation.subtitle,
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            itemType = getItemTypeString(),
            watchStatus = presentation.watchStatus,
            playbackProgress = presentation.playbackProgress,
            unwatchedCount = presentation.unwatchedCount,
        )
    }
}
