package com.rpeters.cinefintv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.screens.home.HomeCardModel
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val results: List<HomeCardModel> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        observeQuery()
    }

    fun updateQuery(value: String) {
        _uiState.update {
            it.copy(
                query = value,
                errorMessage = null,
            )
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            _uiState
                .debounce(350)
                .distinctUntilChanged { old, new -> old.query == new.query }
                .collect { state ->
                    runSearch(state.query)
                }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    results = emptyList(),
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        when (val result = repositories.search.searchItems(query, limit = 60)) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = result.data.mapNotNull(::toCardModel),
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.message,
                        results = emptyList(),
                    )
                }
            }
            is ApiResult.Loading -> Unit
        }
    }

    private fun toCardModel(item: BaseItemDto): HomeCardModel? {
        val id = item.id.toString()
        val isResumable = item.canResume()
        val isWatched = item.isWatched()
        val watchStatus = when {
            isWatched -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) {
            item.getWatchedPercentage().toFloat() / 100f
        } else null

        val subtitle = item.getYear()?.toString()
            ?: item.getFormattedDuration()
            ?: item.type.toString().replace('_', ' ')

        return HomeCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = subtitle,
            imageUrl = repositories.stream.getSearchCardImageUrl(item),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
        )
    }
}
