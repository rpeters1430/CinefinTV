package com.rpeters.cinefintv.ui.player

data class TrackOption(
    val id: String,
    val label: String,
    val language: String?,
    val streamIndex: Int?,
)

data class ChapterMarker(val positionMs: Long, val name: String?)

data class SkipRange(val startMs: Long, val endMs: Long?)

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
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedAudioTrack: TrackOption? = null,
    val selectedSubtitleTrack: TrackOption? = null,
    val playbackSpeed: Float = 1.0f,
    val chapters: List<ChapterMarker> = emptyList(),
    val introSkipRange: SkipRange? = null,
    val creditsSkipRange: SkipRange? = null,
    val isLoading: Boolean = true,
    val isRetrying: Boolean = false,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
)
