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
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getSeriesCardDetailLine
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCount
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val repositories: JellyfinRepositoryCoordinator,
    private val itemTypes: List<BaseItemKind>,
) : ViewModel() {

    val pagedItems: Flow<PagingData<LibraryCardModel>> = Pager(
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
        .map { pagingData ->
            pagingData.map { toCardModel(it) }
        }
        .cachedIn(viewModelScope)

    private fun toCardModel(item: BaseItemDto): LibraryCardModel {
        val id = item.id.toString()
        val watchedPercentage = item.getWatchedPercentage()
        val isResumable = item.canResume()
        val isWatched = item.isWatched()
        
        val watchStatus = when {
            isWatched -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) watchedPercentage.toFloat() / 100f else null
        val unwatchedCount = if (item.isSeries()) item.getUnwatchedEpisodeCount().takeIf { it > 0 } else null

        val subtitle = when {
            item.isSeries() -> item.getSeriesCardDetailLine()
                ?: item.getYear()?.toString()
            item.getYear() != null -> item.getYear().toString()
            item.getFormattedDuration() != null -> item.getFormattedDuration()
            else -> null
        }

        return LibraryCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = subtitle,
            imageUrl = repositories.stream.getPosterCardImageUrl(item),
            itemType = item.type.toString(),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
            unwatchedCount = unwatchedCount,
        )
    }
}

@HiltViewModel
class MovieLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator
) : BaseLibraryViewModel(repositories, listOf(BaseItemKind.MOVIE))

@HiltViewModel
class TvShowLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator
) : BaseLibraryViewModel(repositories, listOf(BaseItemKind.SERIES))

@HiltViewModel
class StuffLibraryViewModel @Inject constructor(
    repositories: JellyfinRepositoryCoordinator
) : BaseLibraryViewModel(repositories, listOf(BaseItemKind.VIDEO, BaseItemKind.COLLECTION_FOLDER))
