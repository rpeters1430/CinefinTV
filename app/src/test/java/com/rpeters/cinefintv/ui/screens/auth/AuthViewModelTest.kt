package com.rpeters.cinefintv.ui.screens.auth

import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.jellyfin.sdk.model.api.AuthenticationResult

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: JellyfinAuthRepository = mockk()
    private val secureCredentialManager: SecureCredentialManager = mockk()

    @Test
    fun testServerConnection_whenUrlInvalid_setsValidationError() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("not a url")
        viewModel.testServerConnection()

        val state = viewModel.uiState.value
        assertEquals("Enter a valid server URL.", state.connectionError)
        assertEquals(null, state.connectedServerUrl)
    }

    @Test
    fun login_whenAuthSucceeds_setsSuccessAndSavesPassword() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.testServerConnection(any()) } returns ApiResult.Success(mockk())
        coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk<AuthenticationResult>())
        every { secureCredentialManager.savePassword(any(), any(), any()) } just runs

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("https://demo.jellyfin.org")
        viewModel.testServerConnection()
        advanceUntilIdle()

        viewModel.login("demo", "password")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loginSucceeded)
        assertEquals(null, state.loginError)
        coVerify { authRepository.authenticateUser("https://demo.jellyfin.org", "demo", "password") }
    }

    @Test
    fun login_whenAuthFails_setsError() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Error("Bad credentials")

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("https://demo.jellyfin.org")
        viewModel.login("demo", "wrong")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loginSucceeded)
        assertEquals("Bad credentials", state.loginError)
    }

    @Test
    fun init_whenSessionRestored_marksSessionActive() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns true

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSessionChecked)
        assertTrue(state.isSessionActive)
    }
}
