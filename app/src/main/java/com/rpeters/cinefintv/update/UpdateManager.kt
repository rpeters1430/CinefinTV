package com.rpeters.cinefintv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val updateUrl: String,
    val releaseNotes: String? = null,
    val isCritical: Boolean = false
)

sealed class UpdateStatus {
    data object NoUpdate : UpdateStatus()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

sealed class UpdateInstallResult {
    data object InstallerLaunched : UpdateInstallResult()
    data object PermissionRequired : UpdateInstallResult()
}

internal const val FOREGROUND_UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000L

internal enum class InstallAction {
    LaunchInstaller,
    RequestUnknownSourcesPermission,
}

internal fun determineInstallAction(
    sdkInt: Int,
    canRequestPackageInstalls: Boolean,
): InstallAction {
    return if (sdkInt >= Build.VERSION_CODES.O && !canRequestPackageInstalls) {
        InstallAction.RequestUnknownSourcesPermission
    } else {
        InstallAction.LaunchInstaller
    }
}

internal fun shouldCheckForUpdate(
    lastCheckedAtMs: Long,
    nowMs: Long,
    minIntervalMs: Long = FOREGROUND_UPDATE_CHECK_INTERVAL_MS,
): Boolean {
    if (lastCheckedAtMs <= 0L) return true
    return nowMs - lastCheckedAtMs >= minIntervalMs
}

// URL pointing to the version JSON file in your repository
private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/rpeters1430/CinefinTV/main/updates/version.json"

@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(UPDATE_JSON_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext UpdateStatus.Error("Failed to check for updates: ${response.code}")

            val body = response.body.string()
            Log.d("UpdateManager", "Received version JSON: $body")
            val updateInfo = json.decodeFromString<UpdateInfo>(body)

            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
            
            Log.d("UpdateManager", "Current versionCode: $currentVersionCode, Remote versionCode: ${updateInfo.versionCode}")

            if (updateInfo.versionCode > currentVersionCode) {
                Log.i("UpdateManager", "Update available: ${updateInfo.versionName} (${updateInfo.versionCode})")
                UpdateStatus.UpdateAvailable(updateInfo)
            } else {
                Log.i("UpdateManager", "App is up to date")
                UpdateStatus.NoUpdate
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking for update", e)
            UpdateStatus.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun downloadAndInstallApk(
        updateInfo: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): Result<UpdateInstallResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(updateInfo.updateUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK: ${response.code}"))
            }

            val body = requireNotNull(response.body) { "Empty response body" }
            val totalBytes = body.contentLength()
            
            val apkFile = File(context.cacheDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            onProgress(totalRead.toFloat() / totalBytes)
                        }
                    }
                }
            }

            val installResult = installApk(apkFile)
            Result.success(installResult)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error downloading update", e)
            Result.failure(e)
        }
    }

    private fun installApk(file: File): UpdateInstallResult {
        return when (
            determineInstallAction(
                sdkInt = Build.VERSION.SDK_INT,
                canRequestPackageInstalls = context.packageManager.canRequestPackageInstalls(),
            )
        ) {
            InstallAction.RequestUnknownSourcesPermission -> {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
                UpdateInstallResult.PermissionRequired
            }

            InstallAction.LaunchInstaller -> {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setData(uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)
                UpdateInstallResult.InstallerLaunched
            }
        }
    }
}
