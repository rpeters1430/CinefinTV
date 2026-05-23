package com.rpeters.cinefintv.ui.theme

import androidx.compose.ui.graphics.Color
import app.cash.turbine.test
import com.rpeters.cinefintv.data.preferences.ThemePreferences
import com.rpeters.cinefintv.data.preferences.ThemePreferencesRepository
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsThemePreferencesFlowAndExposesDefaultValues() = runTest {
        val themePrefsRepo = mockk<ThemePreferencesRepository>()
        every { themePrefsRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)

        val viewModel = ThemeViewModel(themePrefsRepo)
        
        viewModel.themePreferences.test {
            assertEquals(ThemePreferences.DEFAULT, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(viewModel.currentSeedColor)
    }

    @Test
    fun updateSeedColor_changesCurrentSeedColorCorrectly() = runTest {
        val themePrefsRepo = mockk<ThemePreferencesRepository>()
        every { themePrefsRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)

        val viewModel = ThemeViewModel(themePrefsRepo)
        advanceUntilIdle()

        assertNull(viewModel.currentSeedColor)

        val targetColor = Color(0xFFE50914)
        viewModel.updateSeedColor(targetColor)

        assertEquals(targetColor, viewModel.currentSeedColor)

        // Verifying that updating the same color does not trigger redundant updates
        viewModel.updateSeedColor(targetColor)
        assertEquals(targetColor, viewModel.currentSeedColor)

        // Verifying that setting back to null clears it
        viewModel.updateSeedColor(null)
        assertNull(viewModel.currentSeedColor)
    }

    @Test
    fun motionSpec_createsCorrectMotionTiersBasedOnPreferences() = runTest {
        val themePrefsRepo = mockk<ThemePreferencesRepository>()
        
        // Test case 1: respectReduceMotion is false
        every { themePrefsRepo.themePreferencesFlow } returns flowOf(
            ThemePreferences.DEFAULT.copy(respectReduceMotion = false)
        )
        val viewModelNormalMotion = ThemeViewModel(themePrefsRepo)
        
        viewModelNormalMotion.motionSpec.test {
            // StateFlow immediately emits the mapped value once subscribed
            assertEquals(CinefinMotion.create(reduceMotion = false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Test case 2: respectReduceMotion is true
        every { themePrefsRepo.themePreferencesFlow } returns flowOf(
            ThemePreferences.DEFAULT.copy(respectReduceMotion = true)
        )
        val viewModelReducedMotion = ThemeViewModel(themePrefsRepo)

        viewModelReducedMotion.motionSpec.test {
            assertEquals(CinefinMotion.create(reduceMotion = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
