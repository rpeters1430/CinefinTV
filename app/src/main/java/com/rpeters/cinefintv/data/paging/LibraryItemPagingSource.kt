package com.rpeters.cinefintv.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * PagingSource for library items that provides paginated loading of media content.
 * This helps prevent memory issues when loading large libraries.
 */
class LibraryItemPagingSource(
    private val mediaRepository: JellyfinMediaRepository,
    private val parentId: String? = null,
    private val itemTypes: List<BaseItemKind>? = null,
    private val collectionType: String? = null,
    private val excludedItemIds: Set<String> = emptySet(),
    private val pageSize: Int = 20,
) : PagingSource<Int, BaseItemDto>() {

    companion object {
        private const val TAG = "LibraryItemPagingSource"
        private const val STARTING_PAGE_INDEX = 0
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BaseItemDto> {
        return try {
            val pageIndex = params.key ?: STARTING_PAGE_INDEX
            val startIndex = pageIndex * pageSize

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Loading page $pageIndex, startIndex=$startIndex, loadSize=${params.loadSize}")
            }

            val itemTypesString = itemTypes?.joinToString(",") { type ->
                when (type) {
                    BaseItemKind.MOVIE -> "Movie"
                    BaseItemKind.SERIES -> "Series"
                    BaseItemKind.EPISODE -> "Episode"
                    BaseItemKind.AUDIO -> "Audio"
                    BaseItemKind.MUSIC_ALBUM -> "MusicAlbum"
                    BaseItemKind.MUSIC_ARTIST -> "MusicArtist"
                    BaseItemKind.BOOK -> "Book"
                    BaseItemKind.AUDIO_BOOK -> "AudioBook"
                    BaseItemKind.VIDEO -> "Video"
                    else -> type.name
                }
            }

            when (
                val result = mediaRepository.getLibraryItems(
                    parentId = parentId,
                    itemTypes = itemTypesString,
                    startIndex = startIndex,
                    limit = params.loadSize,
                    collectionType = collectionType,
                )
            ) {
                is ApiResult.Success -> {
                    val rawItems = result.data
                    val items = if (excludedItemIds.isEmpty()) {
                        rawItems
                    } else {
                        rawItems.filterNot { item -> excludedItemIds.contains(item.id.toString()) }
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "Loaded page $pageIndex: raw=${rawItems.size}, filtered=${items.size}, excluded=${excludedItemIds.size}",
                        )
                    }

                    LoadResult.Page(
                        data = items,
                        prevKey = if (pageIndex == STARTING_PAGE_INDEX) null else pageIndex - 1,
                        nextKey = if (rawItems.isEmpty() || rawItems.size < params.loadSize) null else pageIndex + 1,
                    )
                }
                is ApiResult.Error -> {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Failed to load page $pageIndex: ${result.message}")
                    }
                    LoadResult.Error(Exception(result.message))
                }
                is ApiResult.Loading -> {
                    // This shouldn't happen in our paging context
                    LoadResult.Error(Exception("Unexpected loading state"))
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        }
    }

    override fun getRefreshKey(state: PagingState<Int, BaseItemDto>): Int? {
        // Try to find the page key of the closest page to anchorPosition, from
        // either the prevKey or the nextKey, but you need to handle nullability
        // here:
        //  * prevKey == null -> anchorPage is the first page.
        //  * nextKey == null -> anchorPage is the last page.
        //  * both prevKey and nextKey null -> anchorPage is the initial page, so
        //    just return null.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
