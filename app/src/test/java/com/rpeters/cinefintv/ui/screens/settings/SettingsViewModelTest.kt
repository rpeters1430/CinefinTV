package com.rpeters.cinefintv.ui.screens.settings

import com.rpeters.cinefintv.data.preferences.IntroSkipPreferences
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepository
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
import com.rpeters.cinefintv.data.repository.JellyfinUserRepository
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
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
        val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
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
        val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
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
        val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
        advanceUntilIdle()

        viewModel.setAudioLanguage(AudioLanguagePreference.SPANISH)
        advanceUntilIdle()

        assertEquals(AudioLanguagePreference.SPANISH, viewModel.uiState.value.playback.audioLanguage)
        coVerify { playbackRepo.setAudioLanguage(AudioLanguagePreference.SPANISH) }
    }

    @Test
    fun logout_invokesRepositoryAndClearsSigningOutFlag() = runTest {
        val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)
        val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }

        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
        coEvery { userRepo.logout() } just runs

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSigningOut)
        assertEquals(null, viewModel.uiState.value.signOutError)
        coVerify(exactly = 1) { userRepo.logout() }
    }

    @Test
    fun logout_isSigningOut_preservedWhenPreferenceFlowEmitsWhileLogoutPending() = runTest {
        // Regression test: the combine() flow must not overwrite isSigningOut with a stale false
        // when a preference DataStore flow emits while logout is in progress.
        val themeFlow = MutableSharedFlow<ThemePreferences>(replay = 1)
        val themeRepo = mockk<ThemePreferencesRepository>()
        every { themeRepo.themePreferencesFlow } returns themeFlow
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
        val userRepo = mockk<JellyfinUserRepository>()
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }
        val logoutGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { userRepo.logout() } coAnswers { logoutGate.await() }

        themeFlow.emit(ThemePreferences.DEFAULT)

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSigningOut)

        // Begin logout (will stay pending until logoutGate is completed)
        viewModel.logout()
        runCurrent()
        assertTrue("isSigningOut should be true while logout is in progress", viewModel.uiState.value.isSigningOut)

        // Simulate a concurrent preference update firing while logout is pending
        themeFlow.emit(ThemePreferences.DEFAULT)
        advanceUntilIdle()

        // isSigningOut must still be true — logout has not completed yet
        assertTrue("isSigningOut must not be cleared by a concurrent preference update", viewModel.uiState.value.isSigningOut)

        // Complete logout to clean up
        logoutGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSigningOut)
    }

    @Test
    fun setAutoSkipIntro_callsRepository() = runTest {
        val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)
        val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }
        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
        coEvery { introSkipRepo.setAutoSkipIntro(true) } returns Unit

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
        advanceUntilIdle()

        viewModel.setAutoSkipIntro(true)
        advanceUntilIdle()

        coVerify { introSkipRepo.setAutoSkipIntro(true) }
        assertTrue(viewModel.uiState.value.introSkip.autoSkipIntro)
    }

    @Test
    fun setAutoSkipCredits_callsRepository() = runTest {
        val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
        val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
        val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
        val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)
        val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
        val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
            every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        }
        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
        coEvery { introSkipRepo.setAutoSkipCredits(true) } returns Unit

        val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
        advanceUntilIdle()

        viewModel.setAutoSkipCredits(true)
        advanceUntilIdle()

        coVerify { introSkipRepo.setAutoSkipCredits(true) }
        assertTrue(viewModel.uiState.value.introSkip.autoSkipCredits)
    }
}
