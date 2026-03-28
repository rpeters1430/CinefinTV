package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.LibraryHealthChecker
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinMediaRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: JellyfinAuthRepository = mockk(relaxed = true)
    private val sessionManager: JellyfinSessionManager = mockk()
    private val cache: JellyfinCache = mockk(relaxed = true)
    private val healthChecker: LibraryHealthChecker = mockk(relaxed = true)
    private val apiClient: ApiClient = mockk()

    private val repository = JellyfinMediaRepository(
        authRepository = authRepository,
        sessionManager = sessionManager,
        cache = cache,
        healthChecker = healthChecker,
        updateBus = MediaUpdateBus(),
    )

    @Test
    fun getUserLibraries_whenSuccess_returnsLibraryList() = runTest {
        val mockItems = listOf(mockk<BaseItemDto> { every { name } returns "Movies" })
        
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } returns mockItems
        coEvery { authRepository.isTokenExpired() } returns false

        val result = repository.getUserLibraries()

        assertTrue(result is ApiResult.Success)
        assertEquals(mockItems, (result as ApiResult.Success).data)
    }

    @Test
    fun getUserLibraries_whenError_returnsApiError() = runTest {
        coEvery { authRepository.isTokenExpired() } returns false
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } throws RuntimeException("Network error")

        val result = repository.getUserLibraries()

        assertTrue(result is ApiResult.Error)
        assertEquals("Network error", (result as ApiResult.Error).message)
    }
}
