package com.rpeters.cinefintv

import android.app.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.rpeters.cinefintv.data.repository.RemoteConfigRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import com.rpeters.cinefintv.utils.SecureLogger

@HiltAndroidApp
class CinefinTvApplication : Application() {

    private companion object {
        const val TAG = "AppStartup"
        // Mirrors Android trim levels without referencing deprecated framework constants.
        const val TRIM_MEMORY_RUNNING_LOW_LEVEL = 10
        const val TRIM_MEMORY_UI_HIDDEN_LEVEL = 20
    }

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

    @Inject
    lateinit var authRepository: com.rpeters.cinefintv.data.repository.JellyfinAuthRepository

    override fun onCreate() {
        super.onCreate()
        SecureLogger.d(TAG, "Application onCreate")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Initialize Remote Config
        scope.launch {
            SecureLogger.d(TAG, "Remote config fetch start")
            runCatching {
                remoteConfigRepository.fetchAndActivate()
            }.onSuccess {
                SecureLogger.d(TAG, "Remote config fetch complete")
            }.onFailure { error ->
                SecureLogger.w(TAG, "Remote config fetch failed", error)
            }
        }

        // Eagerly restore session to speed up startup
        scope.launch {
            SecureLogger.d(TAG, "Session restore start")
            val restored = runCatching {
                authRepository.tryRestoreSession()
            }.onFailure { error ->
                SecureLogger.w(TAG, "Session restore failed", error)
            }.getOrDefault(false)
            SecureLogger.d(
                TAG,
                "Session restore complete: restored=$restored currentServerPresent=${authRepository.currentServer.value != null} isConnected=${authRepository.isConnected.value}",
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Clear Coil memory cache on critical memory pressure
        if (
            level in TRIM_MEMORY_RUNNING_LOW_LEVEL until TRIM_MEMORY_UI_HIDDEN_LEVEL
        ) {
            coil3.SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }
}
