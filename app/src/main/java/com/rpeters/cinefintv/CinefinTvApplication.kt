package com.rpeters.cinefintv

import android.app.Application
import com.rpeters.cinefintv.data.repository.RemoteConfigRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CinefinTvApplication : Application() {

    @Inject
    lateinit var remoteConfigRepository: RemoteConfigRepository

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Remote Config
        MainScope().launch {
            remoteConfigRepository.fetchAndActivate()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Clear Coil memory cache on critical memory pressure
        if (level >= TRIM_MEMORY_MODERATE) {
            coil3.SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }
}
