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
import com.rpeters.cinefintv.data.repository.JellyfinStreamRepository
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.toMediaCardPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
    val backdropUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
)

/**
 * Shared BaseItemDto -> LibraryCardModel mapping used by every library grid ViewModel.
 * Pass [subtitleOverride] when a screen needs subtitle semantics other than
 * [toMediaCardPresentation]'s playback/detail line (e.g. an item count for playlists).
 */
fun BaseItemDto.toLibraryCardModel(
    streamRepository: JellyfinStreamRepository,
    subtitleOverride: String? = null,
): LibraryCardModel {
    val presentation = toMediaCardPresentation()

    return LibraryCardModel(
        id = id.toString(),
        title = getDisplayTitle(),
        subtitle = subtitleOverride ?: presentation.subtitle,
        imageUrl = streamRepository.getPosterCardImageUrl(this),
        backdropUrl = streamRepository.getBackdropUrl(this),
        description = overview?.take(200),
        year = getYear(),
        rating = communityRating?.let { String.format(java.util.Locale.US, "%.1f", it) },
        itemType = getItemTypeString(),
        watchStatus = presentation.watchStatus,
        playbackProgress = presentation.playbackProgress,
        unwatchedCount = presentation.unwatchedCount,
    )
}

abstract class BaseLibraryViewModel(
    protected val repositories: JellyfinRepositoryCoordinator,
    private val updateBus: MediaUpdateBus,
    private val itemTypes: List<BaseItemKind>,
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 30
        const val INITIAL_LOAD_SIZE = 60
        const val REFRESH_DEBOUNCE_MS = 300L
    }

    private val refreshSignal = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val pagedItems: Flow<PagingData<LibraryCardModel>> = refreshSignal
        .debounce { signal -> if (signal == 0) 0L else REFRESH_DEBOUNCE_MS }
        .flatMapLatest {
            Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    enablePlaceholders = false,
                    initialLoadSize = INITIAL_LOAD_SIZE
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
                refreshSignal.update { it + 1 }
            }
        }
    }

    fun refresh() {
        refreshSignal.update { it + 1 }
    }

    private fun toCardModel(item: BaseItemDto): LibraryCardModel =
        item.toLibraryCardModel(repositories.stream)
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
