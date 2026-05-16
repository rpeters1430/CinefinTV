package com.rpeters.cinefintv.ui.navigation

import androidx.lifecycle.ViewModel
import com.rpeters.cinefintv.VoiceSearchCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class VoiceSearchNavViewModel @Inject constructor(
    coordinator: VoiceSearchCoordinator,
) : ViewModel() {
    val pendingQuery: SharedFlow<String> = coordinator.pendingQuery
}
