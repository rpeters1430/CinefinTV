package com.rpeters.cinefintv.utils

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Extension functions to extract media quality information (4K, HDR, etc.) from Jellyfin items.
 */

fun BaseItemDto.getMediaQualityLabel(): String? {
    val source = mediaSources?.firstOrNull() ?: return null
    val videoStream = source.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO } ?: return null

    val labels = mutableListOf<String>()

    // 1. Resolution
    val width = videoStream.width ?: 0
    val height = videoStream.height ?: 0
    val resolution = when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "1440p"
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width > 0 -> "${height}p"
        else -> null
    }
    resolution?.let { labels.add(it) }

    // 2. Dynamic Range / HDR
    val videoRange = videoStream.videoRange.toString().lowercase()
    val videoRangeType = videoStream.videoRangeType.toString().lowercase()

    val hdrLabel = when {
        videoRange.contains("dolby vision") || videoRangeType.contains("dv") -> "Dolby Vision"
        videoRange.contains("hdr10+") -> "HDR10+"
        videoRange.contains("hdr10") -> "HDR10"
        videoRange.contains("hdr") || videoRangeType.contains("hdr") -> "HDR"
        else -> null
    }
    hdrLabel?.let { labels.add(it) }

    // 3. Codec (Optional, but useful if space allows)
    val codec = videoStream.codec?.uppercase()
    val codecLabel = when (codec) {
        "HEVC", "H265" -> "HEVC"
        "AVC", "H264" -> "AVC"
        "VP9" -> "VP9"
        "AV1" -> "AV1"
        else -> null
    }
    // Only add codec if we have HDR/4K to keep it "premium" feeling, or if requested
    // codecLabel?.let { labels.add(it) }

    return if (labels.isNotEmpty()) labels.joinToString("  •  ") else null
}
