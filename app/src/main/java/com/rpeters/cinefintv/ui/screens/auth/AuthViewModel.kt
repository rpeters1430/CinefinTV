package com.rpeters.cinefintv.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.ServerUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val serverUrlInput: String = "",
    val connectedServerUrl: String? = null,
    val isTestingConnection: Boolean = false,
    val isAuthenticating: Boolean = false,
    val connectionError: String? = null,
    val loginError: String? = null,
    val loginSucceeded: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val secureCredentialManager: SecureCredentialManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateServerUrlInput(value: String) {
        _uiState.update {
            it.copy(
                serverUrlInput = value,
                connectionError = null,
            )
        }
    }

    fun testServerConnection() {
        val normalizedUrl = ServerUrlValidator.validateAndNormalizeUrl(_uiState.value.serverUrlInput)
        if (normalizedUrl == null) {
            _uiState.update {
                it.copy(
                    connectedServerUrl = null,
                    connectionError = "Enter a valid server URL.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingConnection = true,
                    connectionError = null,
                    loginError = null,
                    connectedServerUrl = null,
                    serverUrlInput = normalizedUrl,
                )
            }

            when (val result = authRepository.testServerConnection(normalizedUrl)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectedServerUrl = normalizedUrl,
                            connectionError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectedServerUrl = null,
                            connectionError = result.message,
                        )
                    }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun login(username: String, password: String) {
        val serverUrl = _uiState.value.connectedServerUrl
            ?: ServerUrlValidator.validateAndNormalizeUrl(_uiState.value.serverUrlInput)

        if (serverUrl == null) {
            _uiState.update { it.copy(loginError = "Connect to a valid server first.") }
            return
        }

        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(loginError = "Username and password are required.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAuthenticating = true,
                    loginError = null,
                    connectionError = null,
                    serverUrlInput = serverUrl,
                    connectedServerUrl = serverUrl,
                )
            }

            when (val result = authRepository.authenticateUser(serverUrl, username.trim(), password)) {
                is ApiResult.Success -> {
                    secureCredentialManager.savePassword(serverUrl, username.trim(), password)
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            loginSucceeded = true,
                            loginError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            loginSucceeded = false,
                            loginError = result.message,
                        )
                    }
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun resetLoginSuccess() {
        _uiState.update { it.copy(loginSucceeded = false) }
    }

    fun clearLoginError() {
        _uiState.update { it.copy(loginError = null) }
    }

    fun returnToServerEntry() {
        _uiState.update {
            it.copy(
                connectedServerUrl = null,
                loginError = null,
                isAuthenticating = false,
            )
        }
    }
}
