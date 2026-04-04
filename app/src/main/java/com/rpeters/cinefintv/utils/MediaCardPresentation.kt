package com.rpeters.cinefintv.utils

import com.rpeters.cinefintv.ui.components.WatchStatus
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

data class MediaCardPresentation(
    val subtitle: String?,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
    val unwatchedCount: Int?,
)

fun BaseItemDto.toMediaCardPresentation(): MediaCardPresentation {
    val watchedPercentage = getWatchedPercentage()
    val resumable = canResume()
    val watched = isWatched()

    return MediaCardPresentation(
        subtitle = buildMediaCardSubtitle(),
        watchStatus = when {
            watched -> WatchStatus.WATCHED
            resumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        },
        playbackProgress = if (resumable) watchedPercentage.toFloat() / 100f else null,
        unwatchedCount = if (isSeries()) getUnwatchedEpisodeCount().takeIf { it > 0 } else null,
    )
}

private fun BaseItemDto.buildMediaCardSubtitle(): String? {
    if (type == BaseItemKind.COLLECTION_FOLDER || type == BaseItemKind.USER_VIEW) {
        return null
    }

    val playbackLabel = getMediaPlaybackStatusLabel()
    val detailLabel = when {
        isEpisode() -> getEpisodeCardDetailLine()
        isSeries() -> getSeriesCardDetailLine()
            ?: getYear()?.toString()
            ?: type.toString().replace('_', ' ')
        type == BaseItemKind.VIDEO -> getFormattedDuration()
            ?: getYear()?.toString()
            ?: getItemTypeString()
        type == BaseItemKind.MOVIE -> getYear()?.toString()
            ?: getFormattedDuration()
            ?: getItemTypeString()
        else -> getYear()?.toString()
            ?: getFormattedDuration()
            ?: getItemTypeString()
    }?.takeIf { it.isNotBlank() }

    return listOfNotNull(playbackLabel, detailLabel)
        .distinct()
        .joinToString("  ·  ")
        .ifBlank { null }
}

private fun BaseItemDto.getMediaPlaybackStatusLabel(): String? {
    return when {
        canResume() -> getResumeTimeLeftLabel() ?: "${getWatchedPercentage().toInt()}% watched"
        else -> null
    }
}

private fun BaseItemDto.isPlayableVideoType(): Boolean {
    return when (type) {
        BaseItemKind.MOVIE,
        BaseItemKind.EPISODE,
        BaseItemKind.VIDEO,
        BaseItemKind.SERIES,
        BaseItemKind.SEASON,
        -> true
        else -> false
    }
}

private fun BaseItemDto.getResumeTimeLeftLabel(): String? {
    val runtimeTicks = runTimeTicks ?: return null
    if (runtimeTicks <= 0) return null

    val remainingTicks = (runtimeTicks * (1.0 - (getWatchedPercentage() / 100.0))).toLong()
        .coerceAtLeast(0L)
    val totalSeconds = remainingTicks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "< 1m left"
    }
}
