package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.common.MediaUpdateEvent
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.isMusic
import com.rpeters.cinefintv.utils.toMediaCardPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class PlaylistDetailModel(
    val id: String,
    val title: String,
    val overview: String?,
    val backdropUrl: String?,
    val posterUrl: String?,
)

data class PlaylistItemModel(
    val id: String,
    val playlistItemId: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val itemType: String?,
    val isAudio: Boolean,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
    val unwatchedCount: Int?,
)

sealed class PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState()
    data class Error(val message: String) : PlaylistDetailUiState()
    data class Content(
        val playlist: PlaylistDetailModel,
        val items: List<PlaylistItemModel>,
    ) : PlaylistDetailUiState()
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
) : ViewModel() {

    private var playlistId: String = ""

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    fun init(id: String) {
        if (playlistId == id) return
        playlistId = id
        if (playlistId.isBlank()) {
            _uiState.value = PlaylistDetailUiState.Error("Invalid playlist ID")
        } else {
            load()
            observeUpdateEvents()
        }
    }

    private fun observeUpdateEvents() {
        viewModelScope.launch {
            updateBus.events.collect { event ->
                when (event) {
                    is MediaUpdateEvent.RefreshItem -> if (event.affects(playlistId)) load(silent = true)
                    is MediaUpdateEvent.RefreshAll -> load(silent = true)
                }
            }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value is PlaylistDetailUiState.Content
            if (!silent || !hasContent) {
                _uiState.value = PlaylistDetailUiState.Loading
            }

            val itemResult = repositories.media.getItemDetails(playlistId)
            if (itemResult !is ApiResult.Success) {
                if (itemResult is ApiResult.Error) {
                    _uiState.value = PlaylistDetailUiState.Error(itemResult.message)
                }
                return@launch
            }

            val itemsResult = repositories.playlist.getPlaylistItems(playlistId)
            val items = if (itemsResult is ApiResult.Success) {
                itemsResult.data.map { it.toPlaylistItemModel() }
            } else {
                emptyList()
            }

            _uiState.value = PlaylistDetailUiState.Content(
                playlist = itemResult.data.toPlaylistDetailModel(),
                items = items,
            )
        }
    }

    fun removeItem(entryId: String) {
        viewModelScope.launch {
            if (repositories.playlist.removeItemsFromPlaylist(playlistId, listOf(entryId)) is ApiResult.Success) {
                load(silent = true)
            }
        }
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            if (repositories.playlist.renamePlaylist(playlistId, newName) is ApiResult.Success) {
                load(silent = true)
            }
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (repositories.playlist.deletePlaylist(playlistId) is ApiResult.Success) {
                onDeleted()
            }
        }
    }

    private fun BaseItemDto.toPlaylistDetailModel(): PlaylistDetailModel {
        return PlaylistDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            posterUrl = repositories.stream.getPosterCardImageUrl(this),
        )
    }

    private fun BaseItemDto.toPlaylistItemModel(): PlaylistItemModel {
        val presentation = toMediaCardPresentation()
        return PlaylistItemModel(
            id = id.toString(),
            playlistItemId = playlistItemId ?: id.toString(),
            title = getDisplayTitle(),
            subtitle = presentation.subtitle,
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            itemType = getItemTypeString(),
            isAudio = isMusic(),
            watchStatus = presentation.watchStatus,
            playbackProgress = presentation.playbackProgress,
            unwatchedCount = presentation.unwatchedCount,
        )
    }
}
