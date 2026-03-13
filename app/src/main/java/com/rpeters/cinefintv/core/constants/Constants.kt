package com.rpeters.cinefintv.core.constants

/**
 * Centralized application constants to prevent drift between multiple files.
 * Replaces magic numbers and strings throughout the codebase.
 */
object Constants {

    // Network timeouts
    const val NETWORK_TIMEOUT_SECONDS = 30L
    const val NETWORK_READ_TIMEOUT_SECONDS = 60L
    const val NETWORK_WRITE_TIMEOUT_SECONDS = 30L

    // Authentication
    const val TOKEN_VALIDITY_DURATION_MS = 45 * 60 * 1000L // 45 minutes (15 min before typical 60min server expiry)
    const val TOKEN_REFRESH_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes before expiry

    // Pagination
    const val DEFAULT_PAGE_SIZE = 20
    const val LARGE_PAGE_SIZE = 50
    const val SEARCH_PAGE_SIZE = 30
    const val PREFETCH_BUFFER_SIZE = 3

    // Image loading
    const val IMAGE_CACHE_SIZE_MB = 50
    const val MAX_IMAGE_WIDTH = 800
    const val MAX_IMAGE_HEIGHT = 1200
    const val IMAGE_QUALITY_HIGH = 85
    const val IMAGE_QUALITY_MEDIUM = 75
    const val IMAGE_QUALITY_LOW = 60

    // Animation durations
    const val ANIMATION_DURATION_MS = 300L
    const val FAST_ANIMATION_MS = 150L
    const val SLOW_ANIMATION_MS = 500L

    // Debounce delays
    const val SEARCH_DEBOUNCE_MS = 300L
    const val INPUT_DEBOUNCE_MS = 500L

    // HTTP status codes
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404
    const val HTTP_SERVER_ERROR = 500

    // Retry settings
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_INITIAL_DELAY_MS = 1000L
    const val RE_AUTH_DELAY_MS = 2000L
    const val QUICK_CONNECT_POLL_INTERVAL_MS = 2000L

    // API limits
    const val RECENTLY_ADDED_LIMIT = 20
    const val RECENTLY_ADDED_BY_TYPE_LIMIT = 15
    const val CONTINUE_WATCHING_LIMIT = 10

    // Player settings
    const val PLAYER_CONTROLS_TIMEOUT_MS = 3000L
    const val PLAYER_SEEK_INCREMENT_MS = 30_000L
    const val PLAYER_REWIND_INCREMENT_MS = 30_000L

    // Cache settings
    const val DISK_CACHE_SIZE_MB = 100
    const val MEMORY_CACHE_PERCENT = 0.25f // 25% of available memory

    // Content Rating Thresholds
    object Rating {
        const val HIGH_RATING_THRESHOLD = 7.0
        const val EXCELLENT_RATING_THRESHOLD = 8.5
        const val GOOD_RATING_THRESHOLD = 6.0
        const val AVERAGE_RATING_THRESHOLD = 5.0
    }

    // Security Constants
    object Security {
        const val KEY_ALIAS = "JellyfinCredentialKey"
        const val ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }

    // Playback Constants
    object Playback {
        const val RESUME_THRESHOLD_PERCENT = 5.0 // Resume if < 5% watched
        const val WATCHED_THRESHOLD_PERCENT = 90.0 // Mark watched if > 90% watched
    }

    // App Information
    object AppInfo {
        const val PRIVACY_POLICY_URL = "https://rpeters1430.github.io/Cinefin/privacy-policy.html"
        const val GITHUB_REPO_URL = "https://github.com/rpeters1430/Cinefin"
    }

    // Error Messages
    object ErrorMessages {
        const val DNS_RESOLUTION_ERROR = "Could not resolve server hostname. Please check the server address for typos, or try using an IP address (e.g., 192.168.1.100)"
    }
}
