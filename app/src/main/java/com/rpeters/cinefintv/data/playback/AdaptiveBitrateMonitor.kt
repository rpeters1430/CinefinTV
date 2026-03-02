package com.rpeters.cinefintv.data.playback

import androidx.media3.exoplayer.ExoPlayer
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.network.ConnectivityChecker
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors playback quality and network conditions to suggest bitrate adjustments.
 * Detects buffering events and bandwidth degradation to recommend quality changes.
 */
@Singleton
class AdaptiveBitrateMonitor @Inject constructor(
    private val connectivityChecker: ConnectivityChecker,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
) {
    private val _qualityRecommendation = MutableStateFlow<QualityRecommendation?>(null)
    val qualityRecommendation: StateFlow<QualityRecommendation?> = _qualityRecommendation.asStateFlow()

    private var monitoringJob: Job? = null
    private var bufferingStartTime: Long? = null
    private var consecutiveBufferingEvents = 0
    private var lastQualityDowngrade: Long = 0

    companion object {
        private const val TAG = "AdaptiveBitrateMonitor"

        // Buffering thresholds
        private const val SUSTAINED_BUFFERING_THRESHOLD_MS = 5_000L // 5 seconds
        private const val BUFFERING_EVENT_WINDOW_MS = 30_000L // 30 seconds
        private const val MIN_TIME_BETWEEN_DOWNGRADES_MS = 60_000L // 1 minute

        // Monitoring interval
        private const val MONITORING_INTERVAL_MS = 1_000L // Check every second

        // Bandwidth thresholds (in bits per second)
        private const val LOW_BANDWIDTH_THRESHOLD = 5_000_000 // 5 Mbps
        private const val CRITICAL_BANDWIDTH_THRESHOLD = 2_000_000 // 2 Mbps
    }

    /**
     * Starts monitoring the ExoPlayer for buffering and bandwidth issues.
     */
    fun startMonitoring(
        exoPlayer: ExoPlayer,
        scope: CoroutineScope,
        currentQuality: TranscodingQuality,
        isTranscoding: Boolean,
    ) {
        stopMonitoring()

        monitoringJob = scope.launch {
            SecureLogger.d(TAG, "Started adaptive bitrate monitoring (quality=$currentQuality, transcoding=$isTranscoding)")

            var previousPlaybackState = exoPlayer.playbackState
            var bufferingDuration = 0L

            while (isActive) {
                try {
                    val currentState = exoPlayer.playbackState
                    val currentTime = System.currentTimeMillis()

                    // Track buffering events
                    if (currentState == androidx.media3.common.Player.STATE_BUFFERING &&
                        previousPlaybackState == androidx.media3.common.Player.STATE_READY
                    ) {
                        // Started buffering
                        bufferingStartTime = currentTime
                        consecutiveBufferingEvents++
                        SecureLogger.v(TAG, "Buffering started (event #$consecutiveBufferingEvents)")
                    } else if (currentState == androidx.media3.common.Player.STATE_READY &&
                        bufferingStartTime != null
                    ) {
                        // Recovered from buffering
                        bufferingDuration = currentTime - (bufferingStartTime ?: currentTime)
                        SecureLogger.v(TAG, "Buffering ended after ${bufferingDuration}ms")
                        bufferingStartTime = null
                    }

                    // Check for sustained buffering
                    val currentBufferingDuration = if (bufferingStartTime != null) {
                        currentTime - (bufferingStartTime ?: currentTime)
                    } else {
                        0L
                    }

                    // Only recommend downgrade if:
                    // 1. User is on AUTO mode (respects manual quality selection)
                    // 2. Currently transcoding (can't downgrade direct play)
                    // 3. Enough time has passed since last downgrade
                    // 4. Not already at lowest quality
                    val prefs = playbackPreferencesRepository.preferences.first()
                    val canDowngrade = prefs.transcodingQuality == TranscodingQuality.AUTO &&
                        isTranscoding &&
                        (currentTime - lastQualityDowngrade) > MIN_TIME_BETWEEN_DOWNGRADES_MS &&
                        currentQuality != TranscodingQuality.LOW

                    if (canDowngrade) {
                        // Check for sustained buffering
                        if (currentBufferingDuration >= SUSTAINED_BUFFERING_THRESHOLD_MS) {
                            val newQuality = getDowngradedQuality(currentQuality)
                            SecureLogger.w(
                                TAG,
                                "Sustained buffering detected (${currentBufferingDuration}ms). Recommending quality downgrade: $currentQuality -> $newQuality",
                            )
                            _qualityRecommendation.value = QualityRecommendation(
                                recommendedQuality = newQuality,
                                reason = "Buffering for ${currentBufferingDuration / 1000}s",
                                severity = RecommendationSeverity.HIGH,
                            )
                            lastQualityDowngrade = currentTime
                        }
                        // Check for multiple buffering events in a short window
                        else if (consecutiveBufferingEvents >= 3) {
                            val newQuality = getDowngradedQuality(currentQuality)
                            SecureLogger.w(
                                TAG,
                                "Multiple buffering events detected ($consecutiveBufferingEvents). Recommending quality downgrade: $currentQuality -> $newQuality",
                            )
                            _qualityRecommendation.value = QualityRecommendation(
                                recommendedQuality = newQuality,
                                reason = "Frequent buffering ($consecutiveBufferingEvents events)",
                                severity = RecommendationSeverity.MEDIUM,
                            )
                            lastQualityDowngrade = currentTime
                            consecutiveBufferingEvents = 0 // Reset counter
                        }

                        // Note: Removed incorrect bandwidth check that was using track bitrate
                        // instead of actual network bandwidth. Buffering-based detection above
                        // is more reliable for quality recommendations.
                    }

                    previousPlaybackState = currentState
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    SecureLogger.e(TAG, "Error in adaptive bitrate monitoring", e)
                    delay(MONITORING_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Stops monitoring.
     */
    fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        bufferingStartTime = null
        consecutiveBufferingEvents = 0
        _qualityRecommendation.value = null
        SecureLogger.d(TAG, "Stopped adaptive bitrate monitoring")
    }

    /**
     * Clears the current quality recommendation.
     */
    fun clearRecommendation() {
        _qualityRecommendation.value = null
    }

    /**
     * Resets buffering event tracking (e.g., after quality change).
     */
    fun resetBufferingTracking() {
        bufferingStartTime = null
        consecutiveBufferingEvents = 0
    }

    /**
     * Gets the next lower quality level.
     */
    private fun getDowngradedQuality(current: TranscodingQuality): TranscodingQuality {
        return when (current) {
            TranscodingQuality.MAXIMUM -> TranscodingQuality.HIGH
            TranscodingQuality.HIGH -> TranscodingQuality.MEDIUM
            TranscodingQuality.MEDIUM -> TranscodingQuality.LOW
            TranscodingQuality.LOW -> TranscodingQuality.LOW // Already at minimum
            TranscodingQuality.AUTO -> TranscodingQuality.MEDIUM // Fallback
        }
    }
}

/**
 * Represents a quality recommendation from the adaptive bitrate monitor.
 */
data class QualityRecommendation(
    val recommendedQuality: TranscodingQuality,
    val reason: String,
    val severity: RecommendationSeverity,
)

enum class RecommendationSeverity {
    LOW, // Suggestion
    MEDIUM, // Recommended
    HIGH, // Strongly recommended (buffering/bandwidth issues)
}
