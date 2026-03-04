package com.rpeters.cinefintv.ui.screens.stuff

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

sealed class StuffLibraryUiState {
    data object Loading : StuffLibraryUiState()
    data class Error(val message: String) : StuffLibraryUiState()
    data class Content(val items: List<StuffItemCardModel>) : StuffLibraryUiState()
}

data class StuffItemCardModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
)

@HiltViewModel
class StuffLibraryViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StuffLibraryUiState>(StuffLibraryUiState.Loading)
    val uiState: StateFlow<StuffLibraryUiState> = _uiState.asStateFlow()

    fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value is StuffLibraryUiState.Content) return

        viewModelScope.launch {
            _uiState.value = StuffLibraryUiState.Loading
            when (val result = repositories.media.getLibraryItems(collectionType = "homevideos", limit = 180)) {
                is ApiResult.Success -> {
                    val cards = result.data.map {
                        StuffItemCardModel(
                            id = it.id.toString(),
                            title = it.getDisplayTitle(),
                            subtitle = it.getYear()?.toString() ?: it.getFormattedDuration(),
                            imageUrl = repositories.stream.getSeriesImageUrl(it),
                        )
                    }
                    _uiState.value = if (cards.isEmpty()) {
                        StuffLibraryUiState.Error("No home videos were found in Stuff.")
                    } else {
                        StuffLibraryUiState.Content(cards)
                    }
                }
                is ApiResult.Error -> _uiState.value = StuffLibraryUiState.Error(result.message)
                is ApiResult.Loading -> Unit
            }
        }
    }
}
