package com.rpeters.cinefintv.ui.screens.profile

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testServer1 = JellyfinServer(
        id = "server1",
        name = "My Server",
        url = "http://192.168.1.100:8096",
        userId = "user1",
        username = "Alice",
        accessToken = "token1"
    )

    private val testServer2 = JellyfinServer(
        id = "server2",
        name = "Other Server",
        url = "http://192.168.1.101:8096",
        userId = "user2",
        username = "Bob",
        accessToken = "token2"
    )

    @Test
    fun init_loadsProfilesAndActiveUserSuccessfully() = runTest {
        val secureCredentialManager = mockk<com.rpeters.cinefintv.data.SecureCredentialManager>(relaxed = true)
        coEvery { secureCredentialManager.loadProfiles() } returns listOf(testServer1, testServer2)
        val authRepository = JellyfinAuthRepository(
            jellyfin = mockk(relaxed = true),
            secureCredentialManager = secureCredentialManager,
            dispatchers = com.rpeters.cinefintv.data.common.TestDispatcherProvider(mainDispatcherRule.dispatcher)
        )
        authRepository.seedCurrentServer(testServer1)

        val viewModel = ProfilePickerViewModel(authRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isSwitching)
        assertNull(state.error)
        assertEquals(2, state.profiles.size)
        assertEquals("user1", state.currentUserId)
        assertEquals(testServer1, state.profiles[0])
    }

    @Test
    fun switchToProfile_success_updatesStateAndTriggersCallback() = runTest {
        val secureCredentialManager = mockk<com.rpeters.cinefintv.data.SecureCredentialManager>(relaxed = true)
        coEvery { secureCredentialManager.loadProfiles() } returns listOf(testServer1, testServer2)
        val authRepository = JellyfinAuthRepository(
            jellyfin = mockk(relaxed = true),
            secureCredentialManager = secureCredentialManager,
            dispatchers = com.rpeters.cinefintv.data.common.TestDispatcherProvider(mainDispatcherRule.dispatcher)
        )
        authRepository.seedCurrentServer(testServer1)

        val viewModel = ProfilePickerViewModel(authRepository)
        advanceUntilIdle()

        var callbackTriggered = false
        viewModel.switchToProfile(testServer2) {
            callbackTriggered = true
        }

        // Switching should set isSwitching to true
        assertTrue(viewModel.uiState.value.isSwitching)

        advanceUntilIdle()

        // Switch completed
        assertTrue(callbackTriggered)
        assertNull(viewModel.uiState.value.error)
        assertEquals("server2", authRepository.getCurrentServer()?.id)
    }

    @Test
    fun switchToProfile_failure_setsErrorAndResetsSwitchingState() = runTest {
        val secureCredentialManager = mockk<com.rpeters.cinefintv.data.SecureCredentialManager>(relaxed = true)
        coEvery { secureCredentialManager.loadProfiles() } returns listOf(testServer1, testServer2)
        // Make the saveServerState throw to simulate failure
        coEvery { secureCredentialManager.saveServerState(any()) } throws java.io.IOException("failed to save")
        val authRepository = JellyfinAuthRepository(
            jellyfin = mockk(relaxed = true),
            secureCredentialManager = secureCredentialManager,
            dispatchers = com.rpeters.cinefintv.data.common.TestDispatcherProvider(mainDispatcherRule.dispatcher)
        )
        authRepository.seedCurrentServer(testServer1)

        val viewModel = ProfilePickerViewModel(authRepository)
        advanceUntilIdle()

        var callbackTriggered = false
        // switchToProfile will still return true if saveServerState fails because it catches the exception and returns true,
        // so to test failure, we pass a server with null accessToken which makes switchToProfile return false immediately.
        val invalidServer = testServer2.copy(accessToken = null)
        viewModel.switchToProfile(invalidServer) {
            callbackTriggered = true
        }

        advanceUntilIdle()

        assertFalse(callbackTriggered)
        assertFalse(viewModel.uiState.value.isSwitching)
        assertEquals("Could not switch to this profile.", viewModel.uiState.value.error)
    }

    @Test
    fun removeProfile_invokesRepositoryAndRefreshesProfiles() = runTest {
        val secureCredentialManager = mockk<com.rpeters.cinefintv.data.SecureCredentialManager>(relaxed = true)
        coEvery { secureCredentialManager.loadProfiles() } returnsMany listOf(
            listOf(testServer1, testServer2), // Initial load
            listOf(testServer1)               // After deletion load
        )
        val authRepository = JellyfinAuthRepository(
            jellyfin = mockk(relaxed = true),
            secureCredentialManager = secureCredentialManager,
            dispatchers = com.rpeters.cinefintv.data.common.TestDispatcherProvider(mainDispatcherRule.dispatcher)
        )
        authRepository.seedCurrentServer(testServer1)

        val viewModel = ProfilePickerViewModel(authRepository)
        advanceUntilIdle()

        // Initial state
        assertEquals(2, viewModel.uiState.value.profiles.size)

        // Delete profile
        viewModel.removeProfile(testServer2)
        advanceUntilIdle()

        // Check verification
        coVerify(exactly = 1) { secureCredentialManager.removeProfile("user2", testServer2.url) }
        assertEquals(1, viewModel.uiState.value.profiles.size)
        assertEquals(testServer1, viewModel.uiState.value.profiles[0])
    }
}
