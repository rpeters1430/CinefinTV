package com.rpeters.cinefintv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerLifecycleTest {

    @Test
    fun nextPlaybackCompletionTarget_returnsNextEpisodeWhenAutoplayIsEnabled() {
        val uiState = PlayerUiState(
            isEpisodicContent = true,
            autoPlayNextEpisode = true,
            nextEpisodeId = "episode-2",
        )

        assertEquals("episode-2", nextPlaybackCompletionTarget(uiState))
    }

    @Test
    fun nextPlaybackCompletionTarget_returnsNullWhenAutoplayIsDisabled() {
        val uiState = PlayerUiState(
            isEpisodicContent = true,
            autoPlayNextEpisode = false,
            nextEpisodeId = "episode-2",
        )

        assertNull(nextPlaybackCompletionTarget(uiState))
    }

    @Test
    fun nextPlaybackCompletionTarget_returnsNullWhenNoNextEpisodeExists() {
        val uiState = PlayerUiState(
            isEpisodicContent = true,
            autoPlayNextEpisode = true,
            nextEpisodeId = null,
        )

        assertNull(nextPlaybackCompletionTarget(uiState))
    }

    @Test
    fun nextPlaybackCompletionTarget_advancesThroughPlaylistQueueWhenNotEpisodic() {
        val uiState = PlayerUiState(
            itemId = "video-1",
            isEpisodicContent = false,
            queueIds = listOf("video-1", "video-2", "video-3"),
        )

        assertEquals("video-2", nextPlaybackCompletionTarget(uiState))
    }

    @Test
    fun nextPlaybackCompletionTarget_returnsNullAtEndOfPlaylistQueue() {
        val uiState = PlayerUiState(
            itemId = "video-3",
            isEpisodicContent = false,
            queueIds = listOf("video-1", "video-2", "video-3"),
        )

        assertNull(nextPlaybackCompletionTarget(uiState))
    }

    @Test
    fun nextInQueue_returnsNullWhenCurrentItemNotInQueue() {
        assertNull(nextInQueue(listOf("a", "b"), "not-in-queue"))
    }

    @Test
    fun nextInQueue_returnsFollowingId() {
        assertEquals("b", nextInQueue(listOf("a", "b", "c"), "a"))
    }
}
