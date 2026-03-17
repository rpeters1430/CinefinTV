package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeHomeRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsItemDetailsAndRelated() = runTest {
        val itemId = UUID.randomUUID().toString()
        val savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId))
        val fakeRepositories = FakeHomeRepositories()
        val item = mockBaseItemDto(itemId, "Test Movie", BaseItemKind.MOVIE)

        coEvery { fakeRepositories.media.getItemDetails(itemId) } returns ApiResult.Success(item)
        coEvery { fakeRepositories.media.getSimilarMovies(itemId, any()) } returns ApiResult.Success(emptyList())
        
        // Return empty seasons for movies
        coEvery { fakeRepositories.media.getSeasonsForSeries(itemId) } returns ApiResult.Success(emptyList())

        every { fakeRepositories.stream.getPosterCardImageUrl(any(), any()) } returns "https://img/poster.jpg"
        every { fakeRepositories.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"
        every { fakeRepositories.stream.getBackdropUrlWithFallback(any(), any()) } returns "https://img/backdrop.jpg"
        every { fakeRepositories.stream.getLandscapeImageUrl(any()) } returns "https://img/landscape.jpg"
        every { fakeRepositories.stream.getLogoUrl(any()) } returns null

        val viewModel = DetailViewModel(savedStateHandle, fakeRepositories.coordinator)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DetailUiState.Content)
        val content = state as DetailUiState.Content
        assertEquals("Test Movie", content.item.title)
        assertEquals(itemId, content.playableItemId)
    }

    @Test
    fun init_whenItemNotFound_setsErrorState() = runTest {
        val itemId = UUID.randomUUID().toString()
        val savedStateHandle = SavedStateHandle(mapOf("itemId" to itemId))
        val fakeRepositories = FakeHomeRepositories()

        coEvery { fakeRepositories.media.getItemDetails(itemId) } returns ApiResult.Error("Not found")

        val viewModel = DetailViewModel(savedStateHandle, fakeRepositories.coordinator)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is DetailUiState.Error)
        assertEquals("Not found", (state as DetailUiState.Error).message)
    }

    private fun mockBaseItemDto(id: String, name: String, kind: BaseItemKind): BaseItemDto {
        val item: BaseItemDto = mockk(relaxed = true)
        every { item.id } returns UUID.fromString(id)
        every { item.name } returns name
        every { item.type } returns kind
        return item
    }
}
