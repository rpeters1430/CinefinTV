package com.rpeters.cinefintv.ui.screens.music

import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeMusicRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun buildPlaybackRequest_returnsSelectedTrackWithAlbumQueue() = runTest {
        val fakeRepositories = FakeMusicRepositories()
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MUSIC_ALBUM, limit = 50)
        } returns ApiResult.Success(emptyList())

        val viewModel = MusicViewModel(fakeRepositories.coordinator)
        advanceUntilIdle()

        val selectedTrack = mockTrack("Track 2", UUID.randomUUID())
        val firstTrack = mockTrack("Track 1", UUID.randomUUID())
        val thirdTrack = mockTrack("Track 3", UUID.randomUUID())

        val request = viewModel.buildPlaybackRequest(
            selectedTrack = selectedTrack,
            albumTracks = listOf(firstTrack, selectedTrack, thirdTrack),
        )

        assertEquals(selectedTrack.id.toString(), request?.trackId)
        assertEquals(
            listOf(
                firstTrack.id.toString(),
                selectedTrack.id.toString(),
                thirdTrack.id.toString(),
            ),
            request?.queueIds,
        )
    }

    @Test
    fun buildPlaybackRequest_withEmptyAlbumTracksFallsBackToSelectedTrack() = runTest {
        val fakeRepositories = FakeMusicRepositories()
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MUSIC_ALBUM, limit = 50)
        } returns ApiResult.Success(emptyList())

        val viewModel = MusicViewModel(fakeRepositories.coordinator)
        advanceUntilIdle()

        val selectedTrack = mockTrack("Solo Track", UUID.randomUUID())

        val request = viewModel.buildPlaybackRequest(
            selectedTrack = selectedTrack,
            albumTracks = emptyList(),
        )

        assertEquals(selectedTrack.id.toString(), request?.trackId)
        assertEquals(listOf(selectedTrack.id.toString()), request?.queueIds)
    }

    @Test
    fun buildPlaybackRequest_usesAlbumQueueIdsEvenWhenSelectedTrackInstanceDiffers() = runTest {
        val fakeRepositories = FakeMusicRepositories()
        coEvery {
            fakeRepositories.media.getRecentlyAddedByType(BaseItemKind.MUSIC_ALBUM, limit = 50)
        } returns ApiResult.Success(emptyList())

        val viewModel = MusicViewModel(fakeRepositories.coordinator)
        advanceUntilIdle()

        val selectedTrack = mockTrack("Selected", UUID.randomUUID())
        val validAlbumTrack = mockTrack("Album Track", UUID.randomUUID())
        val duplicateSelectedTrack = mockTrack("Selected Duplicate", selectedTrack.id)

        val request = viewModel.buildPlaybackRequest(
            selectedTrack = selectedTrack,
            albumTracks = listOf(validAlbumTrack, duplicateSelectedTrack),
        )

        assertEquals(selectedTrack.id.toString(), request?.trackId)
        assertEquals(
            listOf(validAlbumTrack.id.toString(), selectedTrack.id.toString()),
            request?.queueIds,
        )
    }

    private fun mockTrack(name: String, id: UUID): BaseItemDto {
        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
        }
    }
}
