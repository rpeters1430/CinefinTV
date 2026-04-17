package com.rpeters.cinefintv.data.paging

import androidx.paging.PagingSource
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryItemPagingSourceTest {

    @Test
    fun load_usesAbsoluteOffsetsSoAppendsDoNotOverlapInitialLoad() = runTest {
        val mediaRepository: JellyfinMediaRepository = mockk(relaxed = true)
        val pagingSource = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            itemTypes = listOf(BaseItemKind.SERIES),
            pageSize = 20,
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = null,
                itemTypes = "Series",
                startIndex = 0,
                limit = 60,
                collectionType = null,
            )
        } returns ApiResult.Success(List(60) { mockk<BaseItemDto>(relaxed = true) })

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = null,
                itemTypes = "Series",
                startIndex = 60,
                limit = 20,
                collectionType = null,
            )
        } returns ApiResult.Success(List(20) { mockk<BaseItemDto>(relaxed = true) })

        val initialResult = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 60,
                placeholdersEnabled = false,
            )
        )
        val initialPage = initialResult as PagingSource.LoadResult.Page
        assertEquals(60, initialPage.data.size)
        assertNull(initialPage.prevKey)
        assertEquals(60, initialPage.nextKey)

        val appendResult = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = initialPage.nextKey!!,
                loadSize = 20,
                placeholdersEnabled = false,
            )
        )
        val appendPage = appendResult as PagingSource.LoadResult.Page
        assertEquals(20, appendPage.data.size)
        assertEquals(40, appendPage.prevKey)
        assertEquals(80, appendPage.nextKey)
    }

    @Test
    fun load_shortFinalPage_hasNoNextKey() = runTest {
        val mediaRepository: JellyfinMediaRepository = mockk(relaxed = true)
        val pagingSource = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            itemTypes = listOf(BaseItemKind.SERIES),
            pageSize = 20,
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = null,
                itemTypes = "Series",
                startIndex = 60,
                limit = 20,
                collectionType = null,
            )
        } returns ApiResult.Success(List(7) { mockk<BaseItemDto>(relaxed = true) })

        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 60,
                loadSize = 20,
                placeholdersEnabled = false,
            )
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals(7, page.data.size)
        assertEquals(40, page.prevKey)
        assertTrue(page.nextKey == null)
    }

    @Test
    fun load_whenRepositoryThrows_returnsLoadError() = runTest {
        val mediaRepository: JellyfinMediaRepository = mockk(relaxed = true)
        val pagingSource = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            itemTypes = listOf(BaseItemKind.SERIES),
            pageSize = 20,
        )

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = null,
                itemTypes = "Series",
                startIndex = 0,
                limit = 60,
                collectionType = null,
            )
        } throws IllegalArgumentException("Invalid API parameters provided")

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 60,
                placeholdersEnabled = false,
            ),
        )

        assertTrue(result is PagingSource.LoadResult.Error)
        val error = result as PagingSource.LoadResult.Error
        assertTrue(error.throwable is IllegalArgumentException)
        assertEquals("Invalid API parameters provided", error.throwable.message)
    }
}
