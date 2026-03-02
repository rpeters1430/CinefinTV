package com.rpeters.cinefintv.utils

import java.net.URI
import java.net.URISyntaxException

/**
 * Normalize a Jellyfin server URL by trimming whitespace, removing trailing slashes
 * and forcing lowercase scheme and host parts.
 */
fun normalizeServerUrl(input: String): String {
    var url = input.trim()
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    return try {
        val uri = URI(url)
        val scheme = (uri.scheme ?: "https").lowercase()
        val host = uri.host?.lowercase() ?: return input
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.rawPath?.trimEnd('/') ?: ""
        buildString {
            append(scheme).append("://").append(host).append(port)
            if (path.isNotEmpty()) append(path)
        }
    } catch (_: URISyntaxException) {
        url
    }
}

/**
 * Legacy URL normalization that preserves the port number.
 * Used for migrating credentials saved with the previous logic.
 */
fun normalizeServerUrlLegacy(input: String): String {
    var url = input.trim()
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    return try {
        val uri = URI(url)
        val scheme = (uri.scheme ?: "https").lowercase()
        val host = uri.host?.lowercase() ?: return url
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.rawPath?.trimEnd('/') ?: ""
        buildString {
            append(scheme).append("://").append(host).append(port)
            if (path.isNotEmpty()) append(path)
        }
    } catch (_: URISyntaxException) {
        url
    }
}
