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
import com.rpeters.cinefintv.data.preferences.ScreensaverPreferences
import com.rpeters.cinefintv.data.preferences.ScreensaverPreferencesRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var themeRepo: ThemePreferencesRepository
    private lateinit var playbackRepo: PlaybackPreferencesRepository
    private lateinit var subtitleRepo: SubtitleAppearancePreferencesRepository
    private lateinit var libraryRepo: LibraryActionsPreferencesRepository
    private lateinit var userRepo: JellyfinUserRepository
    private lateinit var introSkipRepo: IntroSkipPreferencesRepository
    private lateinit var screensaverRepo: ScreensaverPreferencesRepository

    @Before
    fun setup() {
        themeRepo = mockk(relaxed = true)
        playbackRepo = mockk(relaxed = true)
        subtitleRepo = mockk(relaxed = true)
        libraryRepo = mockk(relaxed = true)
        userRepo = mockk(relaxed = true)
        introSkipRepo = mockk(relaxed = true)
        screensaverRepo = mockk(relaxed = true)

        // Set default flow returns
        every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
        every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
        every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
        every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
        every { introSkipRepo.preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
        every { screensaverRepo.screensaverPreferencesFlow } returns flowOf(ScreensaverPreferences.DEFAULT)
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            themeRepo,
            playbackRepo,
            subtitleRepo,
            libraryRepo,
            userRepo,
            introSkipRepo,
            screensaverRepo
        )
    }

    @Test
    fun init_loadsPreferences() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
    }

    @Test
    fun setVideoSeekIncrement_updatesUiStateAndRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setVideoSeekIncrement(VideoSeekIncrement.FIVE_SECONDS)
        advanceUntilIdle()

        assertEquals(VideoSeekIncrement.FIVE_SECONDS, viewModel.uiState.value.playback.videoSeekIncrement)
        coVerify { playbackRepo.setVideoSeekIncrement(VideoSeekIncrement.FIVE_SECONDS) }
    }

    @Test
    fun setAudioLanguage_updatesUiStateAndRepository() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAudioLanguage(AudioLanguagePreference.SPANISH)
        advanceUntilIdle()

        assertEquals(AudioLanguagePreference.SPANISH, viewModel.uiState.value.playback.audioLanguage)
        coVerify { playbackRepo.setAudioLanguage(AudioLanguagePreference.SPANISH) }
    }

    @Test
    fun logout_invokesRepositoryAndClearsSigningOutFlag() = runTest {
        coEvery { userRepo.logout() } just runs

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSigningOut)
        assertEquals(null, viewModel.uiState.value.signOutError)
        coVerify(exactly = 1) { userRepo.logout() }
    }

    @Test
    fun logout_isSigningOut_preservedWhenPreferenceFlowEmitsWhileLogoutPending() = runTest {
        val themeFlow = MutableSharedFlow<ThemePreferences>(replay = 1)
        every { themeRepo.themePreferencesFlow } returns themeFlow

        val logoutGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { userRepo.logout() } coAnswers { logoutGate.await() }

        themeFlow.emit(ThemePreferences.DEFAULT)

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSigningOut)

        viewModel.logout()
        runCurrent()
        assertTrue("isSigningOut should be true while logout is in progress", viewModel.uiState.value.isSigningOut)

        themeFlow.emit(ThemePreferences.DEFAULT)
        advanceUntilIdle()

        assertTrue("isSigningOut must not be cleared by a concurrent preference update", viewModel.uiState.value.isSigningOut)

        logoutGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSigningOut)
    }

    @Test
    fun setAutoSkipIntro_callsRepository() = runTest {
        coEvery { introSkipRepo.setAutoSkipIntro(true) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAutoSkipIntro(true)
        advanceUntilIdle()

        coVerify { introSkipRepo.setAutoSkipIntro(true) }
        assertTrue(viewModel.uiState.value.introSkip.autoSkipIntro)
    }

    @Test
    fun setAutoSkipCredits_callsRepository() = runTest {
        coEvery { introSkipRepo.setAutoSkipCredits(true) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAutoSkipCredits(true)
        advanceUntilIdle()

        coVerify { introSkipRepo.setAutoSkipCredits(true) }
        assertTrue(viewModel.uiState.value.introSkip.autoSkipCredits)
    }

    @Test
    fun setScreensaverEnabled_updatesUiStateAndRepository() = runTest {
        coEvery { screensaverRepo.setEnabled(true) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setScreensaverEnabled(true)
        advanceUntilIdle()

        coVerify { screensaverRepo.setEnabled(true) }
        assertTrue(viewModel.uiState.value.screensaver.isEnabled)
    }

    @Test
    fun setScreensaverIdleTimeout_updatesUiStateAndRepository() = runTest {
        coEvery { screensaverRepo.setIdleTimeoutMinutes(10) } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setScreensaverIdleTimeout(10)
        advanceUntilIdle()

        coVerify { screensaverRepo.setIdleTimeoutMinutes(10) }
        assertEquals(10, viewModel.uiState.value.screensaver.idleTimeoutMinutes)
    }
}
