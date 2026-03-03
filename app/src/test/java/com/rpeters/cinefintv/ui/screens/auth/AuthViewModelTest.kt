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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        coEvery { authRepository.isQuickConnectEnabled(any()) } returns ApiResult.Success(false)
        coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Success(mockk<AuthenticationResult>())
        coEvery { secureCredentialManager.savePassword(any(), any(), any()) } just runs

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("http://192.168.1.1:8096")
        viewModel.testServerConnection()
        advanceUntilIdle()

        viewModel.login("demo", "password")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loginSucceeded)
        assertEquals(null, state.loginError)
        coVerify { authRepository.authenticateUser("http://192.168.1.1:8096", "demo", "password") }
    }

    @Test
    fun login_whenAuthFails_setsError() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.authenticateUser(any(), any(), any()) } returns ApiResult.Error("Bad credentials")

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("http://192.168.1.1:8096")
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

    @Test
    fun startQuickConnect_whenServerNotConnected_setsError() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        // No server URL set — both connectedServerUrl and serverUrlInput are blank
        viewModel.startQuickConnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Connect to a valid server first.", state.quickConnectError)
        assertFalse(state.isQuickConnectLoading)
    }

    @Test
    fun startQuickConnect_whenInitiateSucceeds_setsCodeAndSecret() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.initiateQuickConnect(any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
        // Return Pending so the poll loop doesn't complete during this test
        coEvery { authRepository.getQuickConnectState(any(), any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Pending"))

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("http://192.168.1.1:8096")
        viewModel.startQuickConnect()
        // Advance only enough to let the initiate + first poll run, but not loop infinitely
        advanceTimeBy(100)

        val state = viewModel.uiState.value
        assertEquals("8374", state.quickConnectCode)
        assertEquals("secret123", state.quickConnectSecret)
        assertFalse(state.isQuickConnectLoading)
        coVerify(exactly = 1) { authRepository.getQuickConnectState(any(), any()) }

        // Cancel the poll job so runTest cleanup does not spin the infinite Pending loop
        viewModel.stopQuickConnect()
    }

    @Test
    fun quickConnectPolling_whenApproved_setsLoginSucceeded() = runTest {
        val authResult: org.jellyfin.sdk.model.api.AuthenticationResult = mockk()
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.initiateQuickConnect(any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
        coEvery { authRepository.getQuickConnectState(any(), any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Approved"))
        coEvery { authRepository.authenticateWithQuickConnect(any(), any()) } returns
            ApiResult.Success(authResult)

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("http://192.168.1.1:8096")
        viewModel.startQuickConnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loginSucceeded)
        assertFalse(state.isAuthenticating)
        assertNull(state.quickConnectCode)
        assertNull(state.quickConnectSecret)
        assertNull(state.quickConnectPollStatus)
        assertFalse(state.isQuickConnectLoading)
    }

    @Test
    fun quickConnectPolling_whenDenied_setsErrorAndStopsPolling() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.initiateQuickConnect(any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
        coEvery { authRepository.getQuickConnectState(any(), any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Denied"))

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("http://192.168.1.1:8096")
        viewModel.startQuickConnect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loginSucceeded)
        assertTrue(state.quickConnectError?.contains("denied", ignoreCase = true) == true)
        assertEquals(null, state.quickConnectPollStatus)
        coVerify(exactly = 1) { authRepository.getQuickConnectState(any(), any()) }
    }

    @Test
    fun stopQuickConnect_clearsAllQuickConnectState() = runTest {
        coEvery { authRepository.tryRestoreSession() } returns false
        coEvery { authRepository.initiateQuickConnect(any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
        coEvery { authRepository.getQuickConnectState(any(), any()) } returns
            ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Pending"))

        val viewModel = AuthViewModel(authRepository, secureCredentialManager)
        advanceUntilIdle()

        viewModel.updateServerUrlInput("http://192.168.1.1:8096")
        viewModel.startQuickConnect()
        // Advance only enough to let the initiate + first poll run, but not loop infinitely
        advanceTimeBy(100)

        // Code should be set at this point
        assertNotNull(viewModel.uiState.value.quickConnectCode)

        viewModel.stopQuickConnect()

        val state = viewModel.uiState.value
        assertNull(state.quickConnectCode)
        assertNull(state.quickConnectSecret)
        assertNull(state.quickConnectPollStatus)
        assertNull(state.quickConnectError)
        assertFalse(state.isQuickConnectLoading)
    }
}
