package com.rpeters.cinefintv.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentityProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val deviceId: String by lazy { resolveDeviceId() }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL?.takeIf { it.isNotBlank() }
        return listOfNotNull(manufacturer, model).joinToString(separator = " ").ifBlank { "Android" }
    }

    fun deviceId(): String = deviceId

    fun clientVersion(): String = BuildConfigWrapper.versionName

    fun clientName(): String = "Cinefin Android"

    private fun resolveDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank()) {
            return androidId
        }
        return UUID.randomUUID().toString()
    }
}

/**
 * Wrapper around BuildConfig so the provider can be unit-tested.
 */
object BuildConfigWrapper {
    val versionName: String
        get() = com.rpeters.cinefintv.BuildConfig.VERSION_NAME
}
