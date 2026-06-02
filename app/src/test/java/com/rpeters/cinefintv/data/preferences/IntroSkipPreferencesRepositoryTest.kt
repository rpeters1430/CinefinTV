package com.rpeters.cinefintv.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IntroSkipPreferencesRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tmpFolder = TemporaryFolder()

    private fun buildRepo(): IntroSkipPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
            produceFile = { tmpFolder.newFile("test_intro_skip.preferences_pb") }
        )
        return IntroSkipPreferencesRepository(dataStore)
    }

    @Test
    fun defaults_areFalse() = runTest {
        val repo = buildRepo()
        val prefs = repo.preferencesFlow.first()
        assertFalse(prefs.autoSkipIntro)
        assertFalse(prefs.autoSkipCredits)
    }

    @Test
    fun setAutoSkipIntro_persistsTrue() = runTest {
        val repo = buildRepo()
        repo.setAutoSkipIntro(true)
        advanceUntilIdle()
        assertTrue(repo.preferencesFlow.first().autoSkipIntro)
    }

    @Test
    fun setAutoSkipCredits_persistsTrue() = runTest {
        val repo = buildRepo()
        repo.setAutoSkipCredits(true)
        advanceUntilIdle()
        assertTrue(repo.preferencesFlow.first().autoSkipCredits)
    }
}
