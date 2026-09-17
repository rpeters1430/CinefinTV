package com.rpeters.cinefintv.data.paging

import androidx.paging.PagingSource
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
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

    @Test
    fun load_excludedCollectionTypesAreFilteredOutOfThePage() = runTest {
        val mediaRepository: JellyfinMediaRepository = mockk(relaxed = true)
        val pagingSource = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            itemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
            excludedCollectionTypes = setOf("movies", "tvshows"),
            pageSize = 20,
        )

        val moviesLibrary = mockk<BaseItemDto>(relaxed = true)
        every { moviesLibrary.collectionType } returns CollectionType.MOVIES
        val tvShowsLibrary = mockk<BaseItemDto>(relaxed = true)
        every { tvShowsLibrary.collectionType } returns CollectionType.TVSHOWS
        val homeVideosLibrary = mockk<BaseItemDto>(relaxed = true)
        every { homeVideosLibrary.collectionType } returns CollectionType.HOMEVIDEOS

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = null,
                itemTypes = "CollectionFolder",
                startIndex = 0,
                limit = 60,
                collectionType = null,
            )
        } returns ApiResult.Success(listOf(moviesLibrary, tvShowsLibrary, homeVideosLibrary))

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 60,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(homeVideosLibrary), page.data)
    }

    @Test
    fun load_pageThatBecomesEmptyAfterFilteringStillDerivesNextKeyFromRawPageSize() = runTest {
        val mediaRepository: JellyfinMediaRepository = mockk(relaxed = true)
        val pagingSource = LibraryItemPagingSource(
            mediaRepository = mediaRepository,
            itemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
            excludedCollectionTypes = setOf("movies"),
            pageSize = 20,
        )

        val moviesLibrary = mockk<BaseItemDto>(relaxed = true)
        every { moviesLibrary.collectionType } returns CollectionType.MOVIES

        coEvery {
            mediaRepository.getLibraryItems(
                parentId = null,
                itemTypes = "CollectionFolder",
                startIndex = 0,
                limit = 60,
                collectionType = null,
            )
        } returns ApiResult.Success(List(60) { moviesLibrary })

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 60,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        // Every item on this page was filtered out, but the raw page was full-sized, so
        // pagination must still advance instead of terminating early.
        assertTrue(page.data.isEmpty())
        assertEquals(60, page.nextKey)
    }
}
