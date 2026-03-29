package com.rpeters.cinefintv.ui.player

import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferences
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.data.preferences.VideoSeekIncrement
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val streamIndex: Int?,
)

data class ChapterMarker(val positionMs: Long, val name: String?)

data class SkipRange(val startMs: Long, val endMs: Long?)

@Serializable
data class TrickplayManifest(
    @SerialName("Version") val version: String,
    @SerialName("Width") val width: Int,
    @SerialName("Height") val height: Int,
    @SerialName("Interval") val intervalMs: Int,
    @SerialName("Tiles") val tiles: List<TrickplayTile>
)

@Serializable
data class TrickplayTile(
    @SerialName("Image") val image: String,
    @SerialName("RowCount") val rowCount: Int,
    @SerialName("ColumnCount") val columnCount: Int
)

object TrickplayTileBitmapCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(tileUrl: String): ImageBitmap? = cache[tileUrl]

    fun put(tileUrl: String, bitmap: ImageBitmap) {
        cache.putIfAbsent(tileUrl, bitmap)
    }
}

data class PlayerContentItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
)

sealed class PlayerContentRow {
    data class Chapters(val chapters: List<ChapterMarker>) : PlayerContentRow()
    data class Episodes(val items: List<PlayerContentItem>, val currentItemId: String) : PlayerContentRow()
    data class Recommendations(val items: List<PlayerContentItem>) : PlayerContentRow()
}

data class PlayerUiState(
    val itemId: String = "",
    val title: String = "Player",
    val logoUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val streamUrl: String? = null,
    val savedPlaybackPositionMs: Long = 0L,
    val shouldShowResumeDialog: Boolean = false,
    val isEpisodicContent: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val nextEpisodeThumbnailUrl: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioTrack: TrackOption? = null,
    val selectedSubtitleTrack: TrackOption? = null,
    val transcodingQuality: TranscodingQuality = TranscodingQuality.AUTO,
    val videoSeekIncrement: VideoSeekIncrement = VideoSeekIncrement.TEN_SECONDS,
    val playbackSpeed: Float = 1.0f,
    val subtitleAppearance: SubtitleAppearancePreferences = SubtitleAppearancePreferences.DEFAULT,
    val isHdrPlayback: Boolean = false,
    val chapters: List<ChapterMarker> = emptyList(),
    val introSkipRange: SkipRange? = null,
    val creditsSkipRange: SkipRange? = null,
    val trickplayManifest: TrickplayManifest? = null,
    val trickplayBaseUrl: String? = null,
    val contentRow: PlayerContentRow? = null,
    val isLoading: Boolean = true,
    val isRetrying: Boolean = false,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
)
