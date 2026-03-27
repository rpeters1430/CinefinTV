package com.rpeters.cinefintv.core

/**
 * Centralized feature flag keys for remote config.
 * All feature flags should be defined here for easy reference and type safety.
 */
object FeatureFlags {
    /**
     * Experimental & Utility Flags
     */
    object Experimental {
        /** Enable/disable video player gestures (tap/drag) */
        const val ENABLE_VIDEO_PLAYER_GESTURES = "enable_video_player_gestures"

        /** Custom seek interval for video player in milliseconds */
        const val VIDEO_PLAYER_SEEK_INTERVAL_MS = "video_player_seek_interval_ms"

        /** Toggle visibility of transcoding diagnostics tool */
        const val SHOW_TRANSCODING_DIAGNOSTICS = "show_transcoding_diagnostics"

        /** Experimental player buffer size in milliseconds */
        const val EXPERIMENTAL_PLAYER_BUFFER_MS = "experimental_player_buffer_ms"
    }
}
