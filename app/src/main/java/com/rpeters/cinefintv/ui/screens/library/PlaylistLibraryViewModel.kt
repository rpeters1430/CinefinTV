package com.rpeters.cinefintv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PlaylistLibraryUiState {
    data object Loading : PlaylistLibraryUiState()
    data class Error(val message: String) : PlaylistLibraryUiState()
    data class Content(val items: List<LibraryCardModel>) : PlaylistLibraryUiState()
}

@HiltViewModel
class PlaylistLibraryViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistLibraryUiState>(PlaylistLibraryUiState.Loading)
    val uiState: StateFlow<PlaylistLibraryUiState> = _uiState.asStateFlow()

    private val _actionErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionErrors: SharedFlow<String> = _actionErrors

    init {
        load()
        viewModelScope.launch {
            updateBus.events.collect { load(silent = true) }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            val hasContent = _uiState.value is PlaylistLibraryUiState.Content
            if (!silent || !hasContent) {
                _uiState.value = PlaylistLibraryUiState.Loading
            }

            when (val result = repositories.playlist.getAllPlaylists()) {
                is ApiResult.Success -> {
                    _uiState.value = PlaylistLibraryUiState.Content(
                        result.data.map { it.toLibraryCardModel(repositories.stream, subtitleOverride = it.childCount?.let { count -> "$count items" }) },
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = PlaylistLibraryUiState.Error(result.message)
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun createPlaylist(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = repositories.playlist.createPlaylist(name)) {
                is ApiResult.Success -> onCreated(result.data)
                is ApiResult.Error -> _actionErrors.tryEmit("Couldn't create playlist: ${result.message}")
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun deletePlaylist(playlistId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (val result = repositories.playlist.deletePlaylist(playlistId)) {
                is ApiResult.Success -> onDeleted()
                is ApiResult.Error -> _actionErrors.tryEmit("Couldn't delete playlist: ${result.message}")
                is ApiResult.Loading -> Unit
            }
        }
    }
}
