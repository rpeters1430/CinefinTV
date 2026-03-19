package com.rpeters.cinefintv.ui.screens.library

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
import com.rpeters.cinefintv.ui.screens.home.HomeCardModel
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCount
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import com.rpeters.cinefintv.data.repository.common.ApiResult
import javax.inject.Inject

sealed class LibraryUiState {
    data object Loading : LibraryUiState()
    data class Content(
        val title: String,
        val recentlyAdded: List<HomeCardModel> = emptyList(),
    ) : LibraryUiState()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val selectedCategory = MutableStateFlow<LibraryCategory?>(null)
    private val refreshGeneration = MutableStateFlow(0)
    private val featuredItemIds = MutableStateFlow<Set<String>>(emptySet())

    val pagedItems: Flow<PagingData<HomeCardModel>> =
        combine(
            selectedCategory.filterNotNull(),
            refreshGeneration,
            featuredItemIds,
        ) { category, _, excludedIds -> category to excludedIds }
            .flatMapLatest { (category, excludedIds) ->
                Pager(
                    config = PagingConfig(
                        pageSize = LIBRARY_PAGE_SIZE,
                        initialLoadSize = LIBRARY_PAGE_SIZE * 2,
                        prefetchDistance = LIBRARY_PAGE_SIZE,
                        enablePlaceholders = false,
                    ),
                ) {
                    LibraryItemPagingSource(
                        mediaRepository = repositories.media,
                        itemTypes = category.itemTypes,
                        collectionType = category.collectionType,
                        excludedItemIds = excludedIds,
                        pageSize = LIBRARY_PAGE_SIZE,
                    )
                }.flow
            }
            .map { pagingData -> pagingData.map(::toCardModel) }
            .cachedIn(viewModelScope)

    fun load(category: LibraryCategory, forceRefresh: Boolean = false) {
        if (!forceRefresh && selectedCategory.value == category && _uiState.value is LibraryUiState.Content) {
            return
        }

        selectedCategory.value = category
        featuredItemIds.value = emptySet()
        _uiState.value = LibraryUiState.Content(title = category.title, recentlyAdded = emptyList())
        loadFeaturedRow(category)

        if (forceRefresh) {
            refreshGeneration.value += 1
        }
    }

    private fun loadFeaturedRow(category: LibraryCategory) {
        viewModelScope.launch {
            val featuredItems = when (category) {
                LibraryCategory.MOVIES -> repositories.media.getRecentlyAddedByType(BaseItemKind.MOVIE, limit = 8)
                LibraryCategory.TV_SHOWS -> repositories.media.getRecentlyAddedByType(BaseItemKind.EPISODE, limit = 8)
                LibraryCategory.COLLECTIONS -> repositories.media.getRecentlyAddedByType(BaseItemKind.VIDEO, limit = 8)
            }
            val cards = when (featuredItems) {
                is ApiResult.Success -> featuredItems.data
                    .distinctBy { it.id.toString() }
                    .take(2)
                    .map(::toCardModel)
                else -> emptyList()
            }
            featuredItemIds.value = cards.map { it.id }.toSet()
            val currentState = _uiState.value
            if (currentState is LibraryUiState.Content) {
                _uiState.value = currentState.copy(recentlyAdded = cards)
            }
        }
    }

    private fun toCardModel(item: BaseItemDto): HomeCardModel {
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
        val unwatchedCount = if (item.isSeries()) {
            item.getUnwatchedEpisodeCount().takeIf { it > 0 }
        } else null

        return HomeCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = item.getYear()?.toString() ?: item.getFormattedDuration(),
            imageUrl = repositories.stream.getWideCardImageUrl(item),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
            unwatchedCount = unwatchedCount,
        )
    }

    companion object {
        private const val LIBRARY_PAGE_SIZE = 40
    }
}
