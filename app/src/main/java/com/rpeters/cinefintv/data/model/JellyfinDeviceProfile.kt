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

    /**
     * Creates a dynamic Android device profile based on detected hardware capabilities.
     */
    fun createAndroidDeviceProfile(capabilities: DirectPlayCapabilities): DeviceProfile {
        val maxWidth = capabilities.maxResolution.first
        val maxHeight = capabilities.maxResolution.second

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
                ProfileCondition(
                    condition = ProfileConditionType.LESS_THAN_EQUAL,
                    property = ProfileConditionValue.WIDTH,
                    value = "$maxWidth",
                    isRequired = false,
                ),
                ProfileCondition(
                    condition = ProfileConditionType.LESS_THAN_EQUAL,
                    property = ProfileConditionValue.HEIGHT,
                    value = "$maxHeight",
                    isRequired = false,
                ),
            )

            // For HEVC/H.265, explicitly declare 10-bit support to enable direct play of Main10 content
            // Most modern Android devices with hardware HEVC support can decode 10-bit
            if (codec == "h265" || codec == "hevc") {
                conditions.add(
                    ProfileCondition(
                        condition = ProfileConditionType.LESS_THAN_EQUAL,
                        property = ProfileConditionValue.VIDEO_BIT_DEPTH,
                        value = "10", // Support up to 10-bit color depth
                        isRequired = false,
                    ),
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
                        container = "mp4",
                        type = DlnaProfileType.VIDEO,
                        videoCodec = "h265,hevc",
                        audioCodec = "aac,opus",
                        protocol = MediaStreamProtocol.HTTP,
                        context = EncodingContext.STREAMING,
                        conditions = listOf(
                            ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, "$maxWidth", isRequired = false),
                            ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, "$maxHeight", isRequired = false),
                        ),
                    )
                } else {
                    null
                },
                // Fallback: h264
                TranscodingProfile(
                    container = "mp4",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = "h264",
                    audioCodec = "aac,opus",
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    conditions = listOf(
                        ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, "$maxWidth", isRequired = false),
                        ProfileCondition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, "$maxHeight", isRequired = false),
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

    /**
     * Creates a device profile for SHIELD/Android TV Cast devices.
     * SHIELD is very capable and can handle almost everything the Android app can.
     */
    fun createShieldCastDeviceProfile(): DeviceProfile {
        Log.d("JellyfinDeviceProfile", "Creating SHIELD Cast device profile (compatible)")

        // Use a more conservative set of audio codecs for Cast, even on SHIELD.
        // Standard Cast receivers don't reliably support DTS, TrueHD, or lossless formats.
        val shieldAudioCodecs = "aac,mp3,ac3,eac3,opus,flac"

        // SHIELD subtitle support
        val shieldSubtitleProfiles = listOf(
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ass", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "ssa", method = SubtitleDeliveryMethod.EXTERNAL),
        )

        // SHIELD can direct play many formats
        val shieldDirectPlayProfiles = listOf(
            DirectPlayProfile(
                container = "mp4,m4v",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,h265,hevc,vp9,av1",
                audioCodec = shieldAudioCodecs,
            ),
            DirectPlayProfile(
                container = "webm",
                type = DlnaProfileType.VIDEO,
                videoCodec = "vp8,vp9,av1",
                audioCodec = shieldAudioCodecs,
            ),
        )

        // HLS transcoding fallback for truly incompatible files
        val shieldTranscodingProfiles = listOf(
            TranscodingProfile(
                container = "ts",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264,h265",
                audioCodec = "aac,ac3",
                context = EncodingContext.STREAMING,
                protocol = MediaStreamProtocol.HLS,
                enableMpegtsM2TsMode = true,
                minSegments = 2,
                segmentLength = 6,
                conditions = listOf(
                    ProfileCondition(
                        condition = ProfileConditionType.LESS_THAN_EQUAL,
                        property = ProfileConditionValue.HEIGHT,
                        value = "2160", // 4K support
                        isRequired = false,
                    ),
                    ProfileCondition(
                        condition = ProfileConditionType.LESS_THAN_EQUAL,
                        property = ProfileConditionValue.WIDTH,
                        value = "3840",
                        isRequired = false,
                    ),
                ),
            ),
        )

        return DeviceProfile(
            name = "Cinefin SHIELD Cast Client",
            maxStreamingBitrate = 120_000_000, // 120 Mbps for 4K
            maxStaticBitrate = 120_000_000,
            musicStreamingTranscodingBitrate = 192_000,
            directPlayProfiles = shieldDirectPlayProfiles,
            transcodingProfiles = shieldTranscodingProfiles,
            containerProfiles = listOf(
                ContainerProfile(
                    type = DlnaProfileType.VIDEO,
                    container = "mkv,mp4,m4v,webm,ts",
                    conditions = emptyList(),
                ),
            ),
            codecProfiles = listOf(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264,h265,hevc,vp9,av1",
                    applyConditions = emptyList<ProfileCondition>(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "2160",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "3840",
                            isRequired = false,
                        ),
                    ),
                ),
            ),
            subtitleProfiles = shieldSubtitleProfiles,
        )
    }

    /**
     * Creates a device profile for Google Chromecast/Cast devices.
     * Chromecast is very restrictive.
     */
    fun createChromecastDeviceProfile(): DeviceProfile {
        Log.d("JellyfinDeviceProfile", "Creating Chromecast device profile")

        // Chromecast only supports basic audio codecs
        val chromecastAudioCodecs = "aac,ac3,eac3,mp3"

        // Chromecast subtitle support (external only)
        val chromecastSubtitleProfiles = listOf(
            SubtitleProfile(format = "srt", method = SubtitleDeliveryMethod.EXTERNAL),
            SubtitleProfile(format = "vtt", method = SubtitleDeliveryMethod.EXTERNAL),
        )

        // Chromecast can only direct play very specific formats
        val chromecastDirectPlayProfiles = listOf(
            DirectPlayProfile(
                container = "mp4,m4v",
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264", // Only H.264!
                audioCodec = chromecastAudioCodecs,
            ),
        )

        // Force HLS transcoding for everything that can't be direct played
        val chromecastTranscodingProfiles = listOf(
            TranscodingProfile(
                container = "ts", // HLS uses MPEG-TS segments
                type = DlnaProfileType.VIDEO,
                videoCodec = "h264",
                audioCodec = "aac",
                context = EncodingContext.STREAMING,
                protocol = MediaStreamProtocol.HLS,
                enableMpegtsM2TsMode = true,
                minSegments = 2,
                segmentLength = 6,
                conditions = listOf(
                    ProfileCondition(
                        condition = ProfileConditionType.LESS_THAN_EQUAL,
                        property = ProfileConditionValue.HEIGHT,
                        value = "1080",
                        isRequired = false,
                    ),
                    ProfileCondition(
                        condition = ProfileConditionType.LESS_THAN_EQUAL,
                        property = ProfileConditionValue.WIDTH,
                        value = "1920",
                        isRequired = false,
                    ),
                ),
            ),
        )

        return DeviceProfile(
            name = "Cinefin Chromecast Client",
            maxStreamingBitrate = 20_000_000, // 20 Mbps max for Cast
            maxStaticBitrate = 20_000_000,
            musicStreamingTranscodingBitrate = 192_000,
            directPlayProfiles = chromecastDirectPlayProfiles,
            transcodingProfiles = chromecastTranscodingProfiles,
            containerProfiles = listOf(
                ContainerProfile(
                    type = DlnaProfileType.VIDEO,
                    container = "mp4,m4v,ts",
                    conditions = emptyList(),
                ),
            ),
            codecProfiles = listOf(
                // Only H.264 video
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264",
                    applyConditions = emptyList<ProfileCondition>(),
                    conditions = listOf(
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.HEIGHT,
                            value = "1080",
                            isRequired = false,
                        ),
                        ProfileCondition(
                            condition = ProfileConditionType.LESS_THAN_EQUAL,
                            property = ProfileConditionValue.WIDTH,
                            value = "1920",
                            isRequired = false,
                        ),
                    ),
                ),
            ),
            subtitleProfiles = chromecastSubtitleProfiles,
        )
    }

    fun createAndroidDeviceProfile(maxWidth: Int = 1920, maxHeight: Int = 1080): DeviceProfile {
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
            transcodingProfiles = emptyList<TranscodingProfile>(),
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

    if (!videoCodecs.isNullOrBlank()) {
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
