package com.rpeters.cinefintv.utils

import android.util.Log
import android.util.Patterns
import kotlinx.coroutines.CancellationException
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * Utility class for validating and normalizing Jellyfin server URLs.
 * Handles common URL formatting issues that cause connection problems.
 */
object ServerUrlValidator {
    private const val TAG = "ServerUrlValidator"

    // Common Jellyfin ports
    private const val DEFAULT_HTTP_PORT = 8096
    private const val DEFAULT_HTTPS_PORT = 8920

    /**
     * Validates and normalizes a server URL for Jellyfin connection.
     *
     * @param inputUrl The user-provided server URL
     * @return A normalized URL string or null if invalid
     */
    fun validateAndNormalizeUrl(inputUrl: String): String? {
        if (inputUrl.isBlank()) {
            return null
        }

        var url = inputUrl.trim()

        // Remove trailing slashes
        url = url.trimEnd('/')

        // Add protocol if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // Default to HTTPS for modern Jellyfin setups
            url = "https://$url"
        }

        // Add default port if missing
        url = addDefaultPortIfMissing(url)

        // Validate the final URL
        return if (isValidUrl(url)) {
            url
        } else {
            Log.w(TAG, "Invalid URL after normalization: $url")
            null
        }
    }

    /**
     * Gets fallback URL for connection failures.
     * If HTTPS fails, try HTTP. If HTTP fails, try different ports.
     *
     * @param originalUrl The original URL that failed
     * @return Alternative URL to try, or null if no fallback available
     */
    fun getFallbackUrl(originalUrl: String): String? {
        return try {
            val uri = URI(originalUrl)
            val host = uri.host ?: return null

            when {
                // HTTPS on 8920 failed -> try HTTP on 8096
                uri.scheme == "https" && uri.port == DEFAULT_HTTPS_PORT -> {
                    "http://$host:$DEFAULT_HTTP_PORT"
                }
                // HTTPS on default port failed -> try HTTP on 8096
                uri.scheme == "https" && uri.port == 443 -> {
                    "http://$host:$DEFAULT_HTTP_PORT"
                }
                // HTTP on 8096 failed -> try HTTP on 80 (if not already tried)
                uri.scheme == "http" && uri.port == DEFAULT_HTTP_PORT -> {
                    "http://$host:80"
                }
                // HTTP on 80 failed -> try HTTPS on 443 (if not already tried)
                uri.scheme == "http" && uri.port == 80 -> {
                    "https://$host:443"
                }
                else -> null // No more fallbacks available
            }
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Failed to parse URI for fallback: $originalUrl", e)
            null
        }
    }

    /**
     * Gets all possible URL variations to try for a server.
     * Returns list in order of preference (most to least likely to work).
     *
     * @param inputUrl The user-provided server URL
     * @return List of URLs to try in order
     */
    fun getUrlVariations(inputUrl: String): List<String> {
        if (inputUrl.isBlank()) return emptyList()

        var baseUrl = inputUrl.trim().trimEnd('/')

        // Extract components from input
        val (host, originalPort, path) = try {
            when {
                baseUrl.startsWith("http://") || baseUrl.startsWith("https://") -> {
                    val uri = URI(baseUrl)
                    Triple(uri.host, uri.port, uri.path ?: "")
                }
                else -> {
                    // No protocol specified, parse manually
                    val parts = baseUrl.split("/", limit = 2)
                    val hostPort = parts[0]
                    val path = if (parts.size > 1) "/${parts[1]}" else ""

                    val host = if (hostPort.contains(":")) {
                        hostPort.substringBefore(":")
                    } else {
                        hostPort
                    }
                    val port = if (hostPort.contains(":")) {
                        hostPort.substringAfter(":").toIntOrNull() ?: -1
                    } else {
                        -1
                    }
                    Triple(host, port, path)
                }
            }
        } catch (e: CancellationException) {
            throw e
        }

        if (host.isNullOrBlank() || !isValidHost(host)) {
            return emptyList()
        }

        // Generate variations in order of preference for reverse proxy scenarios
        val variations = mutableListOf<String>()

        // Determine if this looks like a reverse proxy setup
        val isReverseProxyLikely = isReverseProxySetup(baseUrl, originalPort, path)

        if (isReverseProxyLikely) {
            // Special handling for reverse proxy setups with /jellyfin path
            when {
                baseUrl.startsWith("https://") -> {
                    // For HTTPS reverse proxy, prioritize the exact path first
                    variations.add("$baseUrl/jellyfin")
                    variations.add("$baseUrl")
                    variations.add("https://$host:443/jellyfin")
                    variations.add("https://$host/jellyfin")
                    // Try without the path if it was already included
                    if (path.isNotEmpty() && path != "/jellyfin") {
                        variations.add("https://$host$path")
                    }
                    // Try standard ports
                    variations.add("https://$host:443$path")
                    // Try HTTP fallback on standard ports
                    variations.add("http://$host/jellyfin")
                }
                baseUrl.startsWith("http://") -> {
                    // For HTTP reverse proxy, prioritize the exact path first
                    variations.add("$baseUrl/jellyfin")
                    variations.add("$baseUrl")
                    variations.add("http://$host:80/jellyfin")
                    variations.add("http://$host/jellyfin")
                    // Try without the path if it was already included
                    if (path.isNotEmpty() && path != "/jellyfin") {
                        variations.add("http://$host$path")
                    }
                    // Try standard ports
                    variations.add("http://$host:80$path")
                    // Try HTTPS upgrade
                    variations.add("https://$host/jellyfin")
                }
                else -> {
                    // No protocol specified, but looks like reverse proxy
                    variations.add("https://$host${jellyfinPathIfMissing(path)}")
                    variations.add("http://$host${jellyfinPathIfMissing(path)}")
                    variations.add("https://$host:443${jellyfinPathIfMissing(path)}")
                    variations.add("http://$host:80${jellyfinPathIfMissing(path)}")
                }
            }
        } else {
            // Direct connection: use standard Jellyfin port priorities
            when {
                baseUrl.startsWith("https://") -> {
                    variations.add("https://$host:$DEFAULT_HTTPS_PORT")
                    variations.add("https://$host:443")
                    variations.add("http://$host:$DEFAULT_HTTP_PORT")
                    variations.add("http://$host:80")
                }
                baseUrl.startsWith("http://") -> {
                    variations.add("http://$host:$DEFAULT_HTTP_PORT")
                    variations.add("http://$host:80")
                    variations.add("https://$host:$DEFAULT_HTTPS_PORT")
                    variations.add("https://$host:443")
                }
                else -> {
                    variations.add("http://$host:$DEFAULT_HTTP_PORT")
                    variations.add("https://$host:$DEFAULT_HTTPS_PORT")
                    variations.add("http://$host:80")
                    variations.add("https://$host:443")
                }
            }
        }

        // Remove duplicates while preserving order
        return variations.distinct().filter { isValidUrl(it) }
    }

    /**
     * Helper function to add /jellyfin path if not already present
     */
    private fun jellyfinPathIfMissing(path: String): String {
        return if (path.isEmpty() || path == "/") {
            "/jellyfin"
        } else {
            path
        }
    }

    /**
     * Determines if the URL looks like a reverse proxy setup.
     * Indicators: domain names with subdomains, paths, common web ports
     */
    private fun isReverseProxySetup(url: String, port: Int, path: String): Boolean {
        return when {
            // Has a path component beyond root (like /jellyfin) - strongest indicator
            path.isNotEmpty() && path != "/" -> true
            // Domain has subdomain (more than 1 dot - e.g., server.domain.com)
            url.substringAfter("://").substringBefore("/").substringBefore(":").count { it == '.' } >= 2 -> true
            // Uses standard web ports (likely reverse proxy)
            port == 80 || port == 443 -> true
            // Contains common reverse proxy indicators in hostname
            url.contains("proxy") || url.contains("cdn") || url.contains("gateway") -> true
            // Common subdomain patterns for reverse proxy
            url.contains("jellyfin.") || url.contains("media.") || url.contains("stream.") -> true
            else -> false
        }
    }

    /**
     * Adds default Jellyfin port if no port is specified.
     * For reverse proxy setups (URLs with paths), don't add default ports.
     */
    private fun addDefaultPortIfMissing(url: String): String {
        return try {
            val uri = URI(url)

            // If port is already specified, keep it
            if (uri.port != -1) {
                return url
            }

            // Check if this looks like a reverse proxy setup
            val path = uri.path ?: ""
            val isReverseProxy = path.isNotEmpty() && path != "/"

            // For reverse proxy setups, don't add default Jellyfin ports
            if (isReverseProxy) {
                return url
            }

            // Add default port based on protocol for direct connections
            val defaultPort = when (uri.scheme?.lowercase()) {
                "https" -> DEFAULT_HTTPS_PORT
                "http" -> DEFAULT_HTTP_PORT
                else -> DEFAULT_HTTP_PORT
            }

            "${uri.scheme}://${uri.host}:$defaultPort${uri.path ?: ""}"
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Failed to parse URI for port addition: $url", e)
            url
        }
    }

    /**
     * Validates if a URL is properly formatted and potentially reachable.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = URL(url)

            // Check basic URL structure
            if (urlObj.host.isNullOrBlank()) {
                return false
            }

            // Check if host looks valid (basic pattern check)
            val host = urlObj.host
            if (!isValidHost(host)) {
                return false
            }

            // Check protocol
            if (urlObj.protocol !in listOf("http", "https")) {
                return false
            }

            // Check port range
            val port = if (urlObj.port == -1) {
                when (urlObj.protocol) {
                    "https" -> 443
                    "http" -> 80
                    else -> 80
                }
            } else {
                urlObj.port
            }

            if (port < 1 || port > 65535) {
                return false
            }

            true
        } catch (e: MalformedURLException) {
            Log.w(TAG, "Malformed URL: $url", e)
            false
        }
    }

    /**
     * Validates if a host string is valid (IP address, domain, or localhost).
     */
    private fun isValidHost(host: String): Boolean {
        // Check for localhost variants
        if (host.equals("localhost", ignoreCase = true) ||
            host == "127.0.0.1" ||
            host == "::1"
        ) {
            return true
        }

        // Check for valid IP address pattern
        if (isValidIPAddress(host)) {
            return true
        }

        // Check for valid domain pattern
        if (Patterns.DOMAIN_NAME.matcher(host).matches()) {
            return true
        }

        // Allow local network IPs (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
        if (isLocalNetworkIP(host)) {
            return true
        }

        return false
    }

    /**
     * Checks if an IP address is in private/local network ranges.
     */
    private fun isLocalNetworkIP(host: String): Boolean {
        val ipPattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".toRegex()
        val match = ipPattern.find(host) ?: return false

        val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: 256 }

        // Validate octet ranges
        if (parts.any { it < 0 || it > 255 }) {
            return false
        }

        val (a, b, c, d) = parts

        // Check private IP ranges
        return when {
            // 192.168.0.0/16
            a == 192 && b == 168 -> true
            // 10.0.0.0/8
            a == 10 -> true
            // 172.16.0.0/12
            a == 172 && b in 16..31 -> true
            else -> false
        }
    }

    /**
     * Extracts the base URL (protocol + host + port) from a full URL.
     */
    fun extractBaseUrl(fullUrl: String): String? {
        return try {
            val uri = URI(fullUrl)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            "${uri.scheme}://${uri.host}$port"
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Failed to extract base URL from: $fullUrl", e)
            null
        }
    }

    /**
     * Checks if two URLs point to the same server (ignoring paths).
     */
    fun isSameServer(url1: String, url2: String): Boolean {
        val base1 = extractBaseUrl(url1)
        val base2 = extractBaseUrl(url2)
        return base1 != null && base2 != null && base1.equals(base2, ignoreCase = true)
    }

    /**
     * Validates if a string is a valid IPv4 address.
     */
    private fun isValidIPAddress(host: String): Boolean {
        // Check IPv4
        if (isValidIPv4Address(host)) {
            return true
        }

        // Check IPv6 (with or without brackets)
        if (isValidIPv6Address(host)) {
            return true
        }

        return false
    }

    /**
     * Validates if a string is a valid IPv4 address.
     */
    private fun isValidIPv4Address(host: String): Boolean {
        val ipPattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".toRegex()
        val match = ipPattern.find(host) ?: return false

        val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: 256 }

        // Validate octet ranges (0-255)
        return parts.all { it in 0..255 }
    }

    /**
     * Validates if a string is a valid IPv6 address.
     * Supports both bracketed [::1] and unbracketed ::1 formats.
     * Note: This is a simplified validation covering common IPv6 cases.
     */
    private fun isValidIPv6Address(host: String): Boolean {
        val cleanHost = host.trim().removeSurrounding("[", "]")

        // Basic IPv6 validation (simplified - covers common cases)
        // Full IPv6 has 8 groups, but :: can compress consecutive zeros
        val ipv6Pattern = """^([0-9a-fA-F]{0,4}:){7}[0-9a-fA-F]{0,4}$""".toRegex() // Full 8 groups
        val compressedPattern = """^::([0-9a-fA-F]{0,4}:?)*[0-9a-fA-F]{0,4}$""".toRegex() // Starts with ::
        val compressedEndPattern = """^([0-9a-fA-F]{0,4}:)+::$""".toRegex() // Ends with ::
        val compressedMiddlePattern = """^([0-9a-fA-F]{0,4}:)+::([0-9a-fA-F]{0,4}:?)*[0-9a-fA-F]{0,4}$""".toRegex() // :: in middle

        return ipv6Pattern.matches(cleanHost) ||
            compressedPattern.matches(cleanHost) ||
            compressedEndPattern.matches(cleanHost) ||
            compressedMiddlePattern.matches(cleanHost) ||
            cleanHost == "::" || // Shorthand for all zeros
            cleanHost == "::1" // localhost
    }

    /**
     * Checks if a server address looks like an IP address (not a hostname).
     * Useful for providing guidance to users about using IP addresses vs hostnames.
     */
    fun looksLikeIPAddress(serverUrl: String): Boolean {
        return try {
            val uri = if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
                URI(serverUrl)
            } else {
                URI("http://$serverUrl")
            }

            val host = uri.host ?: return false
            isValidIPv4Address(host) || isValidIPv6Address(host)
        } catch (e: Exception) {
            false
        }
    }
}
