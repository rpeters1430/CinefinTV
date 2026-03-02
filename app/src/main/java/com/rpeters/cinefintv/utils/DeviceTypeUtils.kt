package com.rpeters.cinefintv.utils

import android.os.Build

object DeviceTypeUtils {

    /**
     * Detects if the current device is an emulator.
     * Useful for disabling hardware-intensive features (like complex shaders) that crash on virtual GPUs.
     */
    fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_google") ||
            Build.PRODUCT.contains("google_sdk") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("sdk_x86") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.PRODUCT.contains("emulator") ||
            Build.PRODUCT.contains("simulator")
    }

}
