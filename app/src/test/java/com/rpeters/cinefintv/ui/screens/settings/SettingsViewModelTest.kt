package com.rpeters.cinefintv.ui.screens.settings

import com.rpeters.cinefintv.data.preferences.LibraryActionsPreferences
import com.rpeters.cinefintv.data.preferences.LibraryActionsPreferencesRepository
import com.rpeters.cinefintv.data.preferences.AudioLanguagePreference
import com.rpeters.cinefintv.data.preferences.PlaybackPreferences
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferences
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferencesRepository
import com.rpeters.cinefintv.data.preferences.ThemePreferences
import com.rpeters.cinefintv.data.preferences.ThemePreferencesRepository
import com.rpeters.cinefintv.data.preferences.VideoSeekIncrement
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsPreferences() = runTest {
        val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        val viewModel = SettingsViewModel(
            themeRepo,
            playbackRepo,
            subtitleRepo,
            libraryRepo,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
    }

    @Test
    fun setVideoSeekIncrement_updatesUiStateAndRepository() = runTest {
        val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        val viewModel = SettingsViewModel(
            themeRepo,
            playbackRepo,
            subtitleRepo,
            libraryRepo,
        )
        advanceUntilIdle()

        viewModel.setVideoSeekIncrement(VideoSeekIncrement.FIVE_SECONDS)
        advanceUntilIdle()

        assertEquals(VideoSeekIncrement.FIVE_SECONDS, viewModel.uiState.value.playback.videoSeekIncrement)
        coVerify { playbackRepo.setVideoSeekIncrement(VideoSeekIncrement.FIVE_SECONDS) }
    }

    @Test
    fun setAudioLanguage_updatesUiStateAndRepository() = runTest {
        val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        val viewModel = SettingsViewModel(
            themeRepo,
            playbackRepo,
            subtitleRepo,
            libraryRepo,
        )
        advanceUntilIdle()

        viewModel.setAudioLanguage(AudioLanguagePreference.SPANISH)
        advanceUntilIdle()

        assertEquals(AudioLanguagePreference.SPANISH, viewModel.uiState.value.playback.audioLanguage)
        coVerify { playbackRepo.setAudioLanguage(AudioLanguagePreference.SPANISH) }
    }
}
