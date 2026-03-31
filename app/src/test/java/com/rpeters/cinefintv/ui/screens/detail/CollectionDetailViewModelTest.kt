package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeCollectionDetailRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val updateBus = MediaUpdateBus()

    private fun savedStateHandle(itemId: String) =
        SavedStateHandle(mapOf("itemId" to itemId))

    private fun makeItemDto(
        id: String = UUID.randomUUID().toString(),
        type: BaseItemKind = BaseItemKind.VIDEO,
        title: String = "Sample Video",
        year: Int? = 2024,
        runtimeTicks: Long? = 7_200_000_000L,
        childCount: Int? = null,
        played: Boolean = false,
        playedPercentage: Double = 0.0,
        createdAt: LocalDateTime? = LocalDateTime.parse("2026-03-27T10:15:30"),
    ): BaseItemDto {
        val userData = mockk<UserItemDataDto>(relaxed = true) {
            every { this@mockk.played } returns played
            every { this@mockk.playedPercentage } returns playedPercentage
            every { this@mockk.playbackPositionTicks } returns if (playedPercentage > 0.0) 100_000_000L else 0L
        }

        return mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { name } returns title
            every { this@mockk.type } returns type
            every { productionYear } returns year
            every { runTimeTicks } returns runtimeTicks
            every { this@mockk.childCount } returns childCount
            every { this@mockk.userData } returns userData
            every { overview } returns "Overview for $title"
            every { mediaSources } returns null
            every { this@mockk.dateCreated } returns createdAt
        }
    }

    @Test
    fun load_whenVideo_populatesPolishedMetadata() = runTest {
        val fakeRepos = FakeCollectionDetailRepositories()
        val itemId = UUID.randomUUID().toString()
        val dto = makeItemDto(
            id = itemId,
            type = BaseItemKind.VIDEO,
            title = "Concert Cut",
            year = 2025,
            runtimeTicks = 5_400_000_000L,
            playedPercentage = 42.0,
        )

        coEvery { fakeRepos.media.getItemDetails(itemId) } returns ApiResult.Success(dto)
        every { fakeRepos.stream.getBackdropUrl(any()) } returns "https://img/backdrop.jpg"
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns "https://img/poster.jpg"

        val vm = CollectionDetailViewModel(fakeRepos.coordinator, updateBus, savedStateHandle(itemId))
        advanceUntilIdle()

        val state = vm.uiState.value as CollectionDetailUiState.Content
        assertFalse(state.stuff.isCollection)
        assertEquals("Concert Cut", state.stuff.title)
        assertEquals(2025, state.stuff.year)
        assertEquals("9m", state.stuff.runtime)
        assertEquals("2026-03-27", state.stuff.addedDate)
        assertEquals("https://img/backdrop.jpg", state.stuff.backdropUrl)
        assertEquals("https://img/poster.jpg", state.stuff.posterUrl)
        assertEquals(0.42f, state.stuff.playbackProgress)
    }

    @Test
    fun load_whenCollection_mapsChildrenWithSubtitles() = runTest {
        val fakeRepos = FakeCollectionDetailRepositories()
        val itemId = UUID.randomUUID().toString()
        val collectionDto = makeItemDto(
            id = itemId,
            type = BaseItemKind.COLLECTION_FOLDER,
            title = "Weekend Playlist",
            childCount = 2,
            runtimeTicks = null,
        )
        val child1 = makeItemDto(
            type = BaseItemKind.VIDEO,
            title = "Clip One",
            year = 2022,
            runtimeTicks = 6_000_000_000L,
        )
        val child2 = makeItemDto(
            type = BaseItemKind.VIDEO,
            title = "Clip Two",
            year = 2023,
            runtimeTicks = 12_000_000_000L,
            played = true,
        )

        coEvery { fakeRepos.media.getItemDetails(itemId) } returns ApiResult.Success(collectionDto)
        coEvery { fakeRepos.media.getLibraryItems(parentId = itemId) } returns ApiResult.Success(listOf(child1, child2))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns "https://img/poster.jpg"

        val vm = CollectionDetailViewModel(fakeRepos.coordinator, updateBus, savedStateHandle(itemId))
        advanceUntilIdle()

        val state = vm.uiState.value as CollectionDetailUiState.Content
        assertTrue(state.stuff.isCollection)
        assertEquals(2, state.items.size)
        assertEquals(2, state.stuff.itemCount)
        assertEquals("Unwatched  ·  10m", state.items[0].subtitle)
        assertEquals("Watched  ·  20m", state.items[1].subtitle)
    }

    @Test
    fun updateBus_refreshItemForCurrentCollection_reloadsContent() = runTest {
        val fakeRepos = FakeCollectionDetailRepositories()
        val itemId = UUID.randomUUID().toString()
        val initialDto = makeItemDto(id = itemId, title = "Initial Title")
        val refreshedDto = makeItemDto(id = itemId, title = "Updated Title")

        coEvery { fakeRepos.media.getItemDetails(itemId) } returnsMany listOf(
            ApiResult.Success(initialDto),
            ApiResult.Success(refreshedDto),
        )
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getPosterCardImageUrl(any()) } returns null

        val vm = CollectionDetailViewModel(fakeRepos.coordinator, updateBus, savedStateHandle(itemId))
        advanceUntilIdle()

        assertEquals("Initial Title", (vm.uiState.value as CollectionDetailUiState.Content).stuff.title)

        updateBus.refreshItem(itemId)
        advanceUntilIdle()

        assertEquals("Updated Title", (vm.uiState.value as CollectionDetailUiState.Content).stuff.title)
    }
}
