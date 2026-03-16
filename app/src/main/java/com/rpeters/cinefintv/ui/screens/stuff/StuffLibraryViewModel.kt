package com.rpeters.cinefintv.ui.screens.stuff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.rpeters.cinefintv.data.paging.LibraryItemPagingSource
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

sealed class StuffLibraryUiState {
    data object Loading : StuffLibraryUiState()
    data object Content : StuffLibraryUiState()
}

data class StuffItemCardModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class StuffLibraryViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StuffLibraryUiState>(StuffLibraryUiState.Loading)
    val uiState: StateFlow<StuffLibraryUiState> = _uiState.asStateFlow()

    private val refreshGeneration = MutableStateFlow(0)

    val pagedItems: Flow<PagingData<StuffItemCardModel>> =
        refreshGeneration
            .flatMapLatest {
                Pager(
                    config = PagingConfig(
                        pageSize = STUFF_PAGE_SIZE,
                        initialLoadSize = STUFF_PAGE_SIZE * 2,
                        prefetchDistance = STUFF_PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                ) {
                    LibraryItemPagingSource(
                        mediaRepository = repositories.media,
                        collectionType = "homevideos",
                        pageSize = STUFF_PAGE_SIZE,
                    )
                }.flow
            }
            .map { pagingData -> pagingData.map(::toCardModel) }
            .cachedIn(viewModelScope)

    fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value is StuffLibraryUiState.Content) return

        _uiState.value = StuffLibraryUiState.Content
        if (forceRefresh) {
            refreshGeneration.value += 1
        }
    }

    private fun toCardModel(item: BaseItemDto): StuffItemCardModel {
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

        return StuffItemCardModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = item.getYear()?.toString() ?: item.getFormattedDuration(),
            imageUrl = repositories.stream.getSeriesImageUrl(item),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
        )
    }

    companion object {
        private const val STUFF_PAGE_SIZE = 40
    }
}
