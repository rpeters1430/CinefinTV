package com.rpeters.cinefintv.utils

import com.rpeters.cinefintv.R
import com.rpeters.cinefintv.core.constants.Constants
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * ✅ PHASE 3: Enhanced extension functions with centralized constants
 */

/**
 * Safely converts community rating to Double with fallback to 0.0
 * Eliminates code duplication across multiple screen files
 */
fun BaseItemDto.getRatingAsDouble(): Double =
    (communityRating as? Number)?.toDouble() ?: 0.0

/**
 * ✅ PHASE 3: Enhanced rating checks using centralized constants
 */
fun BaseItemDto.hasHighRating(): Boolean =
    getRatingAsDouble() >= Constants.Rating.HIGH_RATING_THRESHOLD

fun BaseItemDto.hasExcellentRating(): Boolean =
    getRatingAsDouble() >= Constants.Rating.EXCELLENT_RATING_THRESHOLD

fun BaseItemDto.hasGoodRating(): Boolean =
    getRatingAsDouble() >= Constants.Rating.GOOD_RATING_THRESHOLD

fun BaseItemDto.getRatingCategory(): RatingCategory {
    val rating = getRatingAsDouble()
    return when {
        rating >= Constants.Rating.EXCELLENT_RATING_THRESHOLD -> RatingCategory.EXCELLENT
        rating >= Constants.Rating.HIGH_RATING_THRESHOLD -> RatingCategory.HIGH
        rating >= Constants.Rating.GOOD_RATING_THRESHOLD -> RatingCategory.GOOD
        rating >= Constants.Rating.AVERAGE_RATING_THRESHOLD -> RatingCategory.AVERAGE
        else -> RatingCategory.POOR
    }
}

/**
 * ✅ PHASE 3: Rating categories for better UX
 */
enum class RatingCategory(val displayName: String, val color: String) {
    EXCELLENT("Excellent", "#4CAF50"),
    HIGH("High", "#8BC34A"),
    GOOD("Good", "#FFC107"),
    AVERAGE("Average", "#FF9800"),
    POOR("Poor", "#F44336"),
}

/**
 * ✅ PHASE 3: Enhanced media type extensions
 */
