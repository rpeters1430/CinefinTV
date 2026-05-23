package com.rpeters.cinefintv.ui.screens.auth

import com.rpeters.cinefintv.data.repository.DiscoveredServer
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.ServerDiscoveryRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerDiscoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val discoveryRepository = mockk<ServerDiscoveryRepository>()
    private val authRepository = mockk<JellyfinAuthRepository>()
    private val discoveryFlow = MutableSharedFlow<List<DiscoveredServer>>(replay = 1)

    private val rawServer1 = DiscoveredServer(
        serviceName = "Jellyfin1",
        host = "192.168.1.10",
        port = 8096,
        url = "http://192.168.1.10:8096"
    )

    private val rawServer2 = DiscoveredServer(
        serviceName = "Jellyfin2",
        host = "192.168.1.11",
        port = 8096,
        url = "http://192.168.1.11:8096"
    )

    @Test
    fun init_subscribesToDiscoveryFlowAndVerifiesServer_onSuccess() = runTest {
        every { discoveryRepository.discoverServers() } returns discoveryFlow
        val mockSystemInfo = mockk<PublicSystemInfo>(relaxed = true) {
            every { serverName } returns "Custom Named Server"
        }
        coEvery { authRepository.testServerConnection(rawServer1.url) } returns ApiResult.Success(mockSystemInfo)

        val viewModel = ServerDiscoveryViewModel(discoveryRepository, authRepository)
        discoveryFlow.emit(listOf(rawServer1))
        advanceUntilIdle()

        // Verify connection test called
        coVerify(exactly = 1) { authRepository.testServerConnection(rawServer1.url) }

        // Verify UI state reflects connection success
        val state = viewModel.uiState.value
        assertEquals(1, state.servers.size)
        val discoveredServer = state.servers[0]
        assertEquals("Jellyfin1", discoveredServer.serviceName)
        assertEquals("Custom Named Server", discoveredServer.displayName)
        assertFalse(discoveredServer.isVerifying)
        assertTrue(discoveredServer.isVerified)
    }

    @Test
    fun init_subscribesToDiscoveryFlowAndVerifiesServer_onFailure() = runTest {
        every { discoveryRepository.discoverServers() } returns discoveryFlow
        coEvery { authRepository.testServerConnection(rawServer2.url) } returns ApiResult.Error(
            message = "Connection timeout",
            cause = Exception("Connection Failed")
        )

        val viewModel = ServerDiscoveryViewModel(discoveryRepository, authRepository)
        discoveryFlow.emit(listOf(rawServer2))
        advanceUntilIdle()

        // Verify connection test called
        coVerify(exactly = 1) { authRepository.testServerConnection(rawServer2.url) }

        // Verify UI state reflects connection failure
        val state = viewModel.uiState.value
        assertEquals(1, state.servers.size)
        val discoveredServer = state.servers[0]
        assertEquals("Jellyfin2", discoveredServer.serviceName)
        assertEquals("Jellyfin2", discoveredServer.displayName) // name remains unverified default
        assertFalse(discoveredServer.isVerifying)
        assertFalse(discoveredServer.isVerified)
    }
}
