package com.rpeters.cinefintv.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilePickerUiState(
    val isLoading: Boolean = true,
    val profiles: List<JellyfinServer> = emptyList(),
    val currentUserId: String? = null,
    val isSwitching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilePickerUiState())
    val uiState: StateFlow<ProfilePickerUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            val profiles = authRepository.getSavedProfiles()
            val currentUserId = authRepository.getCurrentServer()?.userId
            _uiState.update {
                it.copy(
                    isLoading = false,
                    profiles = profiles,
                    currentUserId = currentUserId,
                )
            }
        }
    }

    fun switchToProfile(server: JellyfinServer, onSwitched: () -> Unit) {
        if (_uiState.value.isSwitching) return
        _uiState.update { it.copy(isSwitching = true, error = null) }
        viewModelScope.launch {
            val success = authRepository.switchToProfile(server)
            if (success) {
                onSwitched()
            } else {
                _uiState.update {
                    it.copy(isSwitching = false, error = "Could not switch to this profile.")
                }
            }
        }
    }

    fun removeProfile(server: JellyfinServer) {
        viewModelScope.launch {
            authRepository.removeProfile(server)
            loadProfiles()
        }
    }
}
