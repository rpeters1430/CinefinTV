package com.rpeters.cinefintv.utils

import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCardPresentationTest {

    @Test
    fun toMediaCardPresentation_forResumableVideo_showsTimeLeftStatusFirst() {
        val item = mediaItem(
            type = BaseItemKind.VIDEO,
            title = "Concert Cut",
            runtimeTicks = 6_000_000_000L,
            playedPercentage = 40.0,
        )

        val presentation = item.toMediaCardPresentation()

        assertEquals("6m left  ·  10m", presentation.subtitle)
    }

    @Test
    fun toMediaCardPresentation_forWatchedMovie_showsYearOnly() {
        val item = mediaItem(
            type = BaseItemKind.MOVIE,
            title = "Feature Film",
            year = 2024,
            played = true,
            playedPercentage = 100.0,
        )

        val presentation = item.toMediaCardPresentation()

        assertEquals("2024", presentation.subtitle)
    }

    @Test
    fun toMediaCardPresentation_forUnwatchedEpisode_showsEpisodeContextOnly() {
        val item = mediaItem(
            type = BaseItemKind.EPISODE,
            title = "Episode 2",
            seriesName = "Example Show",
            seasonNumber = 1,
            episodeNumber = 2,
        )

        val presentation = item.toMediaCardPresentation()

        assertEquals("Example Show  ·  S1 · E2", presentation.subtitle)
    }

    private fun mediaItem(
        type: BaseItemKind,
        title: String,
        year: Int? = null,
        runtimeTicks: Long? = null,
        played: Boolean = false,
        playedPercentage: Double = 0.0,
        seriesName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): BaseItemDto {
        val userData = mockk<UserItemDataDto>(relaxed = true) {
            every { this@mockk.played } returns played
            every { this@mockk.playedPercentage } returns playedPercentage
            every { this@mockk.playbackPositionTicks } returns 0L
            every { this@mockk.unplayedItemCount } returns 0
        }

        return mockk(relaxed = true) {
            every { id } returns UUID.randomUUID()
            every { name } returns title
            every { this@mockk.type } returns type
            every { productionYear } returns year
            every { this@mockk.runTimeTicks } returns runtimeTicks
            every { this@mockk.userData } returns userData
            every { this@mockk.seriesName } returns seriesName
            every { parentIndexNumber } returns seasonNumber
            every { indexNumber } returns episodeNumber
            every { childCount } returns null
        }
    }
}
