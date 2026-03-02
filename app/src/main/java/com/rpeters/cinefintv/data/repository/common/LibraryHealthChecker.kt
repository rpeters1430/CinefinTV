package com.rpeters.cinefintv.data.repository.common

import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.R
import com.rpeters.cinefintv.utils.AppResources
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors library health and maintains a blocklist of problematic libraries
 * to prevent repeated HTTP 400 errors and improve user experience.
 */
@Singleton
class LibraryHealthChecker @Inject constructor() {
    companion object {
        private const val TAG = "LibraryHealthChecker"
        private const val MAX_FAILURES_BEFORE_BLOCK = 5
        private const val BLOCK_DURATION_MS = 60 * 1000L // 1 minute
    }

    // Track library failure counts
    private val failureCounts = mutableMapOf<String, Int>()
    private val blockedLibraries = mutableMapOf<String, Long>() // libraryId -> block expiry time
    private val libraryIssues = mutableMapOf<String, String>() // libraryId -> issue description

    private val _libraryHealth = MutableStateFlow<Map<String, LibraryHealthStatus>>(emptyMap())
    val libraryHealth: StateFlow<Map<String, LibraryHealthStatus>> = _libraryHealth.asStateFlow()

    /**
     * Reports a successful library operation.
     */
    fun reportSuccess(libraryId: String) {
        synchronized(this) {
            failureCounts.remove(libraryId)
            blockedLibraries.remove(libraryId)
            libraryIssues.remove(libraryId)

            updateHealthStatus(libraryId, LibraryHealthStatus.HEALTHY)

            if (BuildConfig.DEBUG) {
                SecureLogger.v(TAG, "Library $libraryId marked as healthy")
            }
        }
    }

    /**
     * Reports a failed library operation.
     */
    fun reportFailure(libraryId: String, error: String) {
        synchronized(this) {
            val currentCount = failureCounts.getOrDefault(libraryId, 0)
            val newCount = currentCount + 1
            failureCounts[libraryId] = newCount
            libraryIssues[libraryId] = error

            if (BuildConfig.DEBUG) {
                SecureLogger.w(TAG, "Library $libraryId failure count: $newCount ($error)")
            }

            when {
                newCount >= MAX_FAILURES_BEFORE_BLOCK -> {
                    // Block this library temporarily
                    val blockUntil = System.currentTimeMillis() + BLOCK_DURATION_MS
                    blockedLibraries[libraryId] = blockUntil

                    updateHealthStatus(libraryId, LibraryHealthStatus.BLOCKED)

                    if (BuildConfig.DEBUG) {
                        SecureLogger.w(TAG, "Library $libraryId blocked for ${BLOCK_DURATION_MS / 1000} seconds due to repeated failures")
                    }
                }
                newCount >= 2 -> {
                    updateHealthStatus(libraryId, LibraryHealthStatus.UNHEALTHY)
                }
                else -> {
                    updateHealthStatus(libraryId, LibraryHealthStatus.WARNING)
                }
            }
        }
    }

    /**
     * Checks if a library is currently blocked due to repeated failures.
     */
    fun isLibraryBlocked(libraryId: String): Boolean {
        synchronized(this) {
            val blockUntil = blockedLibraries[libraryId] ?: return false
            val currentTime = System.currentTimeMillis()

            if (currentTime >= blockUntil) {
                // Block has expired, remove it
                blockedLibraries.remove(libraryId)
                failureCounts.remove(libraryId)
                libraryIssues.remove(libraryId)
                updateHealthStatus(libraryId, LibraryHealthStatus.HEALTHY)

                if (BuildConfig.DEBUG) {
                    SecureLogger.v(TAG, "Library $libraryId block expired, marked as healthy")
                }
                return false
            }

            return true
        }
    }

    /**
     * Gets the health status for a library.
     */
    fun getLibraryHealth(libraryId: String): LibraryHealthStatus {
        return _libraryHealth.value[libraryId] ?: LibraryHealthStatus.UNKNOWN
    }

    /**
     * Gets a user-friendly description of library issues.
     */
    fun getLibraryIssueDescription(libraryId: String): String? {
        return libraryIssues[libraryId]
    }

    /**
     * Gets all libraries with health issues.
     */
    fun getProblematicLibraries(): Map<String, LibraryHealthStatus> {
        return _libraryHealth.value.filterValues {
            it != LibraryHealthStatus.HEALTHY && it != LibraryHealthStatus.UNKNOWN
        }
    }

    /**
     * Clears the health status for a library (useful for manual retry).
     */
    fun clearLibraryHealth(libraryId: String) {
        synchronized(this) {
            failureCounts.remove(libraryId)
            blockedLibraries.remove(libraryId)
            libraryIssues.remove(libraryId)
            updateHealthStatus(libraryId, LibraryHealthStatus.HEALTHY)

            if (BuildConfig.DEBUG) {
                SecureLogger.v(TAG, "Manually cleared health status for library $libraryId")
            }
        }
    }

    /**
     * Provides recommendations for handling problematic libraries.
     */
    fun getLibraryRecommendations(libraryId: String): List<String> {
        val health = getLibraryHealth(libraryId)
        val issue = getLibraryIssueDescription(libraryId)

        return when (health) {
            LibraryHealthStatus.BLOCKED -> listOf(
                "Library temporarily disabled due to repeated errors",
                "Will automatically retry in a few minutes",
                "Check server logs for detailed error information",
            )
            LibraryHealthStatus.UNHEALTHY -> listOf(
                "Library experiencing intermittent issues",
                "Some content may not load properly",
                "Issue: ${issue ?: AppResources.getString(R.string.unknown_error)}",
            )
            LibraryHealthStatus.WARNING -> listOf(
                "Library had recent loading issues",
                "Monitoring for additional problems",
                "Most recent issue: ${issue ?: AppResources.getString(R.string.unknown_error)}",
            )
            else -> emptyList()
        }
    }

    private fun updateHealthStatus(libraryId: String, status: LibraryHealthStatus) {
        val currentHealth = _libraryHealth.value.toMutableMap()
        currentHealth[libraryId] = status
        _libraryHealth.value = currentHealth
    }

    /**
     * Cleanup method to remove old entries.
     */
    fun cleanup() {
        synchronized(this) {
            val currentTime = System.currentTimeMillis()
            val expiredBlocks = blockedLibraries.filterValues { it <= currentTime }.keys

            expiredBlocks.forEach { libraryId ->
                blockedLibraries.remove(libraryId)
                failureCounts.remove(libraryId)
                libraryIssues.remove(libraryId)
                updateHealthStatus(libraryId, LibraryHealthStatus.HEALTHY)
            }

            if (BuildConfig.DEBUG && expiredBlocks.isNotEmpty()) {
                SecureLogger.v(TAG, "Cleaned up ${expiredBlocks.size} expired library blocks")
            }
        }
    }
}

/**
 * Represents the health status of a library.
 */
enum class LibraryHealthStatus {
    UNKNOWN, // No data yet
    HEALTHY, // Working normally
    WARNING, // Had 1 recent failure
    UNHEALTHY, // Had 2 recent failures
    BLOCKED, // Temporarily disabled due to repeated failures
}
