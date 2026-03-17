package com.rpeters.cinefintv.update

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun determineInstallAction_requestsPermission_whenOreoOrHigherAndPermissionMissing() {
        val result = determineInstallAction(
            sdkInt = Build.VERSION_CODES.O,
            canRequestPackageInstalls = false,
        )

        assertEquals(InstallAction.RequestUnknownSourcesPermission, result)
    }

    @Test
    fun determineInstallAction_launchesInstaller_whenPermissionAlreadyGranted() {
        val result = determineInstallAction(
            sdkInt = Build.VERSION_CODES.O,
            canRequestPackageInstalls = true,
        )

        assertEquals(InstallAction.LaunchInstaller, result)
    }

    @Test
    fun determineInstallAction_launchesInstaller_belowOreo() {
        val result = determineInstallAction(
            sdkInt = Build.VERSION_CODES.N,
            canRequestPackageInstalls = false,
        )

        assertEquals(InstallAction.LaunchInstaller, result)
    }

    @Test
    fun shouldCheckForUpdate_returnsTrue_whenNeverChecked() {
        assertTrue(
            shouldCheckForUpdate(
                lastCheckedAtMs = 0L,
                nowMs = 1_000L,
            )
        )
    }

    @Test
    fun shouldCheckForUpdate_returnsFalse_withinCooldown() {
        assertFalse(
            shouldCheckForUpdate(
                lastCheckedAtMs = 10_000L,
                nowMs = 10_000L + FOREGROUND_UPDATE_CHECK_INTERVAL_MS - 1L,
            )
        )
    }

    @Test
    fun shouldCheckForUpdate_returnsTrue_afterCooldown() {
        assertTrue(
            shouldCheckForUpdate(
                lastCheckedAtMs = 10_000L,
                nowMs = 10_000L + FOREGROUND_UPDATE_CHECK_INTERVAL_MS,
            )
        )
    }
}
