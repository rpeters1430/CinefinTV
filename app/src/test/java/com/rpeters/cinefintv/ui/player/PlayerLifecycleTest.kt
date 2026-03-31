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
}
