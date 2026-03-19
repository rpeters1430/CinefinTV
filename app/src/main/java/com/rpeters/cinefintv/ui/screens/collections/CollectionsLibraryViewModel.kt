package com.rpeters.cinefintv.ui.screens.collections

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import com.rpeters.cinefintv.data.repository.common.ApiResult
import javax.inject.Inject

sealed class CollectionsLibraryUiState {
    data object Loading : CollectionsLibraryUiState()
    data class Content(
        val recentlyAdded: List<CollectionsItemCardModel> = emptyList(),
    ) : CollectionsLibraryUiState()
}

data class CollectionsItemCardModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsLibraryViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CollectionsLibraryUiState>(CollectionsLibraryUiState.Loading)
    val uiState: StateFlow<CollectionsLibraryUiState> = _uiState.asStateFlow()

    private val refreshGeneration = MutableStateFlow(0)
    private val featuredItemIds = MutableStateFlow<Set<String>>(emptySet())

    val pagedItems: Flow<PagingData<CollectionsItemCardModel>> =
        combine(refreshGeneration, featuredItemIds) { _, excludedIds -> excludedIds }
            .flatMapLatest { excludedIds ->
                Pager(
                    config = PagingConfig(
                        pageSize = COLLECTIONS_PAGE_SIZE,
                        initialLoadSize = COLLECTIONS_PAGE_SIZE * 2,
                        prefetchDistance = COLLECTIONS_PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                ) {
                    LibraryItemPagingSource(
                        mediaRepository = repositories.media,
                        collectionType = "homevideos",
                        excludedItemIds = excludedIds,
                        pageSize = COLLECTIONS_PAGE_SIZE,
                    )
                }.flow
            }
            .map { pagingData -> pagingData.map(::toCardModel) }
            .cachedIn(viewModelScope)

    fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value is CollectionsLibraryUiState.Content) {
            return
        }

        featuredItemIds.value = emptySet()
        _uiState.value = CollectionsLibraryUiState.Content(recentlyAdded = emptyList())
        loadFeaturedRow()
        if (forceRefresh) {
            refreshGeneration.value += 1
        }
    }

    private fun loadFeaturedRow() {
        viewModelScope.launch {
            val recent = repositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 8)
            val cards = when (recent) {
                is ApiResult.Success -> recent.data
                    .distinctBy { it.id.toString() }
                    .take(2)
                    .map(::toCardModel)
                else -> emptyList()
            }
            featuredItemIds.value = cards.map { it.id }.toSet()
            val currentState = _uiState.value
            if (currentState is CollectionsLibraryUiState.Content) {
                _uiState.value = currentState.copy(recentlyAdded = cards)
            }
        }
    }

    private fun toCardModel(item: BaseItemDto): CollectionsItemCardModel {
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

        return CollectionsItemCardModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = item.getYear()?.toString() ?: item.getFormattedDuration(),
            imageUrl = repositories.stream.getWideCardImageUrl(item)
                ?: repositories.stream.getLandscapeImageUrl(item)
                ?: repositories.stream.getSeriesImageUrl(item),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
        )
    }

    companion object {
        private const val COLLECTIONS_PAGE_SIZE = 40
    }
}
