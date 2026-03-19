package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import com.rpeters.cinefintv.data.repository.JellyfinStreamRepository
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCount
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeDetailLabel
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.getYearRange
import com.rpeters.cinefintv.utils.isEpisode
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeason
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale

import com.rpeters.cinefintv.utils.formatMs

object DetailMappers {

    fun toHeroModel(
        item: BaseItemDto,
        streamRepository: JellyfinStreamRepository,
        seasons: List<DetailSeasonModel> = emptyList(),
        episodesBySeasonId: Map<String, List<DetailEpisodeModel>> = emptyMap(),
        parentForBackdrop: BaseItemDto? = null,
    ): DetailHeroModel {
        val itemId = item.id.toString()
        val totalEpisodeCount = when {
            item.isSeries() -> seasons.sumOf { it.episodeCount }
            item.isSeason() -> episodesBySeasonId.values.flatten().size
            else -> 0
        }
        val subtitleParts = buildList {
            add(item.getYearRange() ?: item.getYear()?.toString())
            if (!item.isSeries() && !item.isSeason()) {
                add(item.getFormattedDuration())
            }
        }.filterNotNull()

        val metaBadges = buildList {
            if (!item.isSeries() && !item.isSeason() && !item.isMovie() && !item.isEpisode()) {
                add(getMediaTypeLabel(item))
            }
            item.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
            if (item.isSeries() && item.status?.equals("Ended", ignoreCase = true) == true) {
                add("Ended")
            }
        }

        val infoRows = buildInfoRows(item, seasons, totalEpisodeCount)

        val cast = item.people.orEmpty().map { person ->
            DetailPersonModel(
                id = person.id.toString(),
                name = person.name ?: "Unknown",
                role = person.role,
                type = person.type.toString(),
                imageUrl = streamRepository.getImageUrl(
                    itemId = person.id.toString(),
                    imageType = "Primary",
                    tag = person.primaryImageTag
                )
            )
        }

        val chapters = item.chapters.orEmpty().mapIndexed { index, chapter ->
            val positionMs = chapter.startPositionTicks / 10_000L
            DetailChapterModel(
                index = index,
                name = chapter.name ?: "Chapter ${index + 1}",
                positionMs = positionMs,
                imageUrl = streamRepository.getChapterImageUrl(itemId, index),
                subtitle = formatMs(positionMs)
            )
        }

        val streams = item.mediaSources?.firstOrNull()?.mediaStreams ?: item.mediaStreams.orEmpty()
        val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
        val audioStream = streams.firstOrNull { it.type == MediaStreamType.AUDIO }
        val subtitleOptions = streams
            .filter { it.type == MediaStreamType.SUBTITLE }
            .mapNotNull { it.displayTitle ?: it.language ?: it.title }
            .filter { it.isNotBlank() }
            .distinct()

        val technicalDetails = DetailTechnicalDetails(
            videoQuality = toVideoQualityLabel(videoStream?.height),
            videoCodec = videoStream?.codec?.takeIf { it.isNotBlank() }?.uppercase(),
            audioCodec = audioStream?.codec?.takeIf { it.isNotBlank() }?.uppercase(),
            audioType = toAudioTypeLabel(audioStream?.channels),
            language = audioStream?.language?.takeIf { it.isNotBlank() },
            subtitleSummary = subtitleOptions.size.takeIf { it > 0 }?.let { count ->
                if (count == 1) "1 track" else "$count tracks"
            },
            container = item.mediaSources?.firstOrNull()?.container?.takeIf { it.isNotBlank() }?.uppercase(),
            bitrate = item.mediaSources?.firstOrNull()?.bitrate?.let { "${it / 1_000_000} Mbps" },
            framerate = videoStream?.averageFrameRate?.let { "${it.toInt()} fps" } ?: videoStream?.realFrameRate?.let { "${it.toInt()} fps" },
        )

        val isResumable = item.canResume()
        val watchStatus = when {
            item.isWatched() -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) {
            item.getWatchedPercentage().toFloat() / 100f
        } else null
        val unwatchedCount = if (item.isSeries()) {
            item.getUnwatchedEpisodeCount().takeIf { it > 0 }
        } else null

        return DetailHeroModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = subtitleParts.joinToString(" | ").ifBlank { null },
            overview = item.overview?.takeIf { it.isNotBlank() },
            imageUrl = streamRepository.getPosterCardImageUrl(item, parentItem = parentForBackdrop),
            backdropUrl = streamRepository.getBackdropUrlWithFallback(item, parentForBackdrop),
            metaBadges = metaBadges,
            infoRows = infoRows,
            technicalDetails = technicalDetails,
            subtitleOptions = subtitleOptions,
            cast = cast,
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
            unwatchedCount = unwatchedCount,
        )
    }

    fun toEpisodeModel(
        item: BaseItemDto,
        streamRepository: JellyfinStreamRepository
    ): DetailEpisodeModel {
        val episodeNumber = item.indexNumber?.let { "E$it" }
        val episodeMetadata = listOfNotNull(
            item.parentIndexNumber?.let { "S$it" },
            episodeNumber,
            item.getFormattedDuration(),
        ).joinToString(" | ").ifBlank { null }

        val isResumable = item.canResume()
        val watchStatus = when {
            item.isWatched() -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) {
            item.getWatchedPercentage().toFloat() / 100f
        } else null

        return DetailEpisodeModel(
            id = item.id.toString(),
            title = item.getDisplayTitle(),
            subtitle = episodeMetadata,
            overview = item.overview?.takeIf { it.isNotBlank() },
            imageUrl = streamRepository.getLandscapeImageUrl(item),
            canResume = isResumable,
            isWatched = item.isWatched(),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
            unwatchedCount = null,
        )
    }

    private fun buildInfoRows(
        item: BaseItemDto,
        seasons: List<DetailSeasonModel>,
        totalEpisodeCount: Int
    ): List<DetailInfoRowModel> = buildList {
        if (item.isSeries() && seasons.isNotEmpty()) {
            add(DetailInfoRowModel("Seasons", seasons.size.toString(), Icons.Default.Layers))
        }
        if (item.isSeason() && totalEpisodeCount > 0) {
            add(DetailInfoRowModel("Episodes", totalEpisodeCount.toString(), Icons.AutoMirrored.Filled.FormatListBulleted))
        }
        item.getUnwatchedEpisodeCount()
            .takeIf { it > 0 }
            ?.let { add(DetailInfoRowModel("Unwatched", it.toString(), Icons.Default.VisibilityOff)) }
        
        if (item.isEpisode()) {
            item.premiereDate?.let { airDate ->
                val formatted = String.format(Locale.US, "%d-%02d-%02d", airDate.year, airDate.monthValue, airDate.dayOfMonth)
                add(DetailInfoRowModel("Date Aired", formatted, Icons.Default.CalendarToday))
            }
        } else {
            item.getYearRange()?.let { add(DetailInfoRowModel("Year", it, Icons.Default.CalendarToday)) }
        }

        if (!item.isSeries() && !item.isSeason()) {
            item.getFormattedDuration()?.let { add(DetailInfoRowModel("Duration", it, Icons.Default.Timer)) }
        }

        formatCommunityRating(item)?.let { add(DetailInfoRowModel("Rating", it, Icons.Default.Star)) }

        item.genres.orEmpty()
            .take(3)
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
            ?.let { add(DetailInfoRowModel("Genres", it, Icons.Default.Category)) }
        
        item.studios.orEmpty()
            .firstOrNull()
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { add(DetailInfoRowModel("Studio", it, Icons.Default.Domain)) }
    }

    fun buildSeasonSubtitle(item: BaseItemDto, loadedEpisodeCount: Int): String? {
        val episodeCount = (loadedEpisodeCount.takeIf { it > 0 } ?: (item.childCount ?: 0))
            .takeIf { it > 0 }
            ?.let { count -> if (count == 1) "1 episode" else "$count episodes" }
        val unwatchedCount = item.getUnwatchedEpisodeDetailLabel()

        return listOfNotNull(episodeCount, unwatchedCount)
            .joinToString(" | ")
            .ifBlank { null }
    }

    private fun formatCommunityRating(item: BaseItemDto): String? {
        val rating = (item.communityRating as? Number)?.toDouble() ?: return null
        if (rating <= 0.0) return null
        return String.format(Locale.US, "%.1f/10", rating)
    }

    private fun getMediaTypeLabel(item: BaseItemDto): String =
        when (item.type) {
            BaseItemKind.MOVIE -> "Movie"
            BaseItemKind.SERIES -> "TV Show"
            BaseItemKind.SEASON -> "Season"
            BaseItemKind.EPISODE -> "Episode"
            BaseItemKind.AUDIO -> "Track"
            BaseItemKind.MUSIC_ALBUM -> "Album"
            BaseItemKind.MUSIC_ARTIST -> "Artist"
            else -> "Library Item"
        }

    fun toVideoQualityLabel(height: Int?): String? = when {
        height == null -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "${height}p"
    }

    fun toAudioTypeLabel(channels: Int?): String? = when {
        channels == null || channels <= 0 -> null
        channels >= 8 -> "7.1"
        channels >= 6 -> "5.1"
        channels >= 2 -> "Stereo"
        else -> "Mono"
    }
}
