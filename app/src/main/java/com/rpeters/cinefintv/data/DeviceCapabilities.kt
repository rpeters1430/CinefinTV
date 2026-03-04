package com.rpeters.cinefintv.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import com.rpeters.cinefintv.utils.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCapabilities @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "DeviceCapabilities"

        // Supported container formats by Android natively
        val SUPPORTED_CONTAINERS = setOf(
            "mp4", "m4v", "3gp", "3gpp", "3g2", "3gpp2",
            "webm", "mkv", "avi", "mov", "flv", "asf", "wmv",
            "ts", "mpegts", "m2ts",
        )

        // Supported video codecs
        val SUPPORTED_VIDEO_CODECS = setOf(
            "h264", "h265", "hevc", "mpeg4", "mpeg2", "h263",
            "vp8", "vp9", "av1", "mpeg1",
        )

        // Supported audio codecs
        val SUPPORTED_AUDIO_CODECS = setOf(
            "aac", "mp3", "ac3", "eac3", "dts", "flac", "vorbis",
            "opus", "pcm", "amr", "amrnb", "amrwb",
        )
    }

    private var supportedVideoCodecs: Set<String>? = null
    private var supportedAudioCodecs: Set<String>? = null
    private var maxResolution: Pair<Int, Int>? = null
    private var hdrSupported: Boolean? = null
    private var totalRAM: Long? = null
    private var deviceId: String? = null

    /**
     * Get unique device ID for API requests
     */
    fun getDeviceId(): String {
        deviceId?.let { return it }
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        deviceId = id
        return id
    }

    /**
     * Check if the device can directly play a given media format
     */
    fun canDirectPlay(
        container: String?,
        videoCodec: String?,
        audioCodec: String?,
        width: Int = 0,
        height: Int = 0,
    ): Boolean {
        return canPlayContainer(container) &&
            canPlayVideoCodec(videoCodec, width, height) &&
            canPlayAudioCodec(audioCodec)
    }

    /**
     * Check if container format is supported
     */
    fun canPlayContainer(container: String?): Boolean {
        if (container.isNullOrBlank()) return false
        val normalizedContainer = container.lowercase().removePrefix(".")
        return SUPPORTED_CONTAINERS.contains(normalizedContainer)
    }

    /**
     * Check if video codec is supported at given resolution
     */
    fun canPlayVideoCodec(codec: String?, width: Int = 0, height: Int = 0): Boolean {
        if (codec.isNullOrBlank()) return true // Audio-only content

        val normalizedCodec = normalizeVideoCodec(codec.lowercase())
        if (!SUPPORTED_VIDEO_CODECS.contains(normalizedCodec)) return false

        // Check resolution limits if specified
        if (width > 0 && height > 0) {
            val maxRes = getMaxSupportedResolution()
            if (width > maxRes.first || height > maxRes.second) {
                SecureLogger.v(TAG, "Resolution ${width}x$height exceeds device maximum ${maxRes.first}x${maxRes.second}")
                return false
            }
        }

        return isCodecSupported(normalizedCodec, true)
    }

    /**
     * Check if audio codec is supported
     */
    fun canPlayAudioCodec(codec: String?): Boolean {
        return canPlayAudioCodec(codec, channels = 2)
    }

    /**
     * Check if audio codec is supported with the given channel count.
     * Many devices report EAC3/AC3/DTS support for stereo but fail on surround (5.1/7.1).
     * Uses stereo fallback: if the device has a stereo decoder, we consider the codec
     * "supported" because ExoPlayer can downmix in a transcoding/direct-stream context.
     */
    fun canPlayAudioCodec(codec: String?, channels: Int): Boolean {
        if (codec.isNullOrBlank()) return true // Video-only content

        val normalizedCodec = normalizeAudioCodec(codec.lowercase())
        if (!SUPPORTED_AUDIO_CODECS.contains(normalizedCodec)) return false

        return isAudioCodecSupported(normalizedCodec, channels, strict = false)
    }

    /**
     * Strict audio codec check — no stereo downmix fallback.
     * Use this when determining if a file can be truly direct-played without any
     * audio conversion. If this returns false, Direct Stream (audio transcode) is needed.
     */
    fun canPlayAudioCodecStrict(codec: String?, channels: Int): Boolean {
        if (codec.isNullOrBlank()) return true // Video-only content

        val normalizedCodec = normalizeAudioCodec(codec.lowercase())
        if (!SUPPORTED_AUDIO_CODECS.contains(normalizedCodec)) return false

        return isAudioCodecSupported(normalizedCodec, channels, strict = true)
    }

    /**
     * Get optimal direct play parameters
     */
    fun getDirectPlayCapabilities(): DirectPlayCapabilities {
        return DirectPlayCapabilities(
            supportedContainers = SUPPORTED_CONTAINERS.toList(),
            supportedVideoCodecs = getSupportedVideoCodecs(),
            supportedAudioCodecs = getSupportedAudioCodecs(),
            maxResolution = getMaxSupportedResolution(),
            supportsHdr = supportsHdrDisplay(),
            supports4K = supports4K(),
            maxBitrate = getMaxSupportedBitrate(),
            networkCapabilities = getNetworkCapabilities(),
            hardwareAcceleration = getHardwareAccelerationInfo(),
            deviceTier = getDevicePerformanceProfile(),
            maxVideoBitDepth = getMaxVideoBitDepth(),
            hevc10BitSupported = isHevc10BitSupported(),
            hdrTypes = getSupportedHdrTypes(),
            maxAudioChannelsByCodec = getMaxAudioChannelsByCodec(),
        )
    }

    private fun getMaxVideoBitDepth(): Int {
        return if (isHevc10BitSupported()) 10 else 8
    }

    private fun isHevc10BitSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false

        return try {
            val mimeType = "video/hevc"
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            
            // Check all decoders for HEVC 10-bit profiles
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                
                try {
                    val capabilities = codecInfo.getCapabilitiesForType(mimeType)
                    val supportsMain10 = capabilities.profileLevels.any { 
                        it.profile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                        it.profile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                        it.profile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                    }
                    if (supportsMain10) return true
                } catch (e: Exception) {
                    // Ignore codecs that don't support the mime type
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun getSupportedHdrTypes(): List<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyList()

        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)

        @Suppress("DEPRECATION")
        return display?.hdrCapabilities?.supportedHdrTypes?.toList() ?: emptyList()
    }

    private fun getMaxAudioChannelsByCodec(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (codec in SUPPORTED_AUDIO_CODECS) {
            // Check common channel counts: 8 (7.1), 6 (5.1), 2 (stereo)
            val channels = when {
                canPlayAudioCodec(codec, 8) -> 8
                canPlayAudioCodec(codec, 6) -> 6
                canPlayAudioCodec(codec, 2) -> 2
                else -> 0
            }
            if (channels > 0) {
                result[codec] = channels
            }
        }
        return result
    }

    /**
     * Get enhanced codec support information with performance ratings
     */
    fun getEnhancedCodecSupport(): CodecSupportInfo {
        return CodecSupportInfo(
            videoCodecs = getVideoCodecSupport(),
            audioCodecs = getAudioCodecSupport(),
            containerFormats = getContainerSupport(),
            performanceProfile = getDevicePerformanceProfile(),
        )
    }

    /**
     * Check if specific media can be direct played with confidence score
     */
    fun analyzeDirectPlayCompatibility(
        container: String?,
        videoCodec: String?,
        audioCodec: String?,
        width: Int = 0,
        height: Int = 0,
        bitrate: Int = 0,
    ): DirectPlayAnalysis {
        var score = 100
        val issues = mutableListOf<String>()

        // Container check
        if (!canPlayContainer(container)) {
            score -= 100
            issues.add("Container '$container' not supported")
            return DirectPlayAnalysis(false, 0, issues, "Unsupported container format")
        }

        // Video codec check with performance consideration
        if (videoCodec != null) {
            val videoSupport = getVideoCodecSupport()[normalizeVideoCodec(videoCodec.lowercase())]
            when {
                videoSupport == null || videoSupport.support == CodecSupport.NOT_SUPPORTED -> {
                    score -= 100
                    issues.add("Video codec '$videoCodec' not supported")
                }
                videoSupport.support == CodecSupport.SOFTWARE_ONLY -> {
                    score -= 20
                    issues.add("Video codec '$videoCodec' only supported via software decoding")
                }
                videoSupport.support == CodecSupport.HARDWARE_ACCELERATED -> {
                    score += 10 // Bonus for hardware acceleration
                }
            }

            // Resolution check
            if (width > 0 && height > 0) {
                val maxRes = getMaxSupportedResolution()
                if (width > maxRes.first || height > maxRes.second) {
                    score -= 50
                    issues.add("Resolution ${width}x$height exceeds maximum ${maxRes.first}x${maxRes.second}")
                }
            }
        }

        // Audio codec check
        if (audioCodec != null) {
            val audioSupport = getAudioCodecSupport()[normalizeAudioCodec(audioCodec.lowercase())]
            when {
                audioSupport == null || audioSupport.support == CodecSupport.NOT_SUPPORTED -> {
                    score -= 100
                    issues.add("Audio codec '$audioCodec' not supported")
                }
                audioSupport.support == CodecSupport.SOFTWARE_ONLY -> {
                    score -= 10
                    issues.add("Audio codec '$audioCodec' only supported via software decoding")
                }
            }
        }

        // Bitrate check
        if (bitrate > 0) {
            val maxBitrate = getMaxSupportedBitrate()
            if (bitrate > maxBitrate) {
                score -= 30
                issues.add("Bitrate ${bitrate / 1_000_000}Mbps exceeds recommended maximum ${maxBitrate / 1_000_000}Mbps")
            }
        }

        val canDirectPlay = score > 0
        val recommendation = when {
            score >= 90 -> "Excellent Direct Play compatibility"
            score >= 70 -> "Good Direct Play compatibility"
            score >= 50 -> "Fair Direct Play compatibility - may have performance issues"
            score > 0 -> "Poor Direct Play compatibility - transcoding recommended"
            else -> "Direct Play not possible"
        }

        return DirectPlayAnalysis(canDirectPlay, score, issues, recommendation)
    }

    private fun getSupportedVideoCodecs(): List<String> {
        if (supportedVideoCodecs == null) {
            supportedVideoCodecs = detectSupportedCodecs(true)
        }
        // Use safe call with fallback to prevent NPE
        return supportedVideoCodecs?.toList() ?: emptyList()
    }

    private fun getSupportedAudioCodecs(): List<String> {
        if (supportedAudioCodecs == null) {
            supportedAudioCodecs = detectSupportedCodecs(false)
        }
        // Use safe call with fallback to prevent NPE
        return supportedAudioCodecs?.toList() ?: emptyList()
    }

    private fun detectSupportedCodecs(isVideo: Boolean): Set<String> {
        val supportedCodecs = mutableSetOf<String>()

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue // We only care about decoders

                for (type in codecInfo.supportedTypes) {
                    val codec = mimeTypeToCodec(type, isVideo)
                    if (codec != null) {
                        supportedCodecs.add(codec)
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Failed to detect supported codecs", e)
            // Fallback to common codecs
            if (isVideo) {
                supportedCodecs.addAll(setOf("h264", "mpeg4", "h263", "vp8"))
            } else {
                supportedCodecs.addAll(setOf("aac", "mp3", "vorbis"))
            }
        }

        SecureLogger.v(TAG, "${if (isVideo) "Video" else "Audio"} codecs supported: $supportedCodecs")
        return supportedCodecs
    }

    private fun mimeTypeToCodec(mimeType: String, isVideo: Boolean): String? {
        return when (mimeType.lowercase()) {
            // Video codecs
            "video/avc" -> if (isVideo) "h264" else null
            "video/h264" -> if (isVideo) "h264" else null
            "video/hevc" -> if (isVideo) "h265" else null
            "video/h265" -> if (isVideo) "h265" else null
            "video/mp4v-es" -> if (isVideo) "mpeg4" else null
            "video/3gpp" -> if (isVideo) "h263" else null
            "video/x-vnd.on2.vp8" -> if (isVideo) "vp8" else null
            "video/x-vnd.on2.vp9" -> if (isVideo) "vp9" else null
            "video/av01" -> if (isVideo) "av1" else null
            // Audio codecs
            "audio/mp4a-latm" -> if (!isVideo) "aac" else null
            "audio/mpeg" -> if (!isVideo) "mp3" else null
            "audio/mpeg-l1" -> if (!isVideo) "mp3" else null
            "audio/mpeg-l2" -> if (!isVideo) "mp3" else null
            "audio/ac3" -> if (!isVideo) "ac3" else null
            "audio/eac3" -> if (!isVideo) "eac3" else null
            "audio/vorbis" -> if (!isVideo) "vorbis" else null
            "audio/opus" -> if (!isVideo) "opus" else null
            "audio/flac" -> if (!isVideo) "flac" else null
            "audio/raw" -> if (!isVideo) "pcm" else null
            else -> null
        }
    }

    private fun normalizeVideoCodec(codec: String): String {
        return when (codec.lowercase()) {
            "avc", "x264" -> "h264"
            "hevc", "x265" -> "h265"
            "vp08" -> "vp8"
            "vp09" -> "vp9"
            else -> codec
        }
    }

    private fun normalizeAudioCodec(codec: String): String {
        return when (codec.lowercase()) {
            "aac-lc", "aac-he", "aac-he-v2" -> "aac"
            "mp3float" -> "mp3"
            "ac-3" -> "ac3"
            "e-ac-3" -> "eac3"
            else -> codec
        }
    }

    /**
     * Check if an audio codec is supported with the given channel count.
     *
     * @param strict When true, skip the stereo downmix fallback. Use strict=true for
     *               direct play decisions where ExoPlayer won't perform downmixing.
     *               Use strict=false (default) for direct stream / transcoding decisions
     *               where ExoPlayer CAN downmix multi-channel to stereo in software.
     */
    private fun isAudioCodecSupported(codec: String, channels: Int, strict: Boolean = false): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val mimeType = codecToMimeType(codec, false) ?: return false
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

                // First, try to find a decoder for the exact channel count
                val format = android.media.MediaFormat.createAudioFormat(mimeType, 48_000, channels)
                val decoderName = codecList.findDecoderForFormat(format)

                if (decoderName != null) {
                    SecureLogger.d(TAG, "Found decoder for $codec with $channels channels: $decoderName")
                    return true
                }

                // For surround sound codecs (>2 channels), optionally fall back to stereo.
                // Stereo fallback is only appropriate when ExoPlayer will downmix in software
                // (i.e., during Direct Stream / transcoding). NOT for Direct Play decisions.
                if (!strict && channels > 2 && isSurroundSoundCodec(codec)) {
                    val stereoFormat = android.media.MediaFormat.createAudioFormat(mimeType, 48_000, 2)
                    val stereoDecoderName = codecList.findDecoderForFormat(stereoFormat)

                    if (stereoDecoderName != null) {
                        SecureLogger.d(TAG, "Found stereo decoder for $codec (will downmix from $channels ch): $stereoDecoderName")
                        return true
                    } else {
                        SecureLogger.d(TAG, "No decoder found for $codec (tried $channels ch and stereo fallback)")
                    }
                } else {
                    SecureLogger.d(TAG, "No decoder found for $codec with $channels channels (strict=$strict)")
                }

                false
            } catch (e: Exception) {
                SecureLogger.w(TAG, "Failed to check audio codec support for $codec ($channels ch)", e)
                false
            }
        } else {
            val codecs = getSupportedAudioCodecs()
            codecs.contains(codec.lowercase())
        }
    }

    /**
     * Check if a codec is a surround sound codec that supports multi-channel audio.
     */
    private fun isSurroundSoundCodec(codec: String): Boolean {
        return when (codec.lowercase()) {
            "eac3", "ac3", "dts", "dtshd", "truehd" -> true
            else -> false
        }
    }

    private fun isCodecSupported(codec: String, isVideo: Boolean): Boolean {
        // For Android 5.0+ we can rely on MediaCodecList
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val mimeType = codecToMimeType(codec, isVideo) ?: return false
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                val format = if (isVideo) {
                    android.media.MediaFormat.createVideoFormat(mimeType, 1920, 1080)
                } else {
                    android.media.MediaFormat.createAudioFormat(mimeType, 48_000, 2)
                }
                codecList.findDecoderForFormat(format) != null
            } catch (e: Exception) {
                SecureLogger.w(TAG, "Failed to check codec support for $codec", e)
                // Safer to assume NOT supported if we can't check
                // This prevents playback failures when codec is actually unsupported
                false
            }
        } else {
            // On older Android versions, check against detected codec list
            val codecs = if (isVideo) getSupportedVideoCodecs() else getSupportedAudioCodecs()
            codecs.contains(codec.lowercase())
        }
    }

    private fun codecToMimeType(codec: String, isVideo: Boolean): String? {
        return when (codec.lowercase()) {
            "h264" -> if (isVideo) "video/avc" else null
            "h265", "hevc" -> if (isVideo) "video/hevc" else null
            "mpeg4" -> if (isVideo) "video/mp4v-es" else null
            "h263" -> if (isVideo) "video/3gpp" else null
            "vp8" -> if (isVideo) "video/x-vnd.on2.vp8" else null
            "vp9" -> if (isVideo) "video/x-vnd.on2.vp9" else null
            "av1" -> if (isVideo) "video/av01" else null
            "aac" -> if (!isVideo) "audio/mp4a-latm" else null
            "mp3" -> if (!isVideo) "audio/mpeg" else null
            "ac3" -> if (!isVideo) "audio/ac3" else null
            "eac3" -> if (!isVideo) "audio/eac3" else null
            "dts" -> if (!isVideo) "audio/dts" else null
            "dtshd" -> if (!isVideo) "audio/dts-hd" else null
            "truehd" -> if (!isVideo) "audio/true-hd" else null
            "vorbis" -> if (!isVideo) "audio/vorbis" else null
            "opus" -> if (!isVideo) "audio/opus" else null
            "flac" -> if (!isVideo) "audio/flac" else null
            "pcm" -> if (!isVideo) "audio/raw" else null
            else -> null
        }
    }

    private fun getMaxSupportedResolution(): Pair<Int, Int> {
        if (maxResolution == null) {
            maxResolution = try {
                val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                var maxWidth = 1280 // Start with a reasonable minimum
                var maxHeight = 720 // Start with 720p as minimum
                var foundValidCodec = false

                for (codecInfo in codecList.codecInfos) {
                    if (codecInfo.isEncoder) continue

                    for (type in codecInfo.supportedTypes) {
                        if (type.startsWith("video/")) {
                            try {
                                val capabilities = codecInfo.getCapabilitiesForType(type)
                                val videoCapabilities = capabilities.videoCapabilities
                                if (videoCapabilities != null) {
                                    val supportedWidths = videoCapabilities.supportedWidths
                                    val supportedHeights = videoCapabilities.supportedHeights

                                    // Only update if we get reasonable values
                                    if (supportedWidths.upper > maxWidth && supportedWidths.upper >= 1280) {
                                        maxWidth = supportedWidths.upper
                                        foundValidCodec = true
                                    }
                                    if (supportedHeights.upper > maxHeight && supportedHeights.upper >= 720) {
                                        maxHeight = supportedHeights.upper
                                        foundValidCodec = true
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore codec-specific errors
                            }
                        }
                    }
                }

                // If we didn't find any valid codec info, use device display metrics as fallback
                if (!foundValidCodec) {
                    try {
                        val displayMetrics = context.resources.displayMetrics
                        val displayWidth = displayMetrics.widthPixels
                        val displayHeight = displayMetrics.heightPixels

                        // Use display resolution as a reasonable fallback, but cap at 4K
                        maxWidth = minOf(displayWidth, 3840)
                        maxHeight = minOf(displayHeight, 2160)
                        SecureLogger.d(TAG, "Using display metrics as resolution fallback: ${maxWidth}x$maxHeight")
                    } catch (e: Exception) {
                        // Final fallback - assume 1080p for modern devices
                        maxWidth = 1920
                        maxHeight = 1080
                        SecureLogger.w(TAG, "Using final fallback resolution: 1080p")
                    }
                } else {
                    // Cap detected resolution at reasonable limits (4K)
                    maxWidth = minOf(maxWidth, 3840)
                    maxHeight = minOf(maxHeight, 2160)
                    SecureLogger.d(TAG, "Detected max resolution: ${maxWidth}x$maxHeight")

                    // Log device info for debugging
                    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    val displayMetrics = context.resources.displayMetrics
                    SecureLogger.d(TAG, "Device: $deviceModel, Display: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
                }

                Pair(maxWidth, maxHeight)
            } catch (e: CancellationException) {
                throw e
            }
        }
        // Use safe call with safe default to prevent NPE
        return maxResolution ?: Pair(1920, 1080)
    }

    fun supportsHdrDisplay(): Boolean {
        hdrSupported?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            hdrSupported = false
            return false
        }

        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)

        @Suppress("DEPRECATION")
        val hdrTypes = display?.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        val supported = hdrTypes.isNotEmpty()
        hdrSupported = supported
        return supported
    }

    private fun supports4K(): Boolean {
        val maxRes = getMaxSupportedResolution()
        return maxRes.first >= 3840 && maxRes.second >= 2160
    }

    /**
     * Get maximum supported bitrate based on device performance
     */
    private fun getMaxSupportedBitrate(): Int {
        val performanceProfile = getDevicePerformanceProfile()
        return when (performanceProfile) {
            DevicePerformanceProfile.HIGH_END -> 100_000_000 // 100 Mbps
            DevicePerformanceProfile.MID_RANGE -> 50_000_000 // 50 Mbps
            DevicePerformanceProfile.LOW_END -> 25_000_000 // 25 Mbps
        }
    }

    /**
     * Get network capabilities information
     */
    private fun getNetworkCapabilities(): NetworkCapabilityInfo {
        // This could be expanded to include actual network monitoring
        return NetworkCapabilityInfo(
            supportsHighBitrate = true,
            estimatedBandwidth = -1, // Unknown
            connectionType = "unknown",
        )
    }

    /**
     * Get hardware acceleration information
     */
    private fun getHardwareAccelerationInfo(): HardwareAccelerationInfo {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val hardwareCodecs = mutableSetOf<String>()
            val softwareCodecs = mutableSetOf<String>()

            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue

                // Check if this is a hardware or software decoder
                val isHardware = !codecInfo.name.lowercase().contains("software") &&
                    !codecInfo.name.lowercase().contains("omx.google") &&
                    !codecInfo.name.lowercase().contains("c2.android")

                for (type in codecInfo.supportedTypes) {
                    val codec = mimeTypeToCodec(type, type.startsWith("video/"))
                    if (codec != null) {
                        if (isHardware) {
                            hardwareCodecs.add(codec)
                        } else {
                            softwareCodecs.add(codec)
                        }
                    }
                }
            }

            SecureLogger.d(TAG, "Hardware codecs: $hardwareCodecs")
            SecureLogger.d(TAG, "Software codecs: $softwareCodecs")

            HardwareAccelerationInfo(
                supportedCodecs = hardwareCodecs.toList(),
                hasHardwareDecoding = hardwareCodecs.isNotEmpty(),
                softwareOnlyCodecs = softwareCodecs.toList(),
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get detailed video codec support information
     */
    private fun getVideoCodecSupport(): Map<String, CodecSupportDetail> {
        val support = mutableMapOf<String, CodecSupportDetail>()

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

            for (codec in SUPPORTED_VIDEO_CODECS) {
                val mimeType = codecToMimeType(codec, true)
                if (mimeType != null) {
                    val codecName = codecList.findDecoderForFormat(
                        android.media.MediaFormat.createVideoFormat(mimeType, 1920, 1080),
                    )

                    val codecInfo = codecName?.let { name ->
                        codecList.codecInfos.firstOrNull { it.name == name }
                    }

                    val supportLevel = when {
                        codecInfo == null -> CodecSupport.NOT_SUPPORTED
                        else -> {
                            // Check if this is a hardware decoder
                            val isHardware = !codecInfo.name.lowercase().contains("software") &&
                                !codecInfo.name.lowercase().contains("omx.google") &&
                                !codecInfo.name.lowercase().contains("c2.android")

                            if (isHardware) {
                                CodecSupport.HARDWARE_ACCELERATED
                            } else {
                                CodecSupport.SOFTWARE_ONLY
                            }
                        }
                    }

                    support[codec] = CodecSupportDetail(
                        support = supportLevel,
                        maxResolution = if (codecInfo != null) getCodecMaxResolution(codecInfo, mimeType) else Pair(1920, 1080),
                        performanceRating = getCodecPerformanceRating(codec, supportLevel),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        }

        return support
    }

    /**
     * Get detailed audio codec support information
     */
    private fun getAudioCodecSupport(): Map<String, CodecSupportDetail> {
        val support = mutableMapOf<String, CodecSupportDetail>()

        for (codec in SUPPORTED_AUDIO_CODECS) {
            val mimeType = codecToMimeType(codec, false)
            if (mimeType != null && isCodecSupported(codec, false)) {
                support[codec] = CodecSupportDetail(
                    support = CodecSupport.HARDWARE_ACCELERATED, // Audio is typically hardware accelerated
                    maxResolution = Pair(0, 0), // Not applicable for audio
                    performanceRating = 5, // Audio codecs typically perform well
                )
            }
        }

        return support
    }

    /**
     * Get container format support information
     */
    private fun getContainerSupport(): Map<String, Boolean> {
        return SUPPORTED_CONTAINERS.associateWith { true }
    }

    /**
     * Determine device performance profile
     */
    private fun getDevicePerformanceProfile(): DevicePerformanceProfile {
        val maxRes = getMaxSupportedResolution()
        val supports4K = supports4K()
        val ramSize = getTotalRAM()

        return when {
            supports4K && maxRes.first >= 3840 && ramSize >= 6_000_000_000 -> DevicePerformanceProfile.HIGH_END
            maxRes.first >= 1920 && ramSize >= 3_000_000_000 -> DevicePerformanceProfile.MID_RANGE
            else -> DevicePerformanceProfile.LOW_END
        }
    }

    /**
     * Get total device RAM (approximate) - cached to prevent repeated detection
     */
    private fun getTotalRAM(): Long {
        totalRAM?.let { return it }

        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)

            val totalMemory = memInfo.totalMem
            SecureLogger.d(TAG, "Detected total RAM: ${totalMemory / 1_000_000L}MB")

            val result = if (totalMemory > 0) {
                totalMemory
            } else {
                // Fallback for devices that don't expose memory info
                SecureLogger.w(TAG, "Could not detect RAM, using fallback")
                4_000_000_000L // 4GB assumption for modern devices
            }

            totalRAM = result
            result
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get maximum resolution supported by a specific codec
     */
    private fun getCodecMaxResolution(
        codecInfo: android.media.MediaCodecInfo?,
        mimeType: String,
    ): Pair<Int, Int> {
        return try {
            codecInfo?.getCapabilitiesForType(mimeType)?.videoCapabilities?.let { videoCaps ->
                Pair(videoCaps.supportedWidths.upper, videoCaps.supportedHeights.upper)
            } ?: Pair(1920, 1080)
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Get performance rating for a codec (1-10 scale)
     */
    private fun getCodecPerformanceRating(codec: String, support: CodecSupport): Int {
        val baseRating = when (codec.lowercase()) {
            "h264" -> 9 // Excellent compatibility
            "h265", "hevc" -> 7 // Good, but newer
            "vp9" -> 6 // Decent, WebM focused
            "vp8" -> 5 // Older codec
            "av1" -> 4 // Very new, limited support
            else -> 3
        }

        return when (support) {
            CodecSupport.HARDWARE_ACCELERATED -> baseRating
            CodecSupport.SOFTWARE_ONLY -> maxOf(1, baseRating - 3)
            CodecSupport.NOT_SUPPORTED -> 0
        }
    }
}

/**
 * Codec support level
 */
enum class CodecSupport {
    HARDWARE_ACCELERATED,
    SOFTWARE_ONLY,
    NOT_SUPPORTED,
}

/**
 * Device performance profile
 */
enum class DevicePerformanceProfile {
    HIGH_END,
    MID_RANGE,
    LOW_END,
}

/**
 * Enhanced direct play capabilities with additional information
 */
data class DirectPlayCapabilities(
    val supportedContainers: List<String>,
    val supportedVideoCodecs: List<String>,
    val supportedAudioCodecs: List<String>,
    val maxResolution: Pair<Int, Int>,
    val supportsHdr: Boolean,
    val supports4K: Boolean,
    val maxBitrate: Int,
    val networkCapabilities: NetworkCapabilityInfo,
    val hardwareAcceleration: HardwareAccelerationInfo,
    val deviceTier: DevicePerformanceProfile = DevicePerformanceProfile.LOW_END,
    val maxVideoBitDepth: Int = 8,
    val hevc10BitSupported: Boolean = false,
    val hdrTypes: List<Int> = emptyList(),
    val maxAudioChannelsByCodec: Map<String, Int> = emptyMap(),
)

/**
 * Detailed codec support information
 */
data class CodecSupportDetail(
    val support: CodecSupport,
    val maxResolution: Pair<Int, Int>,
    val performanceRating: Int, // 1-10 scale
)

/**
 * Comprehensive codec support information
 */
data class CodecSupportInfo(
    val videoCodecs: Map<String, CodecSupportDetail>,
    val audioCodecs: Map<String, CodecSupportDetail>,
    val containerFormats: Map<String, Boolean>,
    val performanceProfile: DevicePerformanceProfile,
)

/**
 * Direct play analysis result
 */
data class DirectPlayAnalysis(
    val canDirectPlay: Boolean,
    val confidenceScore: Int, // 0-100
    val issues: List<String>,
    val recommendation: String,
)

/**
 * Network capability information
 */
data class NetworkCapabilityInfo(
    val supportsHighBitrate: Boolean,
    val estimatedBandwidth: Int, // -1 if unknown
    val connectionType: String,
)

/**
 * Hardware acceleration information
 */
data class HardwareAccelerationInfo(
    val supportedCodecs: List<String>,
    val hasHardwareDecoding: Boolean,
    val softwareOnlyCodecs: List<String> = emptyList(),
)
