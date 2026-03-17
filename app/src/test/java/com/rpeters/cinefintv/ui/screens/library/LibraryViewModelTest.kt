package com.rpeters.cinefintv.ui.screens.library

import com.rpeters.cinefintv.testutil.FakeHomeRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_setsCorrectTitle() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val viewModel = LibraryViewModel(fakeRepositories.coordinator)

        viewModel.load(LibraryCategory.MOVIES)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LibraryUiState.Content)
        assertEquals("Movies", (state as LibraryUiState.Content).title)
    }

    @Test
    fun load_collections_setsCorrectTitle() = runTest {
        val fakeRepositories = FakeHomeRepositories()
        val viewModel = LibraryViewModel(fakeRepositories.coordinator)

        viewModel.load(LibraryCategory.COLLECTIONS)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is LibraryUiState.Content)
        assertEquals("Collections", (state as LibraryUiState.Content).title)
    }
}
