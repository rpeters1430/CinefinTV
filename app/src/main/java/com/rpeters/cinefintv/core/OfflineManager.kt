package com.rpeters.cinefintv.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder for OfflineManager to satisfy legacy JellyfinRepository dependencies.
 * Real offline support can be implemented here if needed.
 */
@Singleton
class OfflineManager @Inject constructor() {
    fun isCurrentlyOnline(): Boolean = true
    fun getOfflineErrorMessage(operation: String): String = "Device is offline. $operation is not available."
}
