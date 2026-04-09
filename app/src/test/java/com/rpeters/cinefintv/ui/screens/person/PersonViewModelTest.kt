package com.rpeters.cinefintv.ui.screens.person

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
class PersonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun init_loadsPersonDetailsAndMedia() = runTest {
        val personId = UUID.randomUUID().toString()
        val fakeRepositories = FakeHomeRepositories()
        val personItem = mockBaseItemDto(personId, "Test Actor", BaseItemKind.PERSON)

        coEvery { fakeRepositories.media.getPersonDetails(personId) } returns ApiResult.Success(personItem)
        coEvery { fakeRepositories.media.getItemsByPerson(personId, any()) } returns ApiResult.Success(emptyList())

        every { fakeRepositories.stream.getImageUrl(any(), any(), any()) } returns "https://img/person.jpg"
        every { fakeRepositories.stream.getPosterCardImageUrl(any(), any()) } returns "https://img/poster.jpg"

        val viewModel = PersonViewModel(fakeRepositories.coordinator)
        viewModel.init(personId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PersonUiState.Content)
        val content = state as PersonUiState.Content
        assertEquals("Test Actor", content.person.name)
    }

    private fun mockBaseItemDto(id: String, name: String, kind: BaseItemKind): BaseItemDto {
        val item: BaseItemDto = mockk(relaxed = true)
        every { item.id } returns UUID.fromString(id)
        every { item.name } returns name
        every { item.type } returns kind
        return item
    }
}
