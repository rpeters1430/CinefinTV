package com.rpeters.cinefintv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.rpeters.cinefintv.data.paging.LibraryItemPagingSource
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.toMediaCardPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

data class LibraryCardModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val itemType: String?,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
)

abstract class BaseLibraryViewModel(
    protected val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
    private val itemTypes: List<BaseItemKind>,
) : ViewModel() {

    private val refreshSignal = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedItems: Flow<PagingData<LibraryCardModel>> = refreshSignal
        .flatMapLatest {
            Pager(
                config = PagingConfig(
                    pageSize = 30,
                    enablePlaceholders = false,
                    initialLoadSize = 60
                ),
                pagingSourceFactory = {
                    LibraryItemPagingSource(
                        mediaRepository = repositories.media,
                        itemTypes = itemTypes
                    )
                }
            ).flow
        }
        .map { pagingData ->
            pagingData.map { toCardModel(it) }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            updateBus.events.collect {
                refreshSignal.value += 1
            }
        }
    }

    private fun toCardModel(item: BaseItemDto): LibraryCardModel {
        val id = item.id.toString()
        val presentation = item.toMediaCardPresentation()

        return LibraryCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = presentation.subtitle,
            imageUrl = repositories.stream.getPosterCardImageUrl(item),
            itemType = item.getItemTypeString(),
            watchStatus = presentation.watchStatus,
            playbackProgress = presentation.playbackProgress,
            unwatchedCount = presentation.unwatchedCount,
        )
    }
}

@HiltViewModel
class MovieLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator,
    updateBus: MediaUpdateBus,
) : BaseLibraryViewModel(repositories, updateBus, listOf(BaseItemKind.MOVIE))

@HiltViewModel
class TvShowLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator,
    updateBus: MediaUpdateBus,
) : BaseLibraryViewModel(repositories, updateBus, listOf(BaseItemKind.SERIES))

@HiltViewModel
class CollectionLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
) : BaseLibraryViewModel(repositories, updateBus, listOf(BaseItemKind.VIDEO, BaseItemKind.COLLECTION_FOLDER)) {
    fun markWatched(itemId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (repositories.user.markAsWatched(itemId) is com.rpeters.cinefintv.data.repository.common.ApiResult.Success) {
                updateBus.refreshItem(itemId)
                onComplete?.invoke()
            }
        }
    }

    fun markUnwatched(itemId: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (repositories.user.markAsUnwatched(itemId) is com.rpeters.cinefintv.data.repository.common.ApiResult.Success) {
                updateBus.refreshItem(itemId)
                onComplete?.invoke()
            }
        }
    }

    fun deleteItem(itemId: String, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = repositories.user.deleteItemAsAdmin(itemId)
            if (result is com.rpeters.cinefintv.data.repository.common.ApiResult.Success) {
                updateBus.refreshAll()
                onDeleted?.invoke()
            }
        }
    }
}
