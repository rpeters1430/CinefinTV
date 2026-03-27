package com.rpeters.cinefintv.data.model

import android.util.Log
import com.rpeters.cinefintv.data.DirectPlayCapabilities
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.ContainerProfile
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * Creates a device profile optimized for direct play with dynamic capability detection.
 * This profile adapts to the actual device hardware capabilities for optimal streaming decisions.
 */
object JellyfinDeviceProfile {
    private const val DEFAULT_MAX_VIDEO_FRAMERATE = 60

    /**
     * Creates a dynamic Android device profile based on detected hardware capabilities.
     */
    fun createDeviceProfileFromCapabilities(capabilities: DirectPlayCapabilities): DeviceProfile {
        val maxWidth = capabilities.maxResolution.first
        val maxHeight = capabilities.maxResolution.second
        val maxVideoBitrate = capabilities.maxBitrate
        val maxVideoFramerate = DEFAULT_MAX_VIDEO_FRAMERATE

        Log.d("JellyfinDeviceProfile", "Creating dynamic device profile for ${capabilities.supportedVideoCodecs.size} video and ${capabilities.supportedAudioCodecs.size} audio codecs")

        // 1. Map detected audio codecs to a comma-separated string for DirectPlayProfiles
        // We include common software-decodable codecs too as they are "safe" for this client
        val supportedAudioCodecs = capabilities.supportedAudioCodecs.toMutableSet()
        supportedAudioCodecs.addAll(listOf("aac", "mp3", "flac", "vorbis", "opus", "pcm", "alac"))
        val audioCodecsStr = supportedAudioCodecs.joinToString(",")

        // 2. Map detected video codecs
        val videoCodecsStr = capabilities.supportedVideoCodecs.joinToString(",")

        // 3. Define Subtitle Profiles
        val subtitleProfiles = listOf(
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EXTERNAL),
        )

        // 4. Generate Direct Play Profiles for each supported container
        val directPlayProfiles = capabilities.supportedContainers.map { container ->
            DirectPlayProfile(
                container = container,
                type = DlnaProfileType.VIDEO,
                videoCodec = videoCodecsStr,
                audioCodec = audioCodecsStr,
            )
        } + listOf(
            // Add explicit audio-only direct play profiles
            DirectPlayProfile(container = "flac", type = DlnaProfileType.AUDIO, audioCodec = "flac", videoCodec = ""),
            DirectPlayProfile(container = "mp3", type = DlnaProfileType.AUDIO, audioCodec = "mp3", videoCodec = ""),
            DirectPlayProfile(container = "ogg", type = DlnaProfileType.AUDIO, audioCodec = "vorbis,opus", videoCodec = ""),
            DirectPlayProfile(container = "m4a", type = DlnaProfileType.AUDIO, audioCodec = "aac", videoCodec = ""),
        )

        // 5. Generate Codec Profiles with resolution constraints
        val codecProfiles = mutableListOf<CodecProfile>()

