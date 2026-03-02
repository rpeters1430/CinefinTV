package com.rpeters.cinefintv.data.playback

import android.content.Context
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.DeviceCapabilities
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import com.rpeters.cinefintv.data.repository.JellyfinStreamRepository
import com.rpeters.cinefintv.network.ConnectivityChecker
import com.rpeters.cinefintv.network.ConnectivityQuality
import com.rpeters.cinefintv.network.NetworkType
import com.rpeters.cinefintv.ui.utils.findDefaultAudioStream
import com.rpeters.cinefintv.ui.utils.findDefaultVideoStream
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Enhanced playback manager that intelligently determines the best playback method
 * based on device capabilities, network conditions, and media characteristics.
 */
@Singleton
class EnhancedPlaybackManager @Inject constructor(
    private val context: Context,
    private val repository: JellyfinRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val deviceCapabilities: DeviceCapabilities,
    private val connectivityChecker: ConnectivityChecker,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
) {

    companion object {
        private const val TAG = "EnhancedPlaybackManager"

        // Network quality thresholds (in Mbps)
        private const val HIGH_QUALITY_THRESHOLD = 25
        private const val MEDIUM_QUALITY_THRESHOLD = 10
        private const val LOW_QUALITY_THRESHOLD = 3

        // Direct play bitrate thresholds (defaults, will be overridden by user prefs)
        private const val DIRECT_PLAY_MAX_BITRATE = 140_000_000 // 140 Mbps

        // Machine-readable reason codes (Phase 0)
        object ReasonCodes {
            const val SERVER_DIRECT_PLAY_OK = "SERVER_DIRECT_PLAY_OK"
            const val SERVER_INCORRECT_10BIT_REJECTION = "SERVER_INCORRECT_10BIT_REJECTION"
            const val DEVICE_CAPABLE_FORCE_DIRECT = "DEVICE_CAPABLE_FORCE_DIRECT"
            const val AUDIO_CODEC_UNSUPPORTED = "AUDIO_CODEC_UNSUPPORTED"
            const val AUDIO_SURROUND_DOWNMIX_REQUIRED = "AUDIO_SURROUND_DOWNMIX_REQUIRED"
            const val VIDEO_CODEC_UNSUPPORTED = "VIDEO_CODEC_UNSUPPORTED"
            const val VIDEO_RESOLUTION_EXCEEDED = "VIDEO_RESOLUTION_EXCEEDED"
            const val NETWORK_BITRATE_EXCEEDED = "NETWORK_BITRATE_EXCEEDED"
            const val CONTAINER_UNSUPPORTED = "CONTAINER_UNSUPPORTED"
            const val DIRECT_STREAM_VIDEO_COPY_OK = "DIRECT_STREAM_VIDEO_COPY_OK"
            const val FULL_TRANSCODE_REQUIRED = "FULL_TRANSCODE_REQUIRED"
            const val DECISION_FALLBACK_TO_SERVER = "DECISION_FALLBACK_TO_SERVER"
        }
    }

    /**
     * Get optimal playback URL with intelligent Direct Play detection
     */
    suspend fun getOptimalPlaybackUrl(
        item: BaseItemDto,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val itemId = item.id.toString()

                if (BuildConfig.DEBUG) {
                    SecureLogger.v(TAG, "Getting optimal playback URL for: ${item.name} (${item.type}) [Audio: $audioStreamIndex, Sub: $subtitleStreamIndex]")
                }

                // Get detailed playback info from server
                val playbackInfo = getPlaybackInfo(itemId, audioStreamIndex, subtitleStreamIndex)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info")
                }

                // CENTRALIZED DECISION (Phase 2)
                val decision = computePlaybackDecision(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
                
                if (decision is PlaybackResult.Error) {
                    SecureLogger.e(TAG, "Playback decision failed: ${decision.message}")
                } else if (decision is PlaybackResult.DirectPlay && decision.decisionTrace != null) {
                    logDecisionTrace(decision.decisionTrace)
                } else if (decision is PlaybackResult.Transcoding && decision.decisionTrace != null) {
                    logDecisionTrace(decision.decisionTrace)
                }

                return@withContext decision
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Helper to log structured decision traces (Phase 0)
     */
    private fun logDecisionTrace(trace: PlaybackDecisionTrace) {
        SecureLogger.d(TAG, "[PLAYBACK_DECISION] decision=${trace.decision}, ruleId=${trace.ruleId}, reasons=${trace.reasonCodes}, device=${trace.deviceClass}")
    }

    /**
     * Fallback URL when Direct Play fails at runtime.
     * First attempts Direct Stream (video copy + audio transcode) — far more efficient than
     * full transcoding when only the audio codec is incompatible. Falls back to full
     * transcoding only if the video codec itself cannot be copied.
     */
    suspend fun getTranscodingPlaybackUrl(
        item: BaseItemDto,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        return withContext(Dispatchers.IO) {
            try {
                val itemId = item.id.toString()
                SecureLogger.d(TAG, "Getting fallback URL for: ${item.name} (Direct Play failed)")

                val playbackInfo = getPlaybackInfo(itemId, audioStreamIndex, subtitleStreamIndex)
                if (playbackInfo == null) {
                    return@withContext PlaybackResult.Error("Failed to get playback info for fallback")
                }

                val anySource = playbackInfo.mediaSources.firstOrNull()
                    ?: return@withContext PlaybackResult.Error("No media sources available for fallback")

                // Try Direct Stream first — copies video, only transcodes audio.
                // This avoids unnecessary video re-encoding when only the audio was the problem.
                if (canDirectStreamMediaSource(anySource, item, null)) {
                    SecureLogger.d(TAG, "Direct Play fallback → trying Direct Stream (video copy, audio transcode)")
                    val directStreamResult = getDirectStreamWithAudioTranscode(
                        item, anySource, playbackInfo, audioStreamIndex, subtitleStreamIndex,
                    )
                    if (directStreamResult !is PlaybackResult.Error) {
                        return@withContext directStreamResult
                    }
                    SecureLogger.w(TAG, "Direct Stream also failed, falling back to full transcoding")
                }

                // Full transcoding fallback
                SecureLogger.d(TAG, "Using full transcoding fallback for: ${item.name}")
                getOptimalTranscodingUrl(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Centralized decision engine (Phase 2).
     * Replaces getServerDirectedPlaybackUrl with structured trace logging.
     */
    private suspend fun computePlaybackDecision(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val serverUrl = repository.getCurrentServer()?.url
        val mediaSources = playbackInfo.mediaSources
        val playSessionId = playbackInfo.playSessionId
        val sessionId = java.util.UUID.randomUUID().toString()
        val deviceCaps = deviceCapabilities.getDirectPlayCapabilities()
        val networkClass = connectivityChecker.getNetworkType().name

        if (mediaSources.isNullOrEmpty()) {
            return PlaybackResult.Error("No media sources available")
        }

        val reasons = mutableListOf<String>()
        val anySource = mediaSources.firstOrNull() ?: return PlaybackResult.Error("Empty source list")

        // WORKAROUND: Force Direct Play if server incorrectly rejects (e.g. 10-bit HEVC).
        // IMPORTANT: Only bypass when audio passes a strict check (no stereo-downmix fallback).
        // If the server rejected because the audio codec needs transcoding (e.g. EAC3 5.1, DTS),
        // we must NOT force direct play — instead let it fall through to Direct Stream below.
        if (!anySource.supportsDirectPlay) {
            val audioStream = anySource.mediaStreams.findDefaultAudioStream()
            val audioCodec = audioStream?.codec
            val audioChannels = audioStream?.channels ?: 2
            val audioCanBeDirectPlayed = if (audioCodec != null) {
                deviceCapabilities.canPlayAudioCodecStrict(audioCodec, audioChannels)
            } else true

            if (audioCanBeDirectPlayed && canDirectPlayMediaSource(anySource, item, reasons, bypassServerDecision = true)) {
                // Audio is fine — server likely incorrectly rejected (e.g. 10-bit HEVC profile)
                val container = anySource.container ?: "mkv"
                val url = buildDirectPlayUrl(serverUrl, itemId, anySource.id, container, playSessionId)

                reasons.add(ReasonCodes.SERVER_INCORRECT_10BIT_REJECTION)
                val trace = createTrace(sessionId, "DIRECT_PLAY", "FORCE_BYPASS_SERVER", reasons, anySource, deviceCaps, networkClass)

                return PlaybackResult.DirectPlay(
                    url = url ?: "",
                    container = container,
                    videoCodec = getVideoCodec(anySource),
                    audioCodec = getAudioCodec(anySource),
                    bitrate = anySource.bitrate ?: 0,
                    reason = "Forcing direct play: server rejection bypassed",
                    reasonCodes = reasons,
                    playSessionId = playSessionId,
                    decisionTrace = trace
                )
            }
            // If audio failed strict check, fall through — Direct Stream will handle it below.
        }

        // Try standard Direct Play
        val directPlaySource = mediaSources.firstOrNull { it.supportsDirectPlay }
        if (directPlaySource != null && canDirectPlayMediaSource(directPlaySource, item, reasons)) {
            val container = directPlaySource.container ?: "mkv"
            val url = buildDirectPlayUrl(serverUrl, itemId, directPlaySource.id, container, playSessionId)
            
            reasons.add(ReasonCodes.SERVER_DIRECT_PLAY_OK)
            val trace = createTrace(sessionId, "DIRECT_PLAY", "STANDARD_DIRECT_PLAY", reasons, directPlaySource, deviceCaps, networkClass)
            
            return PlaybackResult.DirectPlay(
                url = url ?: "",
                container = container,
                videoCodec = getVideoCodec(directPlaySource),
                audioCodec = getAudioCodec(directPlaySource),
                bitrate = directPlaySource.bitrate ?: 0,
                reason = "Direct Play supported by server and device",
                reasonCodes = reasons,
                playSessionId = playSessionId,
                decisionTrace = trace
            )
        }

        // Try Direct Stream (audio transcode only)
        if (canDirectStreamMediaSource(anySource, item, reasons)) {
            reasons.add(ReasonCodes.DIRECT_STREAM_VIDEO_COPY_OK)
            val result = getDirectStreamWithAudioTranscode(item, anySource, playbackInfo, audioStreamIndex, subtitleStreamIndex)
            
            if (result is PlaybackResult.Transcoding) {
                val trace = createTrace(sessionId, "DIRECT_STREAM", "AUDIO_ONLY_TRANSCODE", reasons, anySource, deviceCaps, networkClass, isDirectStream = true)
                return result.copy(reasonCodes = reasons, decisionTrace = trace)
            }
        }

        // Fallback to Full Transcoding
        reasons.add(ReasonCodes.FULL_TRANSCODE_REQUIRED)
        val result = getOptimalTranscodingUrl(item, playbackInfo, audioStreamIndex, subtitleStreamIndex)
        if (result is PlaybackResult.Transcoding) {
            val trace = createTrace(sessionId, "TRANSCODING", "FULL_TRANSCODE", reasons, anySource, deviceCaps, networkClass)
            return result.copy(reasonCodes = reasons, decisionTrace = trace)
        }

        return result
    }

    private fun buildDirectPlayUrl(serverUrl: String?, itemId: String, sourceId: String?, container: String, playSessionId: String?): String? {
        if (serverUrl == null || sourceId == null) return streamRepository.getDirectStreamUrl(itemId, container)
        return buildString {
            append(serverUrl)
            append("/Videos/")
            append(itemId)
            append("/stream.")
            append(container)
            append("?static=true&mediaSourceId=")
            append(sourceId)
            if (!playSessionId.isNullOrBlank()) {
                append("&PlaySessionId=")
                append(playSessionId)
            }
        }
    }

    private fun createTrace(
        sessionId: String,
        decision: String,
        ruleId: String,
        reasons: List<String>,
        source: org.jellyfin.sdk.model.api.MediaSourceInfo,
        caps: com.rpeters.cinefintv.data.DirectPlayCapabilities,
        network: String,
        isDirectStream: Boolean = false
    ): PlaybackDecisionTrace {
        val videoStream = source.mediaStreams.findDefaultVideoStream()
        val audioStream = source.mediaStreams.findDefaultAudioStream()
        
        return PlaybackDecisionTrace(
            sessionId = sessionId,
            decision = decision,
            ruleId = ruleId,
            reasonCodes = reasons,
            deviceClass = caps.deviceTier.name,
            networkClass = network,
            video = VideoDecision(
                sourceCodec = videoStream?.codec,
                sourceBitDepth = if (videoStream?.codec?.contains("10") == true) 10 else 8,
                targetCodec = if (isDirectStream || decision == "DIRECT_PLAY") videoStream?.codec ?: "h264" else "h264",
                targetResolution = "${videoStream?.width ?: 1920}x${videoStream?.height ?: 1080}",
                strategy = if (decision == "DIRECT_PLAY" || isDirectStream) "copy" else "transcode"
            ),
            audio = AudioDecision(
                sourceCodec = audioStream?.codec,
                sourceChannels = audioStream?.channels,
                targetCodec = if (decision == "DIRECT_PLAY") audioStream?.codec ?: "aac" else "aac",
                targetChannels = if (decision == "DIRECT_PLAY") audioStream?.channels ?: 2 else 2,
                strategy = if (decision == "DIRECT_PLAY") "copy" else "transcode"
            )
        )
    }

    /**
     * Check if a media source can be directly played (both video and audio supported)
     */
    private suspend fun canDirectPlayMediaSource(
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        item: BaseItemDto,
        reasons: MutableList<String>? = null,
        bypassServerDecision: Boolean = false,
    ): Boolean {
        if (!bypassServerDecision) {
            if (!mediaSource.supportsDirectPlay && !mediaSource.supportsDirectStream) {
                SecureLogger.d(TAG, "❌ Server says DirectPlay=false, DirectStream=false")
                return false
            }
        }

        // Check container support
        val container = mediaSource.container
        if (!deviceCapabilities.canPlayContainer(container)) {
            SecureLogger.d(TAG, "❌ Container '$container' not supported for Direct Play")
            reasons?.add(ReasonCodes.CONTAINER_UNSUPPORTED)
            return false
        }

        // Check video codec support
        val videoStream = mediaSource.mediaStreams.findDefaultVideoStream()
        if (videoStream != null) {
            val videoCodec = videoStream.codec
            val width = videoStream.width ?: 0
            val height = videoStream.height ?: 0

            if (!deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)) {
                SecureLogger.d(TAG, "❌ Video codec '$videoCodec' at ${width}x$height not supported for Direct Play")
                reasons?.add(ReasonCodes.VIDEO_CODEC_UNSUPPORTED)
                return false
            }
        }

        // Check audio codec support with actual channel count.
        // Use strict check (no stereo fallback): Direct Play requires the device to fully
        // decode the audio at the source channel count without downmixing.
        val audioStream = mediaSource.mediaStreams.findDefaultAudioStream()
        if (audioStream != null) {
            val audioCodec = audioStream.codec
            val audioChannels = audioStream.channels ?: 2
            if (!deviceCapabilities.canPlayAudioCodecStrict(audioCodec, audioChannels)) {
                SecureLogger.d(TAG, "❌ Audio codec '$audioCodec' ($audioChannels ch) not supported for Direct Play (strict check)")
                reasons?.add(ReasonCodes.AUDIO_CODEC_UNSUPPORTED)
                return false
            }
        }

        // Check network conditions for high-bitrate content
        val bitrate = mediaSource.bitrate ?: 0
        if (!isNetworkSuitableForDirectPlay(bitrate)) {
            SecureLogger.d(TAG, "❌ Network conditions not suitable for Direct Play (bitrate: ${bitrate / 1_000_000} Mbps)")
            reasons?.add(ReasonCodes.NETWORK_BITRATE_EXCEEDED)
            return false
        }

        SecureLogger.d(TAG, "✅ Device CAN direct play: container=$container, video=${videoStream?.codec}, audio=${audioStream?.codec}")
        return true
    }

    /**
     * Check if a media source can be direct streamed (video supported, audio may need transcoding)
     * This is better than full transcoding when only audio is incompatible.
     */
    private suspend fun canDirectStreamMediaSource(
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        item: BaseItemDto,
        reasons: MutableList<String>? = null,
    ): Boolean {
        // Check video codec support (REQUIRED for direct stream)
        val videoStream = mediaSource.mediaStreams.findDefaultVideoStream()
        if (videoStream != null) {
            val videoCodec = videoStream.codec
            val width = videoStream.width ?: 0
            val height = videoStream.height ?: 0

            if (!deviceCapabilities.canPlayVideoCodec(videoCodec, width, height)) {
                SecureLogger.d(TAG, "❌ Video codec '$videoCodec' at ${width}x$height not supported for Direct Stream")
                return false
            }
        } else {
            // No video stream means this is audio-only content
            return false
        }

        // Check container support - for Direct Stream, we remux to TS or MP4.
        // Even if the SOURCE container is not natively supported, we can still Direct Stream
        // if the server can remux the video codec into a supported container.
        val container = mediaSource.container
        if (!deviceCapabilities.canPlayContainer(container)) {
            // If the source container is not supported, we can still direct stream if it's
            // a common video codec that supports remuxing to TS/MP4.
            val videoCodec = videoStream.codec?.lowercase() ?: ""
            val isRemuxable = videoCodec == "h264" || videoCodec == "h265" || videoCodec == "hevc" || videoCodec == "mpeg4"
            
            if (!isRemuxable) {
                SecureLogger.d(TAG, "❌ Container '$container' and codec '$videoCodec' not supported for Direct Stream")
                return false
            }
            SecureLogger.d(TAG, "⚠️ Container '$container' unsupported, but codec '$videoCodec' is remuxable - allowing Direct Stream")
        }

        // Check network conditions
        val bitrate = mediaSource.bitrate ?: 0
        if (!isNetworkSuitableForDirectPlay(bitrate)) {
            SecureLogger.d(TAG, "❌ Network conditions not suitable for Direct Stream (bitrate: ${bitrate / 1_000_000} Mbps)")
            return false
        }

        // Audio codec check is intentionally NOT included - that's the whole point of Direct Stream!
        val audioStream = mediaSource.mediaStreams.findDefaultAudioStream()
        val audioCodec = audioStream?.codec
        SecureLogger.d(TAG, "✅ Device CAN direct stream: video=${videoStream.codec}, audio=$audioCodec (may transcode audio)")
        return true
    }

    /**
     * Check if network conditions are suitable for Direct Play
     */
    private suspend fun isNetworkSuitableForDirectPlay(bitrate: Int): Boolean {
        val type = connectivityChecker.getNetworkType()
        val prefs = playbackPreferencesRepository.preferences.first()

        return when (type) {
            NetworkType.WIFI -> bitrate <= prefs.maxBitrateWifi
            NetworkType.CELLULAR -> bitrate <= prefs.maxBitrateCellular
            NetworkType.ETHERNET -> bitrate <= DIRECT_PLAY_MAX_BITRATE
            else -> bitrate <= LOW_QUALITY_THRESHOLD * 1_000_000
        }
    }

    /**
     * Get Direct Stream URL with audio-only transcoding.
     * This keeps the original video stream and only transcodes the audio,
     * which is much more efficient than transcoding both.
     */
    private suspend fun getDirectStreamWithAudioTranscode(
        item: BaseItemDto,
        mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo,
        playbackInfo: PlaybackInfoResponse,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val serverUrl = repository.getCurrentServer()?.url
        val mediaSourceId = mediaSource.id
        val playSessionId = playbackInfo.playSessionId
        val prefs = playbackPreferencesRepository.preferences.first()

        // Get source video info (we're keeping this unchanged)
        val sourceVideoStream = mediaSource.mediaStreams?.findDefaultVideoStream()
        val sourceAudioStream = mediaSource.mediaStreams?.findDefaultAudioStream()

        val maxAudioChannels = prefs.audioChannels.channels ?: 2

        // Build Direct Stream URL with audio transcoding parameters
        // VideoCodec=copy tells the server to NOT transcode video
        val directStreamUrl = streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = mediaSource.bitrate ?: 20_000_000, // Use source bitrate
            maxWidth = sourceVideoStream?.width ?: 1920,
            maxHeight = sourceVideoStream?.height ?: 1080,
            videoCodec = "copy", // CRITICAL: This tells server to copy video stream without transcoding
            audioCodec = "aac", // Transcode audio to AAC (universally supported)
            container = "ts", // Use TS container for better streaming compatibility
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            audioChannels = maxAudioChannels,
        )

        if (directStreamUrl.isNullOrBlank()) {
            SecureLogger.e(TAG, "Failed to generate Direct Stream URL with audio transcode for item $itemId")
            return PlaybackResult.Error("Unable to generate playback URL")
        }

        SecureLogger.d(
            TAG,
            "Direct Stream with audio transcode: video=${sourceVideoStream?.codec} (copy), " +
                "audio=${sourceAudioStream?.codec} → aac, " +
                "channels: ${sourceAudioStream?.channels} → $maxAudioChannels"
        )

        return PlaybackResult.Transcoding(
            url = directStreamUrl,
            targetBitrate = mediaSource.bitrate ?: 20_000_000,
            targetResolution = "${sourceVideoStream?.width ?: 1920}x${sourceVideoStream?.height ?: 1080}",
            targetVideoCodec = sourceVideoStream?.codec ?: "h264", // Original codec (video is copied)
            targetAudioCodec = "aac",
            targetContainer = "ts",
            reason = "Direct Stream: Original video (${sourceVideoStream?.codec}), audio transcoded (${sourceAudioStream?.codec} → aac)",
            playSessionId = playSessionId,
            isDirectStream = true,
        )
    }

    /**
     * Get optimal transcoding URL based on device capabilities and network conditions.
     * Returns PlaybackResult.Error if no valid URL can be generated.
     */
    private suspend fun getOptimalTranscodingUrl(
        item: BaseItemDto,
        playbackInfo: PlaybackInfoResponse,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackResult {
        val itemId = item.id.toString()
        val transcodingSource = playbackInfo.mediaSources.firstOrNull {
            it.supportsTranscoding || it.supportsDirectStream
        }
        val mediaSourceId = transcodingSource?.id
        val playSessionId = playbackInfo.playSessionId
        val deviceCaps = deviceCapabilities.getDirectPlayCapabilities()
        val prefs = playbackPreferencesRepository.preferences.first()

        // Get source video resolution and codec to prevent upscaling and unnecessary transcoding
        val sourceVideoStream = transcodingSource?.mediaStreams?.findDefaultVideoStream()
        val sourceWidth = sourceVideoStream?.width ?: 1920
        val sourceHeight = sourceVideoStream?.height ?: 1080
        val sourceVideoCodec = sourceVideoStream?.codec?.lowercase() ?: "h264"

        // Determine effective quality based on network AND user preference
        val networkQuality = connectivityChecker.getNetworkQuality()
        val effectiveQuality = if (prefs.transcodingQuality != TranscodingQuality.AUTO) {
            prefs.transcodingQuality
        } else {
            // Map ConnectivityQuality to TranscodingQuality
            when (networkQuality) {
                ConnectivityQuality.EXCELLENT -> TranscodingQuality.MAXIMUM
                ConnectivityQuality.GOOD -> TranscodingQuality.HIGH
                ConnectivityQuality.FAIR -> TranscodingQuality.MEDIUM
                ConnectivityQuality.POOR -> TranscodingQuality.LOW
            }
        }

        val transcodingParams = when (effectiveQuality) {
            TranscodingQuality.MAXIMUM -> TranscodingParams(
                maxBitrate = 60_000_000, // 60 Mbps
                maxWidth = minOf(if (deviceCaps.supports4K) 3840 else 1920, sourceWidth),
                maxHeight = minOf(if (deviceCaps.supports4K) 2160 else 1080, sourceHeight),
                videoCodec = if (deviceCaps.supportedVideoCodecs.contains(sourceVideoCodec)) sourceVideoCodec else getBestVideoCodec(deviceCaps.supportedVideoCodecs),
                audioCodec = getBestAudioCodec(deviceCaps.supportedAudioCodecs),
                container = "mp4",
            )
            TranscodingQuality.HIGH -> TranscodingParams(
                maxBitrate = 20_000_000, // 20 Mbps
                maxWidth = minOf(1920, sourceWidth),
                maxHeight = minOf(1080, sourceHeight),
                videoCodec = if (deviceCaps.supportedVideoCodecs.contains(sourceVideoCodec)) sourceVideoCodec else "h264",
                audioCodec = "aac",
                container = "mp4",
            )
            TranscodingQuality.MEDIUM -> TranscodingParams(
                maxBitrate = 8_000_000, // 8 Mbps
                maxWidth = minOf(1280, sourceWidth),
                maxHeight = minOf(720, sourceHeight),
                videoCodec = "h264",
                audioCodec = "aac",
                container = "mp4",
            )
            TranscodingQuality.LOW -> TranscodingParams(
                maxBitrate = 3_000_000, // 3 Mbps
                maxWidth = minOf(854, sourceWidth),
                maxHeight = minOf(480, sourceHeight),
                videoCodec = "h264",
                audioCodec = "aac",
                container = "mp4",
            )
            TranscodingQuality.AUTO -> {
                // This branch shouldn't be reached due to effectiveQuality logic above
                TranscodingParams(20_000_000, 1920, 1080, "h264", "aac", "mp4")
            }
        }

        val maxAudioChannels = prefs.audioChannels.channels ?: 2

        SecureLogger.d(
            TAG,
            "Transcoding params: source=${sourceWidth}x$sourceHeight, " +
                "target=${transcodingParams.maxWidth}x${transcodingParams.maxHeight}, " +
                "bitrate=${transcodingParams.maxBitrate / 1_000_000}Mbps, " +
                "quality=$effectiveQuality, channels=$maxAudioChannels",
        )

        // Try primary transcoding URL
        val transcodingUrl = streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = transcodingParams.maxBitrate,
            maxWidth = transcodingParams.maxWidth,
            maxHeight = transcodingParams.maxHeight,
            videoCodec = transcodingParams.videoCodec,
            audioCodec = transcodingParams.audioCodec,
            container = transcodingParams.container,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            audioChannels = maxAudioChannels,
        )

        // Return error if no valid URL could be generated
        if (transcodingUrl.isNullOrBlank()) {
            SecureLogger.e(TAG, "Failed to generate any valid transcoding URL for item $itemId")
            return PlaybackResult.Error("Unable to generate playback URL. Please check server connection.")
        }

        return PlaybackResult.Transcoding(
            url = transcodingUrl,
            targetBitrate = transcodingParams.maxBitrate,
            targetResolution = "${transcodingParams.maxWidth}x${transcodingParams.maxHeight}",
            targetVideoCodec = transcodingParams.videoCodec,
            targetAudioCodec = transcodingParams.audioCodec,
            targetContainer = transcodingParams.container,
            reason = "Optimized for $effectiveQuality quality",
            playSessionId = playSessionId,
        )
    }

    /**
     * Get playback info from server using repository method
     */
    private suspend fun getPlaybackInfo(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponse? {
        return try {
            repository.getPlaybackInfo(itemId, audioStreamIndex, subtitleStreamIndex)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SecureLogger.e(TAG, "Failed to get playback info for item $itemId", e)
            null
        }
    }

    /**
     * Get the best video codec from supported list
     */
    private fun getBestVideoCodec(supportedCodecs: List<String>): String {
        return when {
            supportedCodecs.contains("h265") || supportedCodecs.contains("hevc") -> "h265"
            supportedCodecs.contains("h264") -> "h264"
            supportedCodecs.contains("vp9") -> "vp9"
            supportedCodecs.contains("vp8") -> "vp8"
            else -> "h264" // Fallback
        }
    }

    /**
     * Get the best audio codec from supported list
     */
    private fun getBestAudioCodec(supportedCodecs: List<String>): String {
        return when {
            supportedCodecs.contains("opus") -> "opus"
            supportedCodecs.contains("aac") -> "aac"
            supportedCodecs.contains("mp3") -> "mp3"
            else -> "aac" // Fallback
        }
    }

    /**
     * Extract video codec from media source
     */
    private fun getVideoCodec(mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo): String? {
        return mediaSource.mediaStreams.findDefaultVideoStream()?.codec
    }

    /**
     * Extract audio codec from media source
     */
    private fun getAudioCodec(mediaSource: org.jellyfin.sdk.model.api.MediaSourceInfo): String? {
        return mediaSource.mediaStreams?.find { it.type == MediaStreamType.AUDIO }?.codec
    }

    private fun buildServerUrl(serverUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val normalizedServer = serverUrl.removeSuffix("/")
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedServer + normalizedPath
    }
}

/**
 * Structured decision trace for playback selection.
 * Used for telemetry and debugging to understand why a specific playback method was chosen.
 */
data class PlaybackDecisionTrace(
    val sessionId: String,
    val decision: String,
    val ruleId: String,
    val reasonCodes: List<String>,
    val audio: AudioDecision? = null,
    val video: VideoDecision? = null,
    val deviceClass: String,
    val serverVersion: String? = null,
    val networkClass: String,
    val mediaFingerprint: String? = null,
)

data class AudioDecision(
    val sourceCodec: String?,
    val sourceChannels: Int?,
    val targetCodec: String,
    val targetChannels: Int,
    val strategy: String, // "copy" or "transcode"
)

data class VideoDecision(
    val sourceCodec: String?,
    val sourceBitDepth: Int?,
    val targetCodec: String,
    val targetResolution: String,
    val strategy: String, // "copy" or "transcode"
)

/**
 * Transcoding parameters
 */
private data class TranscodingParams(
    val maxBitrate: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val videoCodec: String,
    val audioCodec: String,
    val container: String,
)

/**
 * Playback result sealed class
 */
sealed class PlaybackResult {
    data class DirectPlay(
        val url: String,
        val container: String,
        val videoCodec: String?,
        val audioCodec: String?,
        val bitrate: Int,
        val reason: String,
        val reasonCodes: List<String> = emptyList(),
        val playSessionId: String? = null,
        val decisionTrace: PlaybackDecisionTrace? = null,
    ) : PlaybackResult()

    data class Transcoding(
        val url: String,
        val targetBitrate: Int,
        val targetResolution: String,
        val targetVideoCodec: String,
        val targetAudioCodec: String,
        val targetContainer: String,
        val reason: String,
        val reasonCodes: List<String> = emptyList(),
        val playSessionId: String? = null,
        val decisionTrace: PlaybackDecisionTrace? = null,
        /** True when the video stream is copied without re-encoding (only audio is transcoded). */
        val isDirectStream: Boolean = false,
    ) : PlaybackResult()

    data class Error(val message: String) : PlaybackResult()
}
