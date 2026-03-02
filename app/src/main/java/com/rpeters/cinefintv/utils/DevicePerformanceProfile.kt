package com.rpeters.cinefintv.utils

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService

/**
 * Describes the device's performance tier to tune caches and animations.
 */
data class DevicePerformanceProfile(
    val tier: Tier,
    val memoryCachePercent: Double,
    val diskCacheSizeMb: Long,
) {
    enum class Tier { HIGH, MID, LOW }

    companion object {
        private const val HIGH_MEMORY_CLASS_MB = 256
        private const val MID_MEMORY_CLASS_MB = 192
        private const val DEFAULT_MEMORY_CLASS_MB = 128
        private const val HIGH_MEMORY_CACHE_PERCENT = 0.22
        private const val MID_MEMORY_CACHE_PERCENT = 0.18
        private const val LOW_MEMORY_CACHE_PERCENT = 0.12
        private const val HIGH_DISK_CACHE_MB = 160L
        private const val MID_DISK_CACHE_MB = 120L
        private const val LOW_DISK_CACHE_MB = 80L

        @Volatile
        private var cachedProfile: DevicePerformanceProfile? = null

        /**
         * Detects the current device's performance tier based on memory characteristics.
         *
         * Tier thresholds:
         * - HIGH: Not a low-RAM device and `largeMemoryClass` >= 256 MB
         * - MID:  Not a low-RAM device and `largeMemoryClass` >= 192 MB
         * - LOW:  Any device that does not meet the above thresholds or reports low-RAM
         *
         * Cache sizing:
         * - Memory cache uses a percentage of app-available memory tuned per tier
         * - Disk cache scales between 80 MB and 160 MB, favoring higher tiers
         */
        fun detect(context: Context): DevicePerformanceProfile {
            cachedProfile?.let { return it }

            val activityManager = context.getSystemService<ActivityManager>()
            val isLowRamDevice = activityManager?.isLowRamDevice ?: false
            val memoryClassMb = activityManager?.largeMemoryClass ?: DEFAULT_MEMORY_CLASS_MB

            val tier = when {
                !isLowRamDevice && memoryClassMb >= HIGH_MEMORY_CLASS_MB -> Tier.HIGH
                !isLowRamDevice && memoryClassMb >= MID_MEMORY_CLASS_MB -> Tier.MID
                else -> Tier.LOW
            }

            val profile = when (tier) {
                Tier.HIGH -> DevicePerformanceProfile(
                    tier = tier,
                    memoryCachePercent = HIGH_MEMORY_CACHE_PERCENT,
                    diskCacheSizeMb = HIGH_DISK_CACHE_MB,
                )

                Tier.MID -> DevicePerformanceProfile(
                    tier = tier,
                    memoryCachePercent = MID_MEMORY_CACHE_PERCENT,
                    diskCacheSizeMb = MID_DISK_CACHE_MB,
                )

                Tier.LOW -> DevicePerformanceProfile(
                    tier = tier,
                    memoryCachePercent = LOW_MEMORY_CACHE_PERCENT,
                    diskCacheSizeMb = LOW_DISK_CACHE_MB,
                )
            }

            cachedProfile = profile
            return profile
        }

        @VisibleForTesting
        internal fun clearCachedProfile() {
            cachedProfile = null
        }
    }
}
