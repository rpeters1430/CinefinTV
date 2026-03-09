package com.rpeters.cinefintv.update

import android.os.Build
import org.junit.Assert.assertEquals
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
}
