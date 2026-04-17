package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
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
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinMediaRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: JellyfinAuthRepository
    private lateinit var sessionManager: JellyfinSessionManager
    private lateinit var cache: JellyfinCache
    private lateinit var healthChecker: LibraryHealthChecker
    private lateinit var repository: JellyfinMediaRepository

    @Before
    fun setUp() {
        authRepository = JellyfinAuthRepository(
            jellyfin = mockk<Jellyfin>(relaxed = true),
            secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true),
        )
        sessionManager = mockk()
        cache = mockk(relaxed = true)
        healthChecker = mockk(relaxed = true)
        repository = JellyfinMediaRepository(
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = cache,
            healthChecker = healthChecker,
            updateBus = MediaUpdateBus(),
        )
    }

    @Test
    fun getUserLibraries_whenSuccess_returnsLibraryList() = runTest {
        val mockItems = listOf(mockk<BaseItemDto> { every { name } returns "Movies" })
        val mockServer = JellyfinServer(
            id = "server-id",
            name = "Test Server",
            url = "http://localhost",
            userId = UUID.randomUUID().toString(),
            accessToken = "token",
            isConnected = true
        )

        authRepository.seedCurrentServer(mockServer)
        coEvery { cache.getCachedLibraries() } returns null
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } returns mockItems

        val result = repository.getUserLibraries()

        assertTrue(result is ApiResult.Success)
        assertEquals(mockItems, (result as ApiResult.Success).data)
    }

    @Test
    fun getUserLibraries_whenError_returnsApiError() = runTest {
        val mockServer = JellyfinServer(
            id = "server-id",
            name = "Test Server",
            url = "http://localhost",
            userId = UUID.randomUUID().toString(),
            accessToken = "token",
            isConnected = true
        )

        authRepository.seedCurrentServer(mockServer)
        coEvery { cache.getCachedLibraries() } returns null
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } throws RuntimeException("Network error")

        val result = repository.getUserLibraries()

        assertTrue(result is ApiResult.Error)
        assertEquals("Network error", (result as ApiResult.Error).message)
    }
}
