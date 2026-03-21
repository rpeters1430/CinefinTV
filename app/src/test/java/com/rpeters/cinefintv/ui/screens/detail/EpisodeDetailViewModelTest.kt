package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.FakeEpisodeDetailRepositories
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeSavedStateHandle(episodeId: String) =
        SavedStateHandle(mapOf("itemId" to episodeId))

    private fun makeBaseItem(
        id: String = UUID.randomUUID().toString(),
        videoCodec: String? = "hevc",
        videoWidth: Int? = 1920,
        videoHeight: Int? = 1080,
        videoBitRate: Int? = 8000000,
        audioCodec: String? = "eac3",
        audioChannels: Int? = 6,
        audioLanguage: String? = "eng",
        audioIsDefault: Boolean = true,
        container: String? = "mkv",
    ): BaseItemDto {
        val videoStream = mockk<MediaStream>(relaxed = true) {
            every { type } returns MediaStreamType.VIDEO
            every { codec } returns videoCodec
            every { width } returns videoWidth
            every { height } returns videoHeight
            every { bitRate } returns videoBitRate
        }
        val audioStream = mockk<MediaStream>(relaxed = true) {
            every { type } returns MediaStreamType.AUDIO
            every { codec } returns audioCodec
            every { channels } returns audioChannels
            every { language } returns audioLanguage
            every { isDefault } returns audioIsDefault
            every { profile } returns null
        }
        val sourceContainer = container
        val source = mockk<MediaSourceInfo>(relaxed = true) {
            every { this@mockk.container } returns sourceContainer
            every { mediaStreams } returns listOf(videoStream, audioStream)
        }
        return mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(id)
            every { mediaSources } returns listOf(source)
            every { chapters } returns null
        }
    }

    @Test
    fun load_parsesVideoResolutionCorrectly() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(makeBaseItem(id = episodeId, videoWidth = 1920, videoHeight = 1080))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any(), any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        assertEquals("1080p", state.mediaDetail?.video?.resolution)
    }

    @Test
    fun load_parsesVideoCodecHEVC() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(makeBaseItem(id = episodeId, videoCodec = "hevc"))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any(), any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        assertEquals("HEVC", state.mediaDetail?.video?.codec)
    }

    @Test
    fun load_parsesAudioStreamChannels() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(makeBaseItem(id = episodeId, audioChannels = 6, audioCodec = "eac3"))
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null
        every { fakeRepos.stream.getImageUrl(any(), any(), any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        val audio = state.mediaDetail?.audioStreams?.firstOrNull()
        assertNotNull(audio)
        assertEquals("5.1", audio?.channels)
        assertEquals("EAC3", audio?.codec)
    }

    @Test
    fun load_whenNoMediaSources_mediaDetailIsNull() = runTest {
        val fakeRepos = FakeEpisodeDetailRepositories()
        val episodeId = UUID.randomUUID().toString()
        val itemWithNoSources = mockk<BaseItemDto>(relaxed = true) {
            every { this@mockk.id } returns UUID.fromString(episodeId)
            every { mediaSources } returns null
            every { chapters } returns null
        }
        coEvery { fakeRepos.media.getEpisodeDetails(episodeId) } returns
            ApiResult.Success(itemWithNoSources)
        every { fakeRepos.stream.getBackdropUrl(any()) } returns null

        val vm = EpisodeDetailViewModel(fakeRepos.coordinator, makeSavedStateHandle(episodeId))
        advanceUntilIdle()

        val state = vm.uiState.value as EpisodeDetailUiState.Content
        assertNull(state.mediaDetail)
    }
}
