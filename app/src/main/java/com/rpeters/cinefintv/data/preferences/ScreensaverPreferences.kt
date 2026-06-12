package com.rpeters.cinefintv.data.preferences

/**
 * Data class representing user preferences for the ambient screensaver.
 */
data class ScreensaverPreferences(
    /**
     * Whether the ambient screensaver is enabled.
     */
    val isEnabled: Boolean = true,

    /**
     * Idle duration in minutes before the screensaver starts.
     */
    val idleTimeoutMinutes: Int = 5,
) {
    companion object {
        /**
         * Default screensaver preferences.
         */
        val DEFAULT = ScreensaverPreferences()
    }
}
