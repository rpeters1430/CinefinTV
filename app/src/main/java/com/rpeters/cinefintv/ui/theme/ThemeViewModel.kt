package com.rpeters.cinefintv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.preferences.ThemePreferences
import com.rpeters.cinefintv.data.preferences.ThemePreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

interface ThemeColorController {
    fun updateSeedColor(color: Color?)
}

/**
 * ViewModel responsible for managing global theme state, including Material You seed colors
 * and user preferences for dynamic theming.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferencesRepository: ThemePreferencesRepository
) : ViewModel(), ThemeColorController {

    /**
     * The current theme preferences from DataStore.
     */
    val themePreferences: StateFlow<ThemePreferences> = themePreferencesRepository.themePreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreferences.DEFAULT
        )

    /**
     * Derived motion spec based on user preferences.
     */
    val motionSpec: StateFlow<CinefinMotionSpec> = themePreferences
        .map { prefs -> CinefinMotion.create(reduceMotion = prefs.respectReduceMotion) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CinefinMotion.create()
        )

    /**
     * The current seed color for Material You dynamic theming.
     * This can be updated by screens as users navigate content.
     */
    var currentSeedColor by mutableStateOf<Color?>(null)
        private set

    /**
     * Update the seed color for dynamic theming.
     * Typically called when a movie detail or hero section is focused.
     */
    override fun updateSeedColor(color: Color?) {
        if (currentSeedColor == color) return
        currentSeedColor = color
    }
}
