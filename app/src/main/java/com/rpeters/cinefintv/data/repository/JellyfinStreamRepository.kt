package com.rpeters.cinefintv.data.repository

import android.util.Log
import com.rpeters.cinefintv.data.DeviceCapabilities
import com.rpeters.cinefintv.network.ConnectivityChecker
import com.rpeters.cinefintv.network.ConnectivityQuality
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository component responsible for streaming URLs, image URLs, and media playback.
 * Extracted from JellyfinRepository to improve code organization and maintainability.
 */
@Singleton
class JellyfinStreamRepository @Inject constructor(
    private val authRepository: JellyfinAuthRepository,
    private val deviceCapabilities: DeviceCapabilities,
    private val connectivityChecker: ConnectivityChecker,
) {
    companion object {
        // Stream quality constants
        private const val DEFAULT_MAX_BITRATE = 140_000_000
        private const val DEFAULT_MAX_AUDIO_CHANNELS = 2

        // Image size constants
        private const val DEFAULT_IMAGE_MAX_HEIGHT = 800
        private const val DEFAULT_IMAGE_MAX_WIDTH = 800
        private const val BACKDROP_MAX_HEIGHT = 1080
        private const val BACKDROP_MAX_WIDTH = 1920

        // Default codecs
        private const val DEFAULT_VIDEO_CODEC = "h264"
        private const val DEFAULT_AUDIO_CODEC = "aac"
        private const val DEFAULT_CONTAINER = "mp4"
    }

    /**
     * Get optimal stream URL - intelligently chooses between direct play and transcoding
     * This is now a simplified wrapper around EnhancedPlaybackManager
     */
    fun getStreamUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null

        // Validate server connection and authentication
        if (server.accessToken.isNullOrBlank()) {
            Log.w("JellyfinStreamRepository", "getStreamUrl: No access token available")
            return null
        }

        // Validate itemId format
        if (itemId.isBlank()) {
            Log.w("JellyfinStreamRepository", "getStreamUrl: Invalid item ID")
            return null
        }

        // Validate that itemId is a valid UUID format
        runCatching { UUID.fromString(itemId) }.getOrNull() ?: run {
            Log.w("JellyfinStreamRepository", "getStreamUrl: Invalid item ID format: $itemId")
            return null
        }

        return try {
            // Enhanced logic with better Direct Play detection
            val directPlayUrl = getEnhancedDirectPlayUrl(itemId, server.url)
            if (directPlayUrl != null) {
                Log.d("JellyfinStreamRepository", "Using enhanced direct play for item $itemId")
                return directPlayUrl
            }

            // Fall back to intelligent transcoding with device-optimized parameters
            Log.d("JellyfinStreamRepository", "Using intelligent transcoded stream for item $itemId")
            getIntelligentTranscodedUrl(itemId, server.url)
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Enhanced direct play URL generation with better codec detection
     */
    private fun getEnhancedDirectPlayUrl(itemId: String, serverUrl: String): String? {
        return try {
            val deviceCaps = deviceCapabilities.getDirectPlayCapabilities()

            // Try multiple container strategies in order of preference
            val containerStrategies = listOf(
                // Strategy 1: Original container with format detection
                null to "Try original container format",
                // Strategy 2: MP4 container (most compatible)
                "mp4" to "MP4 container for maximum compatibility",
                // Strategy 3: WebM container for modern devices
                "webm" to "WebM container for modern codec support",
                // Strategy 4: MKV container for high-quality content
                "mkv" to "MKV container for advanced codec support",
            )

            for ((container, description) in containerStrategies) {
                if (container != null && !deviceCaps.supportedContainers.contains(container)) {
                    continue
                }

                val directUrl = buildDirectPlayUrl(itemId, serverUrl, container)

                // Test if this URL would work (we could implement HEAD request checking here)
                Log.d("JellyfinStreamRepository", "Testing direct play strategy: $description")

                // For now, return the first compatible option
                return directUrl
            }

            null
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Build direct play URL with optional container override
     */
    private fun buildDirectPlayUrl(itemId: String, serverUrl: String, container: String?): String {
        val baseUrl = "$serverUrl/Videos/$itemId/stream"
        val params = mutableListOf<String>()

        params.add("static=true")
        // Auth handled by OkHttp interceptor (X-Emby-Token)

        container?.let { params.add("Container=$it") }

        return "$baseUrl?${params.joinToString("&")}"
    }

    /**
     * Get intelligent transcoded URL with adaptive quality and codec selection
     */
    private fun getIntelligentTranscodedUrl(itemId: String, serverUrl: String): String {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()
        val networkQuality = assessNetworkQuality()
        val codecSupport = deviceCapabilities.getEnhancedCodecSupport()

        val params = mutableListOf<String>()

        // Select optimal video codec based on device capabilities
        val videoCodec = selectOptimalVideoCodec(codecSupport.videoCodecs)
        val audioCodec = selectOptimalAudioCodec(codecSupport.audioCodecs)
        val container = selectOptimalContainer(capabilities.supportedContainers)

        // Adaptive quality based on network conditions and device capabilities
        val qualityParams = getAdaptiveQualityParams(networkQuality, capabilities)

        params.add("VideoCodec=$videoCodec")
        params.add("AudioCodec=$audioCodec")
        params.add("Container=$container")
        params.add("MaxStreamingBitrate=${qualityParams.maxBitrate}")
        params.add("MaxWidth=${qualityParams.maxWidth}")
        params.add("MaxHeight=${qualityParams.maxHeight}")
        params.add("MaxFramerate=${qualityParams.maxFramerate}")
        params.add("TranscodingMaxAudioChannels=${qualityParams.maxAudioChannels}")

        // Advanced transcoding parameters
        params.add("BreakOnNonKeyFrames=true")
        params.add("AllowVideoStreamCopy=true") // Allow Direct Stream - keep video quality, only transcode audio if needed
        params.add("AllowAudioStreamCopy=true") // Allow audio copy if compatible
        params.add("PlaySessionId=${UUID.randomUUID()}")

        // Prevent subtitle encoding to avoid forced transcoding
        params.add("SubtitleMethod=Skip")

        val transcodingUrl = "$serverUrl/Videos/$itemId/stream?${params.joinToString("&")}"

        Log.d(
            "JellyfinStreamRepository",
            "Intelligent transcoding: $videoCodec/$audioCodec in $container, " +
                "max ${qualityParams.maxWidth}x${qualityParams.maxHeight} @ ${qualityParams.maxBitrate / 1_000_000}Mbps",
        )

        // Enhanced debug logging
        Log.d("JellyfinStreamRepository", "Device capabilities: maxRes=${capabilities.maxResolution}, supports4K=${capabilities.supports4K}")
        Log.d("JellyfinStreamRepository", "Network quality: $networkQuality")
        Log.d("JellyfinStreamRepository", "Quality params: ${qualityParams.maxWidth}x${qualityParams.maxHeight}, bitrate=${qualityParams.maxBitrate}")
        Log.d("JellyfinStreamRepository", "Transcoding URL: $transcodingUrl")

        return transcodingUrl
    }

    /**
     * Get transcoded stream URL with specific quality parameters.
     * Uses progressive streaming endpoint for better compatibility and immediate playback.
     * @param allowVideoStreamCopy When false, forces real video transcoding (required for offline
     *   quality presets so the server honours MaxWidth/MaxHeight/VideoBitrate instead of copying
     *   the original stream).
     * @param allowAudioStreamCopy When false, forces audio transcoding for compatibility.
     */
    fun getTranscodedStreamUrl(
        itemId: String,
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        videoCodec: String = DEFAULT_VIDEO_CODEC,
        audioCodec: String = DEFAULT_AUDIO_CODEC,
        container: String = DEFAULT_CONTAINER,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        audioChannels: Int = DEFAULT_MAX_AUDIO_CHANNELS,
        audioBitrate: Int? = null,
        allowVideoStreamCopy: Boolean = true,
        allowAudioStreamCopy: Boolean = true,
    ): String? {
        val server = authRepository.getCurrentServer() ?: return null

        // Validate server connection and authentication
        if (server.accessToken.isNullOrBlank()) {
            Log.w("JellyfinStreamRepository", "getTranscodedStreamUrl: No access token available")
            return null
        }

        // Validate itemId format
        if (itemId.isBlank()) {
            Log.w("JellyfinStreamRepository", "getTranscodedStreamUrl: Invalid item ID")
            return null
        }

        // Validate that itemId is a valid UUID format
        runCatching { java.util.UUID.fromString(itemId) }.getOrNull() ?: run {
            Log.w("JellyfinStreamRepository", "getTranscodedStreamUrl: Invalid item ID format: $itemId")
            return null
        }

        return try {
            val params = mutableListOf<String>()

            // Add transcoding parameters
            maxBitrate?.let {
                params.add("MaxStreamingBitrate=$it")
                params.add("VideoBitRate=$it")
            }
            maxWidth?.let { params.add("MaxWidth=$it") }
            maxHeight?.let { params.add("MaxHeight=$it") }
            params.add("VideoCodec=$videoCodec")
            params.add("AudioCodec=$audioCodec")
            params.add("Container=$container")
            params.add("AudioChannels=$audioChannels")
            audioBitrate?.let { params.add("AudioBitRate=$it") }
            params.add("DeviceId=${deviceCapabilities.getDeviceId()}")
            params.add("BreakOnNonKeyFrames=true")
            params.add("AllowVideoStreamCopy=$allowVideoStreamCopy")
            params.add("AllowAudioStreamCopy=$allowAudioStreamCopy")

            // Add stream indices for multilingual content
            audioStreamIndex?.let { params.add("AudioStreamIndex=$it") }
            subtitleStreamIndex?.let { params.add("SubtitleStreamIndex=$it") }

            // Add playback identifiers when available so the server can apply session-specific settings.
            mediaSourceId?.let { params.add("MediaSourceId=$it") }
            playSessionId?.let { params.add("PlaySessionId=$it") }
                ?: params.add("PlaySessionId=${java.util.UUID.randomUUID()}")
            // Auth via header (OkHttp interceptor)

            // Use progressive stream endpoint instead of HLS for better compatibility
            // HLS (master.m3u8) requires additional manifest parsing and can fail if not ready
            "${server.url}/Videos/$itemId/stream?${params.joinToString("&")}"
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get HLS (HTTP Live Streaming) URL for adaptive bitrate streaming
     */
    fun getHlsStreamUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Videos/$itemId/master.m3u8?" +
            "VideoCodec=$DEFAULT_VIDEO_CODEC&" +
            "AudioCodec=$DEFAULT_AUDIO_CODEC&" +
            "MaxStreamingBitrate=$DEFAULT_MAX_BITRATE&" +
            "PlaySessionId=${UUID.randomUUID()}"
    }

    /**
     * Get DASH (Dynamic Adaptive Streaming over HTTP) URL
     */
    fun getDashStreamUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Videos/$itemId/stream.mpd?" +
            "VideoCodec=$DEFAULT_VIDEO_CODEC&" +
            "AudioCodec=$DEFAULT_AUDIO_CODEC&" +
            "MaxStreamingBitrate=$DEFAULT_MAX_BITRATE&" +
            "PlaySessionId=${UUID.randomUUID()}"
    }

    /**
     * Get download URL for a media item
     */
    fun getDownloadUrl(itemId: String): String? {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Items/$itemId/Download"
    }

    /**
     * Get direct stream URL - forces direct play without transcoding
     */
    fun getDirectStreamUrl(itemId: String, container: String? = null): String? {
        val server = authRepository.getCurrentServer() ?: return null
        val containerParam = container?.let { "&Container=$it" } ?: ""
        return "${server.url}/Videos/$itemId/stream?static=true$containerParam"
    }

    /**
     * Get optimal direct play URL if the device supports the media format
     */
    private fun getOptimalDirectPlayUrl(itemId: String, serverUrl: String, accessToken: String): String? {
        return try {
            // For now, we'll try common container formats that Android supports well
            // In a full implementation, we'd query the media info first

            // Try MP4 container first (most compatible)
            if (deviceCapabilities.canPlayContainer("mp4")) {
                val mp4Url = "$serverUrl/Videos/$itemId/stream?static=true&Container=mp4"
                Log.d("JellyfinStreamRepository", "Trying direct play with MP4 container")
                return mp4Url
            }

            // Try original container (MKV is supported on newer Android versions)
            if (deviceCapabilities.canPlayContainer("mkv")) {
                val directUrl = "$serverUrl/Videos/$itemId/stream?static=true"
                Log.d("JellyfinStreamRepository", "Trying direct play with original container")
                return directUrl
            }

            null
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get optimal transcoded URL based on device capabilities
     */
    private fun getOptimalTranscodedUrl(itemId: String, serverUrl: String, accessToken: String): String {
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()
        val maxRes = capabilities.maxResolution

        val params = mutableListOf<String>()

        // Use best supported video codec
        val videoCodec = when {
            capabilities.supportedVideoCodecs.contains("h264") -> "h264"
            capabilities.supportedVideoCodecs.contains("mpeg4") -> "mpeg4"
            else -> "h264" // Fallback
        }

        // Use best supported audio codec
        val audioCodec = when {
            capabilities.supportedAudioCodecs.contains("aac") -> "aac"
            capabilities.supportedAudioCodecs.contains("mp3") -> "mp3"
            else -> "aac" // Fallback
        }

        // Use best supported container
        val container = when {
            capabilities.supportedContainers.contains("mp4") -> "mp4"
            capabilities.supportedContainers.contains("webm") -> "webm"
            else -> "mp4" // Fallback
        }

        params.add("VideoCodec=$videoCodec")
        params.add("AudioCodec=$audioCodec")
        params.add("Container=$container")
        params.add("MaxStreamingBitrate=20000000") // 20 Mbps max - can be made configurable
        params.add("MaxWidth=${maxRes.first}")
        params.add("MaxHeight=${maxRes.second}")
        params.add("TranscodingMaxAudioChannels=2")
        params.add("BreakOnNonKeyFrames=true")
        // Auth via header

        Log.d("JellyfinStreamRepository", "Transcoding with: $videoCodec/$audioCodec in $container, max ${maxRes.first}x${maxRes.second}")

        return "$serverUrl/Videos/$itemId/stream?${params.joinToString("&")}"
    }

    /**
     * Get image URL for an item
     */
    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) {
                Log.w("JellyfinStreamRepository", "getImageUrl: Server not available or missing credentials")
                return null
            }

            val tagParam = tag?.let { "&tag=$it" } ?: ""
            "${server.url}/Items/$itemId/Images/$imageType?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH$tagParam"
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get avatar image URL for a user
     */
    fun getUserImageUrl(userId: String, tag: String? = null): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) {
                Log.w("JellyfinStreamRepository", "getUserImageUrl: Server not available or missing credentials")
                return null
            }

            val tagParam = tag?.let { "&tag=$it" } ?: ""
            "${server.url}/Users/$userId/Images/Primary?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH$tagParam"
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get series image URL for an item (uses series poster for episodes)
     */
    fun getSeriesImageUrl(item: BaseItemDto): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) {
                Log.w("JellyfinStreamRepository", "getSeriesImageUrl: Server not available or missing credentials")
                return null
            }

            // For episodes, use the series poster if available
            val imageId = if (item.type == BaseItemKind.EPISODE && item.seriesId != null) {
                item.seriesId.toString()
            } else {
                item.id.toString()
            }
            "${server.url}/Items/$imageId/Images/Primary?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH"
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get a landscape (16:9 / backdrop) image URL suitable for horizontal card thumbnails.
     * Order of preference:
     * - Episodes: Primary (landscape still screenshot) -> Backdrop -> Thumb
     * - Others: Backdrop -> Thumb -> Primary (poster/square)
     */
    fun getLandscapeImageUrl(item: BaseItemDto): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) return null

            val itemId = item.id.toString()

            if (item.type == BaseItemKind.EPISODE) {
                // Episode primary is a landscape still screenshot, not a portrait poster
                item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                    return getImageUrl(itemId, "Primary", tag)
                }
            }

            // Prefer Backdrop (Horizontal)
            item.backdropImageTags?.firstOrNull()?.let { tag ->
                return getImageUrl(itemId, "Backdrop", tag)
            }

            // Fallback to Thumb (Horizontal)
            item.imageTags?.get(ImageType.THUMB)?.let { tag ->
                return getImageUrl(itemId, "Thumb", tag)
            }

            // Final fallback to Primary (might be portrait/square)
            item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                return getImageUrl(itemId, "Primary", tag)
            }

            null
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get a strictly horizontal thumbnail for wide cards.
     * Unlike [getLandscapeImageUrl], this intentionally avoids falling back to Primary art,
     * which is often portrait for seasons and causes awkward crops in 16:9 cards.
     *
     * For seasons without a backdrop/thumb, falls back to the parent series landscape image
     * when [parentItem] is supplied.
     */
    fun getWideCardImageUrl(item: BaseItemDto, parentItem: BaseItemDto? = null): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) return null

            val itemId = item.id.toString()

            if (item.type == BaseItemKind.EPISODE) {
                item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                    return getImageUrl(itemId, "Primary", tag)
                }
            }

            item.backdropImageTags?.firstOrNull()?.let { tag ->
                return getImageUrl(itemId, "Backdrop", tag)
            }

            item.imageTags?.get(ImageType.THUMB)?.let { tag ->
                return getImageUrl(itemId, "Thumb", tag)
            }

            // For seasons without backdrop/thumb: try parent series landscape image
            if (item.type == BaseItemKind.SEASON && parentItem != null) {
                parentItem.backdropImageTags?.firstOrNull()?.let { tag ->
                    return getImageUrl(parentItem.id.toString(), "Backdrop", tag)
                }
                parentItem.imageTags?.get(ImageType.THUMB)?.let { tag ->
                    return getImageUrl(parentItem.id.toString(), "Thumb", tag)
                }
            }

            // Last resort: Primary (may be portrait for seasons)
            if (item.type == BaseItemKind.SEASON) {
                item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                    return getImageUrl(itemId, "Primary", tag)
                }
            }

            null
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get image URL for search cards, preferring landscape images for consistency.
     */
    fun getSearchCardImageUrl(item: BaseItemDto): String? {
        return getLandscapeImageUrl(item)
    }

    /**
     * Get backdrop URL for an item
     */
    fun getBackdropUrl(item: BaseItemDto): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) {
                Log.w("JellyfinStreamRepository", "getBackdropUrl: Server not available or missing credentials")
                return null
            }

            val backdropTag = item.backdropImageTags?.firstOrNull()
            if (backdropTag != null) {
                "${server.url}/Items/${item.id}/Images/Backdrop?tag=$backdropTag&maxHeight=$BACKDROP_MAX_HEIGHT&maxWidth=$BACKDROP_MAX_WIDTH"
            } else {
                getImageUrl(item.id.toString(), "Primary", item.imageTags?.get(ImageType.PRIMARY))
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get logo URL for an item (for detail screens)
     */
    fun getLogoUrl(item: BaseItemDto): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) {
                Log.w("JellyfinStreamRepository", "getLogoUrl: Server not available or missing credentials")
                return null
            }

            val logoTag = item.imageTags?.get(ImageType.LOGO)
            if (logoTag != null) {
                "${server.url}/Items/${item.id}/Images/Logo?tag=$logoTag"
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Assess current network quality for adaptive streaming
     */
    private fun assessNetworkQuality(): ConnectivityQuality {
        return connectivityChecker.getNetworkQuality()
    }

    /**
     * Select optimal video codec based on device support and performance ratings
     */
    private fun selectOptimalVideoCodec(videoCodecSupport: Map<String, com.rpeters.cinefintv.data.CodecSupportDetail>): String {
        val preferredOrder = listOf("h265", "hevc", "h264", "vp9", "vp8", "mpeg4")

        for (codec in preferredOrder) {
            val support = videoCodecSupport[codec]
            if (support != null && support.support != com.rpeters.cinefintv.data.CodecSupport.NOT_SUPPORTED) {
                return codec
            }
        }

        return "h264" // Ultimate fallback
    }

    /**
     * Select optimal audio codec based on device support
     */
    private fun selectOptimalAudioCodec(audioCodecSupport: Map<String, com.rpeters.cinefintv.data.CodecSupportDetail>): String {
        val preferredOrder = listOf("opus", "aac", "ac3", "eac3", "mp3")

        for (codec in preferredOrder) {
            val support = audioCodecSupport[codec]
            if (support != null && support.support != com.rpeters.cinefintv.data.CodecSupport.NOT_SUPPORTED) {
                return codec
            }
        }

        return "aac" // Ultimate fallback
    }

    /**
     * Select optimal container format
     */
    private fun selectOptimalContainer(supportedContainers: List<String>): String {
        val preferredOrder = listOf("mp4", "webm", "mkv", "avi")

        for (container in preferredOrder) {
            if (supportedContainers.contains(container)) {
                return container
            }
        }

        return "mp4" // Ultimate fallback
    }

    /**
     * Get adaptive quality parameters based on network and device capabilities
     */
    private fun getAdaptiveQualityParams(
        networkQuality: ConnectivityQuality,
        capabilities: com.rpeters.cinefintv.data.DirectPlayCapabilities,
    ): AdaptiveQualityParams {
        val deviceMaxBitrate = capabilities.maxBitrate
        val deviceMaxWidth = capabilities.maxResolution.first
        val deviceMaxHeight = capabilities.maxResolution.second

        return when (networkQuality) {
            ConnectivityQuality.EXCELLENT -> {
                AdaptiveQualityParams(
                    maxBitrate = minOf(deviceMaxBitrate, 120_000_000), // 120 Mbps for excellent
                    maxWidth = minOf(deviceMaxWidth, 3840), // Cap at 4K
                    maxHeight = minOf(deviceMaxHeight, 2160), // Cap at 4K
                    maxFramerate = 60,
                    maxAudioChannels = 8, // 7.1 surround
                )
            }
            ConnectivityQuality.GOOD -> {
                AdaptiveQualityParams(
                    maxBitrate = minOf(deviceMaxBitrate, 40_000_000), // 40 Mbps for good
                    maxWidth = minOf(deviceMaxWidth, 1920), // Cap at 1080p
                    maxHeight = minOf(deviceMaxHeight, 1080), // Cap at 1080p
                    maxFramerate = 60,
                    maxAudioChannels = 6, // 5.1 surround
                )
            }
            ConnectivityQuality.FAIR -> {
                AdaptiveQualityParams(
                    maxBitrate = minOf(deviceMaxBitrate, 15_000_000), // 15 Mbps for fair
                    maxWidth = minOf(deviceMaxWidth, 1920), // Cap at 1080p
                    maxHeight = minOf(deviceMaxHeight, 1080), // Cap at 1080p
                    maxFramerate = 30,
                    maxAudioChannels = 2, // Stereo
                )
            }
            ConnectivityQuality.POOR -> {
                AdaptiveQualityParams(
                    maxBitrate = minOf(deviceMaxBitrate, 5_000_000), // 5 Mbps for poor
                    maxWidth = minOf(deviceMaxWidth, 1280), // Cap at 720p
                    maxHeight = minOf(deviceMaxHeight, 720), // Cap at 720p
                    maxFramerate = 30,
                    maxAudioChannels = 2, // Stereo
                )
            }
        }
    }
}

/**
 * Adaptive quality parameters for transcoding
 */
private data class AdaptiveQualityParams(
    val maxBitrate: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFramerate: Int,
    val maxAudioChannels: Int,
)
