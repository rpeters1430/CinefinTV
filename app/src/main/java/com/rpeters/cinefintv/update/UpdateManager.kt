package com.rpeters.cinefintv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

// Placeholder URL - replace with your actual hosting URL (e.g. GitHub Raw)
private const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/username/CinefinTV/main/updates/version.json"

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
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

            val body = response.body?.string() ?: return@withContext UpdateStatus.Error("Empty response body")
            val updateInfo = json.decodeFromString<UpdateInfo>(body)

            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }

            if (updateInfo.versionCode > currentVersionCode) {
                UpdateStatus.UpdateAvailable(updateInfo)
            } else {
                UpdateStatus.NoUpdate
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking for update", e)
            UpdateStatus.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun downloadAndInstallApk(updateInfo: UpdateInfo, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(updateInfo.updateUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure<Unit>(Exception("Failed to download APK: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure<Unit>(Exception("Empty download body"))
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

            installApk(apkFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error downloading update", e)
            Result.failure(e)
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
}
