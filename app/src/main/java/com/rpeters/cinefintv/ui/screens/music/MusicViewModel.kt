package com.rpeters.cinefintv.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

enum class MusicViewType {
    ALBUMS, ARTISTS;
    companion object { val DEFAULT = ALBUMS }
}

sealed class MusicUiState {
    data object Loading : MusicUiState()
    data class Grid(val items: List<BaseItemDto>, val viewType: MusicViewType) : MusicUiState()
    data class AlbumDetail(val album: BaseItemDto, val tracks: List<BaseItemDto>) : MusicUiState()
    data class Error(val message: String) : MusicUiState()
}

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var currentViewType = MusicViewType.DEFAULT

    init { loadGrid(MusicViewType.DEFAULT) }

    fun loadGrid(viewType: MusicViewType) {
        currentViewType = viewType
        viewModelScope.launch {
            _uiState.value = MusicUiState.Loading
            val result = when (viewType) {
                MusicViewType.ALBUMS -> repositories.media.getRecentlyAddedByType(BaseItemKind.MUSIC_ALBUM, limit = 50)
                MusicViewType.ARTISTS -> repositories.media.getRecentlyAddedByType(BaseItemKind.MUSIC_ARTIST, limit = 50)
            }
            _uiState.value = when (result) {
                is ApiResult.Success -> MusicUiState.Grid(result.data, viewType)
                is ApiResult.Error -> MusicUiState.Error(result.message)
                is ApiResult.Loading -> MusicUiState.Loading
            }
        }
    }

    fun openAlbum(album: BaseItemDto) {
        viewModelScope.launch {
            _uiState.value = MusicUiState.Loading
            val result = repositories.media.getAlbumTracks(album.id.toString())
            _uiState.value = when (result) {
                is ApiResult.Success -> MusicUiState.AlbumDetail(album, result.data)
                is ApiResult.Error -> MusicUiState.Error(result.message)
                is ApiResult.Loading -> MusicUiState.Loading
            }
        }
    }

    fun openArtist(artist: BaseItemDto) {
        viewModelScope.launch {
            _uiState.value = MusicUiState.Loading
            val result = repositories.media.getAlbumsForArtist(artist.id.toString())
            _uiState.value = when (result) {
                is ApiResult.Success -> MusicUiState.Grid(result.data, MusicViewType.ALBUMS)
                is ApiResult.Error -> MusicUiState.Error(result.message)
                is ApiResult.Loading -> MusicUiState.Loading
            }
        }
    }

    fun backToGrid() {
        loadGrid(currentViewType)
    }

    fun imageUrl(item: BaseItemDto): String? = repositories.stream.getSeriesImageUrl(item)
}
