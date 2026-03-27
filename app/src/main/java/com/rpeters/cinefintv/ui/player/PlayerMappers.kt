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
                    label = toAudioTrackLabel(stream, index + 1),
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
                    label = toSubtitleTrackLabel(stream, index + 1),
                    language = stream.language,
                    streamIndex = stream.index,
                )
            }

    private fun toAudioTrackLabel(stream: MediaStream, fallbackNumber: Int): String {
        stream.displayTitle?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        val parts = listOfNotNull(
            stream.title?.takeIf { it.isNotBlank() },
            stream.language?.takeIf { it.isNotBlank() },
            stream.codec?.takeIf { it.isNotBlank() }?.uppercase(),
        ).distinct()

        return if (parts.isNotEmpty()) {
            parts.joinToString(" • ")
        } else {
            "Audio $fallbackNumber"
        }
    }

    private fun toSubtitleTrackLabel(stream: MediaStream, fallbackNumber: Int): String {
        val baseLabel = stream.displayTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                stream.title?.takeIf { it.isNotBlank() },
                stream.language?.takeIf { it.isNotBlank() },
                stream.codec?.takeIf { it.isNotBlank() }?.uppercase(),
            ).distinct().joinToString(" • ").ifBlank { "Subtitle $fallbackNumber" }

        val badges = buildList {
            if (stream.isForced) add(stream.localizedForced?.takeIf { it.isNotBlank() } ?: "Forced")
            if (stream.isDefault) add(stream.localizedDefault?.takeIf { it.isNotBlank() } ?: "Default")
            if (stream.isHearingImpaired) add(stream.localizedHearingImpaired?.takeIf { it.isNotBlank() } ?: "SDH")
            if (stream.isExternal) add(stream.localizedExternal?.takeIf { it.isNotBlank() } ?: "External")
        }.distinct()

        return listOf(baseLabel, *badges.toTypedArray()).joinToString(" • ")
    }
}
