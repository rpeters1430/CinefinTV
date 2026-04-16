package com.rpeters.cinefintv

import android.app.Application
import com.rpeters.cinefintv.data.repository.RemoteConfigRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CinefinTvApplication : Application() {

    private companion object {
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
        
        val scope = MainScope()

        // Initialize Remote Config
        scope.launch {
            remoteConfigRepository.fetchAndActivate()
        }

        // Eagerly restore session to speed up startup
        scope.launch {
            authRepository.tryRestoreSession()
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