fun BaseItemDto.isMovie(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE
fun BaseItemDto.isSeries(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES
fun BaseItemDto.isSeason(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.SEASON
fun BaseItemDto.isEpisode(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE
fun BaseItemDto.isMusic(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.AUDIO
fun BaseItemDto.isPhoto(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.PHOTO
fun BaseItemDto.isPerson(): Boolean = type == org.jellyfin.sdk.model.api.BaseItemKind.PERSON

/**
 * Maps BaseItemKind to the standard CamelCase strings used for navigation in NavGraph.
 */
fun BaseItemDto.getItemTypeString(): String {
    return when (type) {
        org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> "Movie"
        org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> "Series"
        org.jellyfin.sdk.model.api.BaseItemKind.SEASON -> "Season"
        org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> "Episode"
        org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER -> "CollectionFolder"
        org.jellyfin.sdk.model.api.BaseItemKind.VIDEO -> "Video"
        org.jellyfin.sdk.model.api.BaseItemKind.BOX_SET -> "BoxSet"
        org.jellyfin.sdk.model.api.BaseItemKind.USER_VIEW -> "UserView"
        org.jellyfin.sdk.model.api.BaseItemKind.AUDIO -> "Audio"
        org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM -> "MusicAlbum"
        org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST -> "MusicArtist"
        else -> type?.toString() ?: "Unknown"
    }
}

/**
 * ✅ PHASE 3: Enhanced display utilities
 */
fun BaseItemDto.getDisplayTitle(): String = name ?: AppResources.getString(R.string.unknown)

fun BaseItemDto.getYear(): Int? = productionYear

fun BaseItemDto.getYearRange(): String? {
    val startYear = getYear() ?: return null
    if (!isSeries()) return startYear.toString()
    
    val endYear = endDate?.year
    val isEnded = status?.equals("Ended", ignoreCase = true) == true
    
    return when {
        endYear != null -> "$startYear - $endYear"
        isEnded -> "$startYear - Ended"
        else -> "$startYear - Present"
    }
}

fun BaseItemDto.getFormattedDuration(): String? {
    val ticks = runTimeTicks ?: return null
    val totalSeconds = ticks / 10_000_000 // Convert from ticks to seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}

/**
 * ✅ PHASE 3: Watch status utilities
 */
fun BaseItemDto.getWatchedPercentage(): Double {
    userData?.playedPercentage?.let { return it }

    val positionTicks = userData?.playbackPositionTicks
    val runtimeTicks = runTimeTicks

    return if (positionTicks != null && runtimeTicks != null && runtimeTicks > 0) {
        val percentage = (positionTicks.toDouble() / runtimeTicks.toDouble()) * 100.0
        percentage.coerceIn(0.0, 100.0)
    } else {
        0.0
    }
}

fun BaseItemDto.isWatched(): Boolean {
    val watchedPercentage = getWatchedPercentage()
    return userData?.played == true || watchedPercentage >= Constants.Playback.WATCHED_THRESHOLD_PERCENT
}

fun BaseItemDto.isPartiallyWatched(): Boolean {
    val percentage = getWatchedPercentage()
    return percentage > 0.0 && percentage < Constants.Playback.WATCHED_THRESHOLD_PERCENT
}

fun BaseItemDto.canResume(): Boolean {
    val percentage = getWatchedPercentage()
    return percentage > Constants.Playback.RESUME_THRESHOLD_PERCENT &&
        percentage < Constants.Playback.WATCHED_THRESHOLD_PERCENT
}

/**
 * ✅ PHASE 3: Enhanced Watch status utilities for TV Shows
 */
fun BaseItemDto.getUnwatchedEpisodeCount(): Int {
    return when {
        type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
            // Use userData.unplayedItemCount if available, fallback to calculation
            userData?.unplayedItemCount ?: run {
                val totalCount = childCount ?: 0
                val playedCount = userData?.playedPercentage?.let { percentage ->
                    // Log to verify playedPercentage range from Jellyfin API
                    SecureLogger.d(
                        "Extensions",
                        "Series '$name': playedPercentage=$percentage, totalCount=$totalCount",
                    )
                    // Assumes playedPercentage represents the fraction of episodes completed across the series
                    when {
                        percentage >= Constants.Playback.WATCHED_THRESHOLD_PERCENT -> totalCount
                        percentage > 0 -> (totalCount * (percentage / 100.0)).toInt()
                        else -> 0
                    }
                } ?: 0
                maxOf(0, totalCount - playedCount)
            }
        }
        else -> 0
    }
}

fun BaseItemDto.getUnwatchedEpisodeCardLabel(): String? {
    if (!isSeries()) return null

    val unwatchedCount = getUnwatchedEpisodeCount()
    return when {
        unwatchedCount <= 0 -> null
        unwatchedCount == 1 -> "1 unwatched episode"
        else -> "$unwatchedCount unwatched episodes"
    }
}

fun BaseItemDto.getSeriesCardDetailLine(): String? {
    if (!isSeries()) return null

    val year = getYearRange()
    val episodeCount = childCount?.takeIf { it > 0 }?.let { count ->
        if (count == 1) "1 episode" else "$count episodes"
    }

    return listOfNotNull(year, episodeCount).joinToString("  ·  ").ifBlank { null }
}

fun BaseItemDto.getEpisodeCode(): String? {
    if (!isEpisode()) return null

    val season = parentIndexNumber
    val episode = indexNumber

    return when {
        season != null && episode != null -> "S${season} · E${episode}"
        episode != null -> "Episode $episode"
        else -> null
    }
}

fun BaseItemDto.getEpisodeCardDetailLine(): String? {
    if (!isEpisode()) return null

    val series = seriesName?.takeIf { it.isNotBlank() }
    val episodeCode = getEpisodeCode()

    return listOfNotNull(series, episodeCode).joinToString("  ·  ").ifBlank { null }
}

fun BaseItemDto.getUnwatchedEpisodeDetailLabel(): String? {
    if (!isSeries() && !isSeason()) return null

    val unwatchedCount = userData?.unplayedItemCount ?: return null
    return when {
        unwatchedCount <= 0 -> null
        unwatchedCount == 1 -> "1 episode unwatched"
        else -> "$unwatchedCount episodes unwatched"
    }
}

fun BaseItemDto.hasUnwatchedEpisodes(): Boolean {
    return when {
        type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> getUnwatchedEpisodeCount() > 0
        else -> false
    }
}

fun BaseItemDto.isCompletelyWatched(): Boolean {
    return when {
        type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
            // Series is completely watched if userData.played is true OR unplayedItemCount is 0
            val seriesPercentage = userData?.playedPercentage ?: 0.0
            userData?.played == true ||
                seriesPercentage >= Constants.Playback.WATCHED_THRESHOLD_PERCENT ||
                getUnwatchedEpisodeCount() == 0
        }
        type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> isWatched()
        type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> isWatched()
        else -> isWatched()
    }
}

fun BaseItemDto.getNextUpInfo(): String? {
    return when {
        type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES && hasUnwatchedEpisodes() -> {
            val unwatchedCount = getUnwatchedEpisodeCount()
            when {
                unwatchedCount == 1 -> "1 new episode"
                unwatchedCount > 99 -> "99+ new"
                else -> "$unwatchedCount new"
            }
        }
        else -> null
    }
}

/**
 * ✅ PHASE 3: Color utilities
 */
fun androidx.compose.ui.graphics.Color.coerceAlpha(alpha: Float): androidx.compose.ui.graphics.Color = 
    this.copy(alpha = alpha)

/**
 * Formats milliseconds into a time string (H:MM:SS or M:SS).
 */
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/**
 * ✅ PHASE 3: Enhanced key generation for lists
 */
fun BaseItemDto.getItemKey(): String =
    generateItemKey(type.toString(), id.toString())

/**
 * ✅ PHASE 3: Content key generation utility
 */
fun generateItemKey(prefix: String, id: String): String = "${prefix}_$id"
