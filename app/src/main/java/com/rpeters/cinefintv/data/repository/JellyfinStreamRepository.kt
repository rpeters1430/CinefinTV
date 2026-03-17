package com.rpeters.cinefintv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.DeviceCapabilities
import com.rpeters.cinefintv.data.model.JellyfinDeviceProfile
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository component responsible for streaming URLs, image URLs, and media playback.
 * Extracted from JellyfinRepository to improve code organization and maintainability.
 */
@Singleton
class JellyfinStreamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: JellyfinAuthRepository,
    private val deviceCapabilities: DeviceCapabilities,
) {
    companion object {
        // Stream quality constants
        private const val DEFAULT_MAX_BITRATE = 140_000_000
        private const val DEFAULT_MAX_AUDIO_CHANNELS = 8
        private const val DEFAULT_TV_MAX_AUDIO_CHANNELS = 6 // Standard 5.1 for TV
        private const val TICKS_PER_MILLISECOND = 10_000L

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
     * Fallback stream URL — used when EnhancedPlaybackManager cannot resolve a URL.
     * Returns a direct-play URL; the OkHttp interceptor adds auth headers automatically.
     */
    fun getStreamUrl(itemId: String): String? = getDirectStreamUrl(itemId)

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
     * Get portrait/primary artwork for poster cards.
     * Episodes prefer the parent series poster because episode Primary art is usually a landscape still.
     * Seasons fall back to the parent series poster when they do not have their own Primary art.
     */
    fun getPosterCardImageUrl(item: BaseItemDto, parentItem: BaseItemDto? = null): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) return null

            val itemId = item.id.toString()

            if (item.type == BaseItemKind.EPISODE) {
                val posterOwnerId = item.seriesId?.toString()
                    ?: parentItem?.seriesId?.toString()
                    ?: parentItem?.id?.toString()
                    ?: itemId
                return "${server.url}/Items/$posterOwnerId/Images/Primary?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH"
            }

            item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                return getImageUrl(itemId, "Primary", tag)
            }

            if (item.type == BaseItemKind.SEASON && parentItem != null) {
                return "${server.url}/Items/${parentItem.id}/Images/Primary?maxHeight=$DEFAULT_IMAGE_MAX_HEIGHT&maxWidth=$DEFAULT_IMAGE_MAX_WIDTH"
            }

            item.imageTags?.get(ImageType.THUMB)?.let { tag ->
                return getImageUrl(itemId, "Thumb", tag)
            }

            item.backdropImageTags?.firstOrNull()?.let { tag ->
                return getImageUrl(itemId, "Backdrop", tag)
            }

            null
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
     * Get image URL for search cards, preferring poster-style artwork for portrait cards.
     */
    fun getSearchCardImageUrl(item: BaseItemDto): String? {
        return getPosterCardImageUrl(item)
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
     * Get backdrop URL, falling back to the parent item's backdrop when the item has none.
     * Useful for Seasons/Episodes that often lack their own backdrop image.
     */
    fun getBackdropUrlWithFallback(item: BaseItemDto, parentItem: BaseItemDto? = null): String? {
        return try {
            val server = authRepository.getCurrentServer() ?: return null
            if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) return null

            // Try item's own backdrop first
            val backdropTag = item.backdropImageTags?.firstOrNull()
            if (backdropTag != null) {
                return "${server.url}/Items/${item.id}/Images/Backdrop?tag=$backdropTag&maxHeight=$BACKDROP_MAX_HEIGHT&maxWidth=$BACKDROP_MAX_WIDTH"
            }

            // Try parent's backdrop
            if (parentItem != null) {
                val parentBackdropTag = parentItem.backdropImageTags?.firstOrNull()
                if (parentBackdropTag != null) {
                    return "${server.url}/Items/${parentItem.id}/Images/Backdrop?tag=$parentBackdropTag&maxHeight=$BACKDROP_MAX_HEIGHT&maxWidth=$BACKDROP_MAX_WIDTH"
                }
            }

            // Fall back to existing logic (may use Primary)
            getBackdropUrl(item)
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
     * Gets playback information for a media item, including available streams and transcoding decisions.
     */
    suspend fun getPlaybackInfo(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startPositionMs: Long = 0L,
    ): PlaybackInfoResponse = withContext(Dispatchers.IO) {
        val client = authRepository.createApiClient()
            ?: throw IllegalStateException("Client not authenticated")

        val server = authRepository.getCurrentServer()
            ?: throw IllegalStateException("No active server found")

        val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
            ?: throw IllegalStateException("Invalid user UUID: ${server.userId}")
        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid item UUID: $itemId")

        // Get device capabilities to create proper device profile
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()
        val deviceProfile = JellyfinDeviceProfile.createDeviceProfileFromCapabilities(capabilities)

        // Set maxStreamingBitrate based on network quality
        val maxBitrate = getNetworkBasedMaxBitrate()

        val playbackInfoDto = PlaybackInfoDto(
            userId = userUuid,
            maxStreamingBitrate = maxBitrate,
            startTimeTicks = if (startPositionMs > 0L) startPositionMs * TICKS_PER_MILLISECOND else null,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxAudioChannels = DEFAULT_TV_MAX_AUDIO_CHANNELS,
            mediaSourceId = null,
            liveStreamId = null,
            deviceProfile = deviceProfile,
            enableDirectPlay = true,
            enableDirectStream = true,
            enableTranscoding = true,
            allowVideoStreamCopy = true,
            allowAudioStreamCopy = true,
            autoOpenLiveStream = null,
        )

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                "JellyfinStreamRepository",
                "PlaybackInfo request for item $itemId: " +
                    "maxBitrate=${playbackInfoDto.maxStreamingBitrate} (${maxBitrate / 1_000_000}Mbps), " +
                    "directPlay=${playbackInfoDto.enableDirectPlay}, " +
                    "directStream=${playbackInfoDto.enableDirectStream}, " +
                    "transcode=${playbackInfoDto.enableTranscoding}",
            )
        }

        val response = client.mediaInfoApi.getPostedPlaybackInfo(
            itemId = itemUuid,
            data = playbackInfoDto,
        ).content

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                "JellyfinStreamRepository",
                "PlaybackInfo response: playSessionId=${response.playSessionId}, " +
                    "mediaSources=${response.mediaSources.size}",
            )
        }

        response
    }

    private fun getNetworkBasedMaxBitrate(): Int {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return 25_000_000 // Default to 25 Mbps if network service unavailable

        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return when {
            // Ethernet: Best quality
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> {
                120_000_000 // 120 Mbps
            }
            // WiFi: Good quality
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                60_000_000 // 60 Mbps
            }
            // Default (Mobile or other): Conservative quality
            else -> 20_000_000 // 20 Mbps
        }
    }
}
