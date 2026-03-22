package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getEpisodeCode
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.isWatched
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.canResume
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import javax.inject.Inject

data class EpisodeDetailModel(
    val id: String,
    val title: String,
    val seriesName: String?,
    val seriesId: String?,
    val seasonId: String?,
    val episodeCode: String?,
    val year: Int?,
    val duration: String?,
    val overview: String?,
    val backdropUrl: String?,
    val isWatched: Boolean,
    val playbackProgress: Float?,
)

data class ChapterModel(
    val id: String,
    val name: String,
    val positionMs: Long,
    val imageUrl: String?,
)

data class VideoStreamInfo(
    val resolution: String?,
    val codec: String?,
    val hdr: String?,
    val bitrateKbps: Int?,
)

data class AudioStreamInfo(
    val codec: String,
    val channels: String?,
    val language: String?,
    val isDefault: Boolean,
)

data class MediaDetailModel(
    val container: String?,
    val video: VideoStreamInfo?,
    val audioStreams: List<AudioStreamInfo>,
)

sealed class EpisodeDetailUiState {
    data object Loading : EpisodeDetailUiState()
    data class Error(val message: String) : EpisodeDetailUiState()
    data class Content(
        val episode: EpisodeDetailModel,
        val chapters: List<ChapterModel>,
        val mediaDetail: MediaDetailModel?,
    ) : EpisodeDetailUiState()
}

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val episodeId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<EpisodeDetailUiState>(EpisodeDetailUiState.Loading)
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    init {
        if (episodeId.isBlank()) {
            _uiState.value = EpisodeDetailUiState.Error("Invalid episode ID")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = EpisodeDetailUiState.Loading

            val episodeResult = repositories.media.getEpisodeDetails(episodeId)

            if (episodeResult is ApiResult.Success) {
                val episodeDto = episodeResult.data
                
                val chapters = episodeDto.chapters?.mapIndexed { index, chapter ->
                    ChapterModel(
                        id = "chapter_$index",
                        name = chapter.name ?: "Chapter ${index + 1}",
                        positionMs = (chapter.startPositionTicks ?: 0L) / 10000L,
                        imageUrl = repositories.stream.getImageUrl(
                            itemId = episodeId,
                            imageType = "Chapter",
                            tag = chapter.imageTag
                        )
                    )
                } ?: emptyList()

                _uiState.value = EpisodeDetailUiState.Content(
                    episode = episodeDto.toEpisodeDetailModel(),
                    chapters = chapters,
                    mediaDetail = episodeDto.toMediaDetailModel(),
                )
            } else if (episodeResult is ApiResult.Error) {
                _uiState.value = EpisodeDetailUiState.Error(episodeResult.message)
            }
        }
    }

    fun refreshWatchStatus() {
        _uiState.value as? EpisodeDetailUiState.Content ?: return
        viewModelScope.launch {
            when (val result = repositories.media.getEpisodeDetails(episodeId)) {
                is ApiResult.Success -> {
                    val dto = result.data
                    // Re-read state after the suspension point to avoid overwriting concurrent mutations
                    val latestState = _uiState.value as? EpisodeDetailUiState.Content ?: return@launch
                    _uiState.value = latestState.copy(
                        episode = latestState.episode.copy(
                            isWatched = dto.isWatched(),
                            playbackProgress = if (dto.canResume()) (dto.getWatchedPercentage() / 100.0).toFloat() else null,
                        )
                    )
                }
                else -> { /* no-op on error — stale data is better than a flicker */ }
            }
        }
    }

    private fun BaseItemDto.toEpisodeDetailModel(): EpisodeDetailModel {
        return EpisodeDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            seriesName = seriesName,
            seriesId = seriesId?.toString(),
            seasonId = seasonId?.toString(),
            episodeCode = getEpisodeCode(),
            year = getYear(),
            duration = getFormattedDuration(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            isWatched = isWatched(),
            playbackProgress = if (canResume()) (getWatchedPercentage() / 100.0).toFloat() else null,
        )
    }

    private fun BaseItemDto.toMediaDetailModel(): MediaDetailModel? {
        val source = mediaSources?.firstOrNull() ?: return null
        val streams = source.mediaStreams ?: return null

        val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
        val audioStreams = streams.filter { it.type == MediaStreamType.AUDIO }

        val video = videoStream?.let { vs ->
            val width = vs.width ?: 0
            val height = vs.height ?: 0
            val resolution = when {
                width >= 3840 || height >= 2160 -> "4K"
                width >= 1920 || height >= 1080 -> "1080p"
                width >= 1280 || height >= 720 -> "720p"
                width > 0 -> "${height}p"
                else -> null
            }
            val codecRaw = vs.codec?.uppercase()
            val codec = when (codecRaw) {
                "HEVC", "H265" -> "HEVC"
                "AVC", "H264" -> "AVC"
                "VP9" -> "VP9"
                "AV1" -> "AV1"
                else -> null
            }
            val videoRange = vs.videoRange?.toString()?.lowercase()
            val videoRangeType = vs.videoRangeType?.toString()?.lowercase()
            val hdr = when {
                videoRange?.contains("dolby vision") == true || videoRangeType?.contains("dv") == true -> "Dolby Vision"
                videoRange?.contains("hdr10+") == true -> "HDR10+"
                videoRange?.contains("hdr10") == true -> "HDR10"
                videoRange?.contains("hdr") == true || videoRangeType?.contains("hdr") == true -> "HDR"
                else -> null
            }
            VideoStreamInfo(
                resolution = resolution,
                codec = codec,
                hdr = hdr,
                bitrateKbps = vs.bitRate?.div(1000),
            )
        }

        val audios = audioStreams.map { ast ->
            val codecRaw = ast.codec?.uppercase() ?: "AUDIO"
            val codecDisplay = when {
                ast.profile?.lowercase()?.contains("atmos") == true -> "TrueHD Atmos"
                else -> when (codecRaw) {
                    "EAC3", "E-AC3" -> "EAC3"
                    "AC3" -> "AC3"
                    "TRUEHD" -> "TrueHD"
                    "DTS" -> "DTS"
                    "AAC" -> "AAC"
                    "FLAC" -> "FLAC"
                    "MP3" -> "MP3"
                    "OPUS" -> "Opus"
                    "VORBIS" -> "Vorbis"
                    else -> codecRaw
                }
            }
            val channels = when (ast.channels) {
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> ast.channels?.let { "$it ch" }
            }
            AudioStreamInfo(
                codec = codecDisplay,
                channels = channels,
                language = ast.language?.uppercase()?.take(3),
                isDefault = ast.isDefault == true,
            )
        }.sortedByDescending { it.isDefault }

        if (video == null && audios.isEmpty()) return null
        return MediaDetailModel(
            container = source.container?.uppercase(),
            video = video,
            audioStreams = audios,
        )
    }
}
