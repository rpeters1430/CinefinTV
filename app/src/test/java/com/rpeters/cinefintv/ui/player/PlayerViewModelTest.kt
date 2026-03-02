package com.rpeters.cinefintv.ui.player

import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakePlayerRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_whenItemIdMissing_setsFriendlyError() = runTest {
        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "")),
            repositories = FakePlayerRepositories().coordinator,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No playable item was provided.", state.errorMessage)
        assertNull(state.streamUrl)
    }

    @Test
    fun load_whenStreamUrlMissing_setsStreamError() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns null

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Unable to create a stream for this item.", state.errorMessage)
        assertNull(state.streamUrl)
    }

    @Test
    fun load_whenDetailsFail_usesFallbackTitle() = runTest {
        val fakeRepositories = FakePlayerRepositories()
        every { fakeRepositories.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
        coEvery { fakeRepositories.media.getItemDetails("item-1") } returns ApiResult.Error("not found")

        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("itemId" to "item-1")),
            repositories = fakeRepositories.coordinator,
            okHttpClient = OkHttpClient(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Now Playing", state.title)
        assertEquals("https://stream/item-1", state.streamUrl)
    }

}
