package com.rpeters.cinefintv.ui.player

import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

object PlayerMappers {

    fun toAudioTrackOptions(mediaSource: MediaSourceInfo?): List<TrackOption> =
        mediaSource?.mediaStreams
            .orEmpty()
            .filter { it.type == MediaStreamType.AUDIO }
            .mapIndexed { index, stream ->
                TrackOption(
                    id = "audio-${stream.index}",
                    label = toTrackLabel(stream, "Audio", index + 1),
                    language = stream.language,
                    streamIndex = stream.index,
                )
            }

    fun toSubtitleTrackOptions(mediaSource: MediaSourceInfo?): List<TrackOption> =
        mediaSource?.mediaStreams
            .orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE }
            .mapIndexed { index, stream ->
                TrackOption(
                    id = "sub-${stream.index}",
                    label = toTrackLabel(stream, "Subtitle", index + 1),
                    language = stream.language,
                    streamIndex = stream.index,
                )
            }

    private fun toTrackLabel(stream: MediaStream, prefix: String, fallbackNumber: Int): String {
        val parts = listOfNotNull(
            stream.title?.takeIf { it.isNotBlank() },
            stream.displayTitle?.takeIf { it.isNotBlank() },
            stream.language?.takeIf { it.isNotBlank() },
            stream.codec?.takeIf { it.isNotBlank() }?.uppercase(),
        ).distinct()

        return if (parts.isNotEmpty()) {
            parts.joinToString(" • ")
        } else {
            "$prefix $fallbackNumber"
        }
    }
}
