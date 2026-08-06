package com.rpeters.cinefintv.utils

import java.net.URI

fun normalizeJellyfinBase(input: String): String {
    var url = input.trim().removeSuffix("/")
    if (!url.endsWith("/jellyfin", ignoreCase = true)) {
        url += "/jellyfin"
    }
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        val scheme = ServerUrlValidator.getDefaultScheme(url)
        url = "$scheme://$url"
    }
    return URI(url.replace("//jellyfin", "/jellyfin")).toString()
}
