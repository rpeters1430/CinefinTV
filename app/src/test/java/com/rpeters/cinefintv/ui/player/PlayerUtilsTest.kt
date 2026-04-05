package com.rpeters.cinefintv.ui.player

import com.rpeters.cinefintv.ui.player.PlayerConstants.NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUtilsTest {

    @Test
    fun shouldShowNextEpisodeCard_returnsTrueNearEndOfEpisode() {
        val uiState = PlayerUiState(
            isEpisodicContent = true,
            nextEpisodeId = "episode-2",
        )

        val result = shouldShowNextEpisodeCard(
            uiState = uiState,
            positionMs = 120_000L - NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS,
            durationMs = 120_000L,
        )

        assertTrue(result)
    }

    @Test
    fun shouldShowNextEpisodeCard_returnsTrueWhenCreditsBegin() {
        val uiState = PlayerUiState(
            isEpisodicContent = true,
            nextEpisodeId = "episode-2",
            creditsSkipRange = SkipRange(startMs = 90_000L, endMs = 120_000L),
        )

        val result = shouldShowNextEpisodeCard(
            uiState = uiState,
            positionMs = 95_000L,
            durationMs = 120_000L,
        )

        assertTrue(result)
    }

    @Test
    fun shouldShowNextEpisodeCard_returnsFalseWithoutNextEpisode() {
        val uiState = PlayerUiState(
            isEpisodicContent = true,
            nextEpisodeId = null,
            creditsSkipRange = SkipRange(startMs = 90_000L, endMs = 120_000L),
        )

        val result = shouldShowNextEpisodeCard(
            uiState = uiState,
            positionMs = 95_000L,
            durationMs = 120_000L,
        )

        assertFalse(result)
    }
}
