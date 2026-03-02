package com.rpeters.cinefintv.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.ServerUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val isSessionChecked: Boolean = false,
    val isSessionActive: Boolean = false,
    val isQuickConnectEnabled: Boolean = false,
    val isQuickConnectLoading: Boolean = false,
    val quickConnectCode: String? = null,
    val quickConnectSecret: String? = null,
    val quickConnectPollStatus: String? = null,
    val quickConnectError: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val secureCredentialManager: SecureCredentialManager,
) : ViewModel() {
    private var quickConnectPollJob: Job? = null

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkSavedSession()
    }

    private fun checkSavedSession() {
        viewModelScope.launch {
            val isActive = authRepository.tryRestoreSession()
            _uiState.update {
                it.copy(
                    isSessionChecked = true,
                    isSessionActive = isActive,
                )
            }
        }
    }

    fun updateServerUrlInput(value: String) {
        _uiState.update {
            it.copy(
                serverUrlInput = value,
                connectionError = null,
            )
        }
    }

    fun testServerConnection() {
        stopQuickConnect()
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
                    checkQuickConnectAvailability(normalizedUrl)
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
        stopQuickConnect()
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
                    stopQuickConnect()
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

    fun startQuickConnect() {
        val serverUrl = _uiState.value.connectedServerUrl
            ?: ServerUrlValidator.validateAndNormalizeUrl(_uiState.value.serverUrlInput)

        if (serverUrl == null) {
            _uiState.update { it.copy(quickConnectError = "Connect to a valid server first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isQuickConnectLoading = true,
                    quickConnectError = null,
                    quickConnectCode = null,
                    quickConnectSecret = null,
                    quickConnectPollStatus = "Generating code...",
                )
            }

            when (val result = authRepository.initiateQuickConnect(serverUrl)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isQuickConnectLoading = false,
                            quickConnectCode = result.data.code,
                            quickConnectSecret = result.data.secret,
                            quickConnectPollStatus = "Waiting for approval...",
                            quickConnectError = null,
                            serverUrlInput = serverUrl,
                            connectedServerUrl = serverUrl,
                        )
                    }
                    beginQuickConnectPolling(serverUrl, result.data.secret)
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isQuickConnectLoading = false,
                            quickConnectPollStatus = null,
                            quickConnectError = result.message,
                        )
                    }
                }

                is ApiResult.Loading -> Unit
            }
        }
    }

    fun generateNewQuickConnectCode() {
        stopQuickConnect()
        startQuickConnect()
    }

    fun stopQuickConnect() {
        quickConnectPollJob?.cancel()
        quickConnectPollJob = null
        _uiState.update {
            it.copy(
                isQuickConnectLoading = false,
                quickConnectCode = null,
                quickConnectSecret = null,
                quickConnectPollStatus = null,
                quickConnectError = null,
            )
        }
    }

    private fun checkQuickConnectAvailability(serverUrl: String) {
        viewModelScope.launch {
            when (val result = authRepository.isQuickConnectEnabled(serverUrl)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isQuickConnectEnabled = result.data,
                            quickConnectError = if (result.data) it.quickConnectError else null,
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isQuickConnectEnabled = false,
                            quickConnectError = result.message,
                        )
                    }
                }

                is ApiResult.Loading -> Unit
            }
        }
    }

    private fun beginQuickConnectPolling(serverUrl: String, secret: String) {
        quickConnectPollJob?.cancel()
        quickConnectPollJob = viewModelScope.launch {
            while (true) {
                when (val stateResult = authRepository.getQuickConnectState(serverUrl, secret)) {
                    is ApiResult.Success -> {
                        when {
                            stateResult.data.isPending -> {
                                _uiState.update {
                                    it.copy(
                                        quickConnectPollStatus = "Waiting for approval...",
                                        quickConnectError = null,
                                    )
                                }
                            }

                            stateResult.data.isApproved -> {
                                _uiState.update {
                                    it.copy(quickConnectPollStatus = "Approving sign-in...")
                                }
                                authenticateWithQuickConnect(serverUrl, secret)
                                return@launch
                            }

                            stateResult.data.isDenied -> {
                                _uiState.update {
                                    it.copy(
                                        quickConnectPollStatus = null,
                                        quickConnectError = "Quick Connect request denied. Generate a new code and try again.",
                                    )
                                }
                                return@launch
                            }

                            stateResult.data.isExpired -> {
                                _uiState.update {
                                    it.copy(
                                        quickConnectPollStatus = null,
                                        quickConnectError = "Quick Connect code expired. Generate a new code to continue.",
                                    )
                                }
                                return@launch
                            }
                        }
                    }

                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                quickConnectPollStatus = null,
                                quickConnectError = stateResult.message,
                            )
                        }
                        return@launch
                    }

                    is ApiResult.Loading -> Unit
                }

                delay(3_000)
            }
        }
    }

    private fun authenticateWithQuickConnect(serverUrl: String, secret: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAuthenticating = true,
                    quickConnectError = null,
                )
            }
            when (val result = authRepository.authenticateWithQuickConnect(serverUrl, secret)) {
                is ApiResult.Success -> {
                    stopQuickConnect()
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            loginSucceeded = true,
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            quickConnectPollStatus = null,
                            quickConnectError = result.message,
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
        stopQuickConnect()
        _uiState.update {
            it.copy(
                connectedServerUrl = null,
                loginError = null,
                isAuthenticating = false,
                isQuickConnectEnabled = false,
            )
        }
    }

    override fun onCleared() {
        stopQuickConnect()
        super.onCleared()
    }
}
