package com.rpeters.cinefintv.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.ServerDiscoveryRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoveredServerUi(
    val serviceName: String,
    val host: String,
    val port: Int,
    val url: String,
    val displayName: String,
    val isVerifying: Boolean = true,
    val isVerified: Boolean = false,
)

data class DiscoveryUiState(
    val servers: List<DiscoveredServerUi> = emptyList(),
)

@HiltViewModel
class ServerDiscoveryViewModel @Inject constructor(
    private val discoveryRepository: ServerDiscoveryRepository,
    private val authRepository: JellyfinAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            discoveryRepository.discoverServers().collect { discovered ->
                val current = _uiState.value.servers
                val updated = discovered.map { raw ->
                    current.find { it.serviceName == raw.serviceName }
                        ?: DiscoveredServerUi(
                            serviceName = raw.serviceName,
                            host = raw.host,
                            port = raw.port,
                            url = raw.url,
                            displayName = raw.serviceName,
                        ).also { verifyServer(it) }
                }
                _uiState.update { it.copy(servers = updated) }
            }
        }
    }

    private fun verifyServer(server: DiscoveredServerUi) {
        viewModelScope.launch {
            val result = authRepository.testServerConnection(server.url)
            _uiState.update { state ->
                state.copy(
                    servers = state.servers.map {
                        if (it.serviceName != server.serviceName) return@map it
                        when (result) {
                            is ApiResult.Success -> it.copy(
                                displayName = result.data.serverName?.takeIf { n -> n.isNotBlank() }
                                    ?: it.displayName,
                                isVerifying = false,
                                isVerified = true,
                            )
                            is ApiResult.Error -> it.copy(isVerifying = false, isVerified = false)
                            is ApiResult.Loading -> it
                        }
                    }
                )
            }
        }
    }
}
