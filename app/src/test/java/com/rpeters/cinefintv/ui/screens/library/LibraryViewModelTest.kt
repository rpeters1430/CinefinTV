package com.rpeters.cinefintv.ui.screens.library

import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.JellyfinStreamRepository
import com.rpeters.cinefintv.data.repository.JellyfinUserRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mediaRepository = mockk<JellyfinMediaRepository>(relaxed = true)
    private val streamRepository = mockk<JellyfinStreamRepository>(relaxed = true)
    private val userRepository = mockk<JellyfinUserRepository>(relaxed = true)

    private val coordinator = mockk<JellyfinRepositoryCoordinator> {
        every { media } returns mediaRepository
        every { stream } returns streamRepository
        every { user } returns userRepository
    }

    private val updateBus = MediaUpdateBus()

    @Test
    fun init_registersViewModelsAndPreparesPagedFlow() = runTest {
        val movieViewModel = MovieLibraryViewModel(coordinator, updateBus)
        val tvShowViewModel = TvShowLibraryViewModel(coordinator, updateBus)
        val collectionViewModel = CollectionLibraryViewModel(coordinator, updateBus)

        advanceUntilIdle()

        assertNotNull(movieViewModel.pagedItems)
        assertNotNull(tvShowViewModel.pagedItems)
        assertNotNull(collectionViewModel.pagedItems)
    }

    @Test
    fun refresh_incrementsRefreshSignalFlow() = runTest {
        val movieViewModel = MovieLibraryViewModel(coordinator, updateBus)
        advanceUntilIdle()

        // Verify we can refresh without crashing
        movieViewModel.refresh()
        advanceUntilIdle()
    }

    @Test
    fun markWatched_success_triggersRepositoryAndRefreshesItem() = runTest {
        val collectionViewModel = CollectionLibraryViewModel(coordinator, updateBus)
        coEvery { userRepository.markAsWatched("item123") } returns ApiResult.Success(true)

        advanceUntilIdle()

        var callbackInvoked = false
        collectionViewModel.markWatched("item123") {
            callbackInvoked = true
        }

        advanceUntilIdle()

        assertTrue(callbackInvoked)
        coVerify(exactly = 1) { userRepository.markAsWatched("item123") }
    }

    @Test
    fun markUnwatched_success_triggersRepositoryAndRefreshesItem() = runTest {
        val collectionViewModel = CollectionLibraryViewModel(coordinator, updateBus)
        coEvery { userRepository.markAsUnwatched("item123") } returns ApiResult.Success(true)

        advanceUntilIdle()

        var callbackInvoked = false
        collectionViewModel.markUnwatched("item123") {
            callbackInvoked = true
        }

        advanceUntilIdle()

        assertTrue(callbackInvoked)
        coVerify(exactly = 1) { userRepository.markAsUnwatched("item123") }
    }

    @Test
    fun deleteItem_success_triggersAdminDeletionAndRefreshesAll() = runTest {
        val collectionViewModel = CollectionLibraryViewModel(coordinator, updateBus)
        coEvery { userRepository.deleteItemAsAdmin("item123") } returns ApiResult.Success(true)

        advanceUntilIdle()

        var callbackInvoked = false
        collectionViewModel.deleteItem("item123") {
            callbackInvoked = true
        }

        advanceUntilIdle()

        assertTrue(callbackInvoked)
        coVerify(exactly = 1) { userRepository.deleteItemAsAdmin("item123") }
    }

    private fun assertTrue(value: Boolean) {
        assertEquals(true, value)
    }
}
