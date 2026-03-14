package com.rpeters.cinefintv.ui.screens.detail

import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.isEpisode
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeason
import com.rpeters.cinefintv.utils.isSeries
import org.jellyfin.sdk.model.api.BaseItemDto

data class PlaybackTarget(
    val id: String,
    val label: String,
)

object DetailPlaybackResolver {

    fun resolvePlaybackTarget(
        item: BaseItemDto,
        episodesBySeasonId: Map<String, List<DetailEpisodeModel>>,
    ): PlaybackTarget? {
        return when {
            item.isMovie() || item.isEpisode() -> PlaybackTarget(
                id = item.id.toString(),
                label = if (item.canResume()) "Resume" else "Play",
            )
            item.isSeason() -> {
                val firstEpisode = episodesBySeasonId.values.flatten().firstOrNull() ?: return null
                PlaybackTarget(firstEpisode.id, "Play Season")
            }
            item.isSeries() -> {
                val episodes = episodesBySeasonId[item.id.toString()].orEmpty()
                val targetEpisode = episodes.firstOrNull { it.canResume }
                    ?: episodes.firstOrNull { it.watchStatus != com.rpeters.cinefintv.ui.components.WatchStatus.WATCHED }
                    ?: episodes.firstOrNull()
                    ?: return null
                PlaybackTarget(
                    id = targetEpisode.id,
                    label = if (targetEpisode.canResume) "Resume Show" else "Play Show",
                )
            }
            else -> null
        }
    }
}
