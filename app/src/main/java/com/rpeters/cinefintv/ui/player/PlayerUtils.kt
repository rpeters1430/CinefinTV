package com.rpeters.cinefintv.ui.player

import java.util.Locale

/**
 * Formats milliseconds into a time string (H:MM:SS or M:SS).
 */
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/**
 * Constants for the player.
 */
object PlayerConstants {
    const val POSITION_SAVE_INTERVAL_MS = 10_000L
    const val PROGRESS_UPDATE_INTERVAL_ACTIVE_MS = 250L
    const val PROGRESS_UPDATE_INTERVAL_IDLE_MS = 1000L
    const val CONTROLS_HIDE_DELAY_MS = 5_000L
    const val NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS = 15_000L
    const val EXIT_TRANSITION_DURATION_MS = 180L
}

internal fun shouldShowNextEpisodeCard(
    uiState: PlayerUiState,
    positionMs: Long,
    durationMs: Long,
): Boolean {
    if (!uiState.isEpisodicContent || uiState.nextEpisodeId.isNullOrBlank()) {
        return false
    }

    val inCredits = isInSkipRange(
        positionMs = positionMs,
        range = uiState.creditsSkipRange,
    )
    if (inCredits) {
        return true
    }

    if (durationMs <= 0L) {
        return false
    }

    val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
    return remainingMs <= PlayerConstants.NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS
}

internal fun isInSkipRange(
    positionMs: Long,
    range: SkipRange?,
): Boolean {
    range ?: return false
    if (positionMs < range.startMs) return false

    val endMs = range.endMs ?: return true
    if (endMs <= range.startMs) return false

    return positionMs < endMs
}
