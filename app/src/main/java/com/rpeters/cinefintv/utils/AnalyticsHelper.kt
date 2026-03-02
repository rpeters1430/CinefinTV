package com.rpeters.cinefintv.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op analytics helper for CinefinTV (TV app has no Firebase Analytics dependency).
 * Replace with a real implementation if analytics are added later.
 */
@Singleton
class AnalyticsHelper @Inject constructor() {

    fun logAiEvent(feature: String, success: Boolean, model: String) {
        // No-op: no analytics in TV app
    }

    fun logPlaybackEvent(method: String, container: String, resolution: String) {
        // No-op: no analytics in TV app
    }

    fun logUiEvent(screen: String, action: String) {
        // No-op: no analytics in TV app
    }

    fun logCastEvent(deviceType: String) {
        // No-op: no analytics in TV app
    }
}