        // Add Video Codec Profiles for detected codecs
        capabilities.supportedVideoCodecs.forEach { codec ->
            val conditions = mutableListOf<ProfileCondition>(
                createMaxCondition(ProfileConditionValue.WIDTH, maxWidth),
                createMaxCondition(ProfileConditionValue.HEIGHT, maxHeight),
                createMaxCondition(ProfileConditionValue.VIDEO_BITRATE, maxVideoBitrate),
                createMaxCondition(ProfileConditionValue.VIDEO_FRAMERATE, maxVideoFramerate),
            )

            // For HEVC/H.265, explicitly declare 10-bit support to enable direct play of Main10 content
            // Most modern Android devices with hardware HEVC support can decode 10-bit
            if (codec == "h265" || codec == "hevc") {
                conditions.add(
                    createMaxCondition(ProfileConditionValue.VIDEO_BIT_DEPTH, capabilities.maxVideoBitDepth),
                )
            }

            codecProfiles.add(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = codec,
                    applyConditions = emptyList<ProfileCondition>(),
                    conditions = conditions,
                ),
            )
        }

        capabilities.maxAudioChannelsByCodec.forEach { (codec, maxChannels) ->
            codecProfiles.add(
                CodecProfile(
                    type = CodecType.AUDIO,
                    codec = codec,
                    applyConditions = emptyList<ProfileCondition>(),
                    conditions = listOf(
                        createMaxCondition(ProfileConditionValue.AUDIO_CHANNELS, maxChannels),
                    ),
                ),
            )
        }

        return DeviceProfile(
            name = "Cinefin Android Client (Dynamic)",
            maxStreamingBitrate = capabilities.maxBitrate,
            maxStaticBitrate = capabilities.maxBitrate,
            musicStreamingTranscodingBitrate = 192_000,
            directPlayProfiles = directPlayProfiles,
            subtitleProfiles = subtitleProfiles,
            codecProfiles = codecProfiles,
            containerProfiles = capabilities.supportedContainers.map {
                ContainerProfile(type = DlnaProfileType.VIDEO, container = it, conditions = emptyList())
            },
            transcodingProfiles = listOf(
                // Preferred: HEVC/h265 if supported
                if (capabilities.supportedVideoCodecs.contains("h265") || capabilities.supportedVideoCodecs.contains("hevc")) {
                    TranscodingProfile(
                        container = "ts",
                        type = DlnaProfileType.VIDEO,
                        videoCodec = "h265,hevc",
                        audioCodec = "aac,opus",
                        protocol = MediaStreamProtocol.HLS,
                        context = EncodingContext.STREAMING,
                        enableMpegtsM2TsMode = true,
                        minSegments = 2,
                        segmentLength = 6,
                        conditions = listOf(
                            createMaxCondition(ProfileConditionValue.WIDTH, maxWidth),
                            createMaxCondition(ProfileConditionValue.HEIGHT, maxHeight),
                            createMaxCondition(ProfileConditionValue.VIDEO_BITRATE, maxVideoBitrate),
                            createMaxCondition(ProfileConditionValue.VIDEO_FRAMERATE, maxVideoFramerate),
                        ),
                    )
                } else {
                    null
                },
                // Fallback: h264
                TranscodingProfile(
                    container = "ts",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264",
                    audioCodec = "aac,opus",
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    enableMpegtsM2TsMode = true,
                    minSegments = 2,
                    segmentLength = 6,
                    conditions = listOf(
                        createMaxCondition(ProfileConditionValue.WIDTH, maxWidth),
                        createMaxCondition(ProfileConditionValue.HEIGHT, maxHeight),
                        createMaxCondition(ProfileConditionValue.VIDEO_BITRATE, maxVideoBitrate),
                        createMaxCondition(ProfileConditionValue.VIDEO_FRAMERATE, maxVideoFramerate),
                    ),
                ),
                // Audio fallback
                TranscodingProfile(
                    container = "mp3",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "mp3",
                    protocol = MediaStreamProtocol.HTTP,
                    context = EncodingContext.STREAMING,
                    videoCodec = "",
                    conditions = emptyList<ProfileCondition>(),
                ),
            ).filterNotNull(),
        )
    }

    fun createDeviceProfileWithResolution(maxWidth: Int = 1920, maxHeight: Int = 1080): DeviceProfile {
        Log.d("JellyfinDeviceProfile", "Creating static device profile with maxWidth=$maxWidth, maxHeight=$maxHeight")

        val permissiveAudioCodecs = "aac,mp3,ac3,eac3,flac,vorbis,opus,pcm,alac"
        val subtitleProfiles = listOf(
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EXTERNAL),
        )

        return DeviceProfile(
            name = "Cinefin Android Client (Static)",
            maxStreamingBitrate = 100_000_000,
            maxStaticBitrate = 100_000_000,
            musicStreamingTranscodingBitrate = 192_000,
            directPlayProfiles = listOf(
                DirectPlayProfile(container = "mkv", type = DlnaProfileType.VIDEO, videoCodec = "h264,h265,hevc,vp9,av1", audioCodec = permissiveAudioCodecs),
                DirectPlayProfile(container = "mp4,m4v", type = DlnaProfileType.VIDEO, videoCodec = "h264,h265,hevc,vp9,av1", audioCodec = permissiveAudioCodecs),
            ),
            subtitleProfiles = subtitleProfiles,
            containerProfiles = listOf(
                ContainerProfile(type = DlnaProfileType.VIDEO, container = "mkv,mp4,m4v,webm,avi,mov", conditions = emptyList()),
            ),
            codecProfiles = listOf(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264,h265,hevc,vp9,av1",
                    applyConditions = emptyList<ProfileCondition>(),
                    conditions = listOf(
                        ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, "$maxWidth", isRequired = false),
                        ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, "$maxHeight", isRequired = false),
                    ),
                ),
            ),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264",
                    audioCodec = "aac,opus",
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    enableMpegtsM2TsMode = true,
                    minSegments = 2,
                    segmentLength = 6,
                    conditions = listOf(
                        createMaxCondition(ProfileConditionValue.WIDTH, maxWidth),
                        createMaxCondition(ProfileConditionValue.HEIGHT, maxHeight),
                        createMaxCondition(ProfileConditionValue.VIDEO_FRAMERATE, DEFAULT_MAX_VIDEO_FRAMERATE),
                    ),
                ),
                TranscodingProfile(
                    container = "mp3",
                    type = DlnaProfileType.AUDIO,
                    audioCodec = "mp3",
                    protocol = MediaStreamProtocol.HTTP,
                    context = EncodingContext.STREAMING,
                    videoCodec = "",
                    conditions = emptyList(),
                ),
            ),
        )
    }

    private fun createMaxCondition(property: ProfileConditionValue, value: Int): ProfileCondition {
        return ProfileCondition(
            condition = ProfileConditionType.LESS_THAN_EQUAL,
            property = property,
            value = value.toString(),
            isRequired = false,
        )
    }
}

/**
 * Extension function to convert a DeviceProfile to a map of URL parameters.
 * This provides the shorthand parameters Jellyfin expects in streaming URLs.
 */
fun DeviceProfile.toUrlParameters(): Map<String, String> {
    val params = mutableMapOf<String, String>()

    // Extract primary video codecs
    val videoCodecs = codecProfiles
        .filter { it.type == CodecType.VIDEO }
        .mapNotNull { it.codec }
        .flatMap { it.split(",") }
        .distinct()
        .joinToString(",")

    if (videoCodecs.isNotBlank()) {
        params["VideoCodec"] = videoCodecs
    }

    // Extract primary audio codecs from DirectPlay profiles
    val audioCodecs = directPlayProfiles
        .mapNotNull { it.audioCodec }
        .flatMap { it.split(",") }
        .distinct()
        .joinToString(",")

    if (!audioCodecs.isNullOrBlank()) {
        params["AudioCodec"] = audioCodecs
    }

    params["MaxStreamingBitrate"] = maxStreamingBitrate?.toString() ?: ""

    return params
}
