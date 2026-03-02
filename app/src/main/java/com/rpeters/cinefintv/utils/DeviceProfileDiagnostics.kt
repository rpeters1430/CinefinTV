package com.rpeters.cinefintv.utils

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import com.rpeters.cinefintv.data.DeviceCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Diagnostic utility to validate device profile and identify codec/playback issues.
 * Use this to debug device capability detection problems.
 */
object DeviceProfileDiagnostics {

    suspend fun generateDiagnosticReport(
        context: Context,
        deviceCapabilities: DeviceCapabilities,
    ): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("=== DEVICE PROFILE DIAGNOSTIC REPORT ===")
            appendLine()

            // Device Info
            appendLine("Device Information:")
            appendLine("  Manufacturer: ${Build.MANUFACTURER}")
            appendLine("  Model: ${Build.MODEL}")
            appendLine("  Device: ${Build.DEVICE}")
            appendLine("  Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()

            // Display Info
            val displayMetrics = context.resources.displayMetrics
            appendLine("Display:")
            appendLine("  Resolution: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
            appendLine("  Density: ${displayMetrics.density} (${displayMetrics.densityDpi} dpi)")
            appendLine()

            // Device Capabilities
            val caps = deviceCapabilities.getDirectPlayCapabilities()
            appendLine("Device Capabilities:")
            appendLine("  Max Resolution: ${caps.maxResolution.first}x${caps.maxResolution.second}")
            appendLine("  Supports 4K: ${caps.supports4K}")
            appendLine("  Supports HDR: ${caps.supportsHdr}")
            appendLine("  Max Bitrate: ${caps.maxBitrate / 1_000_000} Mbps")
            appendLine()

            // Video Codecs
            appendLine("Supported Video Codecs:")
            caps.supportedVideoCodecs.sorted().forEach { codec ->
                appendLine("  ✓ $codec")
            }
            appendLine()

            // Audio Codecs
            appendLine("Supported Audio Codecs:")
            caps.supportedAudioCodecs.sorted().forEach { codec ->
                appendLine("  ✓ $codec")
            }
            appendLine()

            // Hardware Acceleration
            appendLine("Hardware Acceleration:")
            appendLine("  Hardware Codecs: ${caps.hardwareAcceleration.supportedCodecs.joinToString(", ")}")
            appendLine("  Has Hardware Decoding: ${caps.hardwareAcceleration.hasHardwareDecoding}")
            if (caps.hardwareAcceleration.softwareOnlyCodecs.isNotEmpty()) {
                appendLine("  Software-Only Codecs: ${caps.hardwareAcceleration.softwareOnlyCodecs.joinToString(", ")}")
            }
            appendLine()

            // Enhanced Codec Support
            val codecSupport = deviceCapabilities.getEnhancedCodecSupport()
            appendLine("Enhanced Video Codec Analysis:")
            codecSupport.videoCodecs.forEach { (codec, detail) ->
                val support = when (detail.support) {
                    com.rpeters.cinefintv.data.CodecSupport.HARDWARE_ACCELERATED -> "Hardware"
                    com.rpeters.cinefintv.data.CodecSupport.SOFTWARE_ONLY -> "Software"
                    com.rpeters.cinefintv.data.CodecSupport.NOT_SUPPORTED -> "Not Supported"
                }
                appendLine("  $codec: $support (max ${detail.maxResolution.first}x${detail.maxResolution.second}, rating: ${detail.performanceRating}/10)")
            }
            appendLine()

            // HEVC/H.265 10-bit Check (common issue on Pixels)
            appendLine("HEVC/H.265 10-bit Support Check:")
            val hevc10BitSupported = checkHevc10BitSupport()
            appendLine("  10-bit HEVC: ${if (hevc10BitSupported) "✓ Supported" else "✗ Not Supported"}")
            if (!hevc10BitSupported && Build.MANUFACTURER.equals("Google", ignoreCase = true)) {
                appendLine("  ⚠️ WARNING: Google Pixel devices often report false negatives for 10-bit HEVC")
                appendLine("  ⚠️ The server may incorrectly reject Direct Play for 10-bit content")
                appendLine("  ⚠️ App uses workaround to bypass server decision when safe")
            }
            appendLine()

            // AV1 Support (important for newer content)
            appendLine("AV1 Codec Support:")
            val av1Supported = caps.supportedVideoCodecs.contains("av1")
            appendLine("  AV1: ${if (av1Supported) "✓ Supported" else "✗ Not Supported"}")
            appendLine()

            // Performance Profile
            appendLine("Performance Profile: ${codecSupport.performanceProfile}")
            appendLine()

            // Known Issues Section
            appendLine("=== KNOWN ISSUES & WORKAROUNDS ===")
            appendKnownIssues(Build.MANUFACTURER, Build.MODEL)

            appendLine()
            appendLine("=== END OF REPORT ===")
        }
    }

    /**
     * Check if device supports 10-bit HEVC (Main10 profile).
     * Many Pixels falsely report no support, but actually can decode it.
     */
    private fun checkHevc10BitSupport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false

        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue

                if (codecInfo.supportedTypes.contains("video/hevc")) {
                    val capabilities = codecInfo.getCapabilitiesForType("video/hevc")
                    val profileLevels = capabilities.profileLevels

                    // Check for Main10 profile (10-bit)
                    for (profileLevel in profileLevels) {
                        if (profileLevel.profile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) {
                            SecureLogger.d("DeviceProfileDiagnostics", "Found 10-bit HEVC support: ${codecInfo.name}")
                            return true
                        }
                    }
                }
            }

            false
        } catch (e: Exception) {
            SecureLogger.w("DeviceProfileDiagnostics", "Error checking 10-bit HEVC support", e)
            false
        }
    }

    /**
     * Document known issues for specific devices.
     */
    private fun StringBuilder.appendKnownIssues(manufacturer: String, model: String) {
        when {
            manufacturer.equals("Google", ignoreCase = true) && model.contains("Pixel", ignoreCase = true) -> {
                appendLine("Google Pixel Devices:")
                appendLine("  • 10-bit HEVC often works despite MediaCodecList reporting no support")
                appendLine("  • App bypasses server rejection of 10-bit HEVC when safe")
                appendLine("  • HDR10/HLG support varies by model (Pixel 4+ generally supported)")
                appendLine("  • AV1 hardware decode supported on Pixel 6+")
                appendLine()
                appendLine("Recommended Settings:")
                appendLine("  • Enable Direct Play for HEVC content")
                appendLine("  • Set transcoding quality to HIGH or MAXIMUM on WiFi")
                appendLine("  • HDR content should direct play automatically")
            }
            manufacturer.equals("Samsung", ignoreCase = true) -> {
                appendLine("Samsung Devices:")
                appendLine("  • Generally excellent codec support")
                appendLine("  • Hardware HEVC decoding well-supported")
                appendLine("  • HDR support on flagship models (S series, Note series)")
            }
            else -> {
                appendLine("No known device-specific issues.")
                appendLine("If experiencing playback problems:")
                appendLine("  1. Check if content is transcoding when it shouldn't")
                appendLine("  2. Verify network quality is sufficient for bitrate")
                appendLine("  3. Check Jellyfin server logs for codec rejection reasons")
            }
        }
    }
}
