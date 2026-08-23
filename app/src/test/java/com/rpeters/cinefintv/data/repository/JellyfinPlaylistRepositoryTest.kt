package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.testutil.DeterministicDispatcherProvider
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
class JellyfinPlaylistRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers by lazy { DeterministicDispatcherProvider(mainDispatcherRule.dispatcher) }
    private lateinit var authRepository: JellyfinAuthRepository
    private lateinit var sessionManager: JellyfinSessionManager
    private lateinit var cache: JellyfinCache
    private lateinit var repository: JellyfinPlaylistRepository

    private val mockServer = JellyfinServer(
        id = "server-id",
        name = "Test Server",
        url = "http://localhost",
        userId = UUID.randomUUID().toString(),
        accessToken = "token",
        isConnected = true,
    )

    @Before
    fun setUp() {
        authRepository = JellyfinAuthRepository(
            jellyfin = mockk<Jellyfin>(relaxed = true),
            secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true),
            dispatchers = dispatchers,
        )
        authRepository.seedCurrentServer(mockServer)
        sessionManager = mockk()
        cache = mockk(relaxed = true)
        repository = JellyfinPlaylistRepository(
            authRepository = authRepository,
            sessionManager = sessionManager,
            cache = cache,
            dispatchers = dispatchers,
            updateBus = MediaUpdateBus(),
        )
    }

    @Test
    fun getAllPlaylists_whenSuccess_returnsPlaylistList() = runTest {
        val mockItems = listOf(mockk<BaseItemDto> { every { name } returns "My Mix" })
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } returns mockItems

        val result = repository.getAllPlaylists()

        assertTrue(result is ApiResult.Success)
        assertEquals(mockItems, (result as ApiResult.Success).data)
    }

    @Test
    fun getAllPlaylists_whenError_returnsApiError() = runTest {
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } throws RuntimeException("Network error")

        val result = repository.getAllPlaylists()

        assertTrue(result is ApiResult.Error)
        assertEquals("Network error", (result as ApiResult.Error).message)
    }

    @Test
    fun getPlaylistItems_whenSuccess_returnsOrderedItemList() = runTest {
        val mockItems = listOf(mockk<BaseItemDto>(), mockk<BaseItemDto>())
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } returns mockItems

        val result = repository.getPlaylistItems(UUID.randomUUID().toString())

        assertTrue(result is ApiResult.Success)
        assertEquals(mockItems, (result as ApiResult.Success).data)
    }

    @Test
    fun getPlaylistItems_whenError_returnsApiError() = runTest {
        coEvery { sessionManager.executeWithAuth<List<BaseItemDto>>(any(), any()) } throws RuntimeException("Playlist not found")

        val result = repository.getPlaylistItems(UUID.randomUUID().toString())

        assertTrue(result is ApiResult.Error)
        assertEquals("Playlist not found", (result as ApiResult.Error).message)
    }

    @Test
    fun createPlaylist_whenSuccess_returnsNewPlaylistId() = runTest {
        val newId = UUID.randomUUID().toString()
        coEvery { sessionManager.executeWithAuth<String>(any(), any()) } returns newId

        val result = repository.createPlaylist("My Playlist")

        assertTrue(result is ApiResult.Success)
        assertEquals(newId, (result as ApiResult.Success).data)
    }

    @Test
    fun createPlaylist_whenError_returnsApiError() = runTest {
        coEvery { sessionManager.executeWithAuth<String>(any(), any()) } throws RuntimeException("Create failed")

        val result = repository.createPlaylist("My Playlist")

        assertTrue(result is ApiResult.Error)
        assertEquals("Create failed", (result as ApiResult.Error).message)
    }

    @Test
    fun addItemsToPlaylist_whenSuccess_returnsTrue() = runTest {
        coEvery { sessionManager.executeWithAuth<Boolean>(any(), any()) } returns true

        val result = repository.addItemsToPlaylist(UUID.randomUUID().toString(), listOf(UUID.randomUUID().toString()))

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun removeItemsFromPlaylist_whenSuccess_returnsTrue() = runTest {
        coEvery { sessionManager.executeWithAuth<Boolean>(any(), any()) } returns true

        val result = repository.removeItemsFromPlaylist(UUID.randomUUID().toString(), listOf("playlist-item-entry-1"))

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun movePlaylistItem_whenSuccess_returnsTrue() = runTest {
        coEvery { sessionManager.executeWithAuth<Boolean>(any(), any()) } returns true

        val result = repository.movePlaylistItem(UUID.randomUUID().toString(), "playlist-item-entry-1", newIndex = 0)

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun renamePlaylist_whenSuccess_returnsTrue() = runTest {
        coEvery { sessionManager.executeWithAuth<Boolean>(any(), any()) } returns true

        val result = repository.renamePlaylist(UUID.randomUUID().toString(), "New Name")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun deletePlaylist_whenSuccess_returnsTrue() = runTest {
        coEvery { sessionManager.executeWithAuth<Boolean>(any(), any()) } returns true

        val result = repository.deletePlaylist(UUID.randomUUID().toString())

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data)
    }

    @Test
    fun deletePlaylist_whenError_returnsApiError() = runTest {
        coEvery { sessionManager.executeWithAuth<Boolean>(any(), any()) } throws RuntimeException("Delete failed")

        val result = repository.deletePlaylist(UUID.randomUUID().toString())

        assertTrue(result is ApiResult.Error)
        assertEquals("Delete failed", (result as ApiResult.Error).message)
    }
}
