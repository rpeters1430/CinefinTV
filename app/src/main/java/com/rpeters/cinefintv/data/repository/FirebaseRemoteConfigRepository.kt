package com.rpeters.cinefintv.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [RemoteConfigRepository] using Firebase Remote Config.
 */
@Singleton
class FirebaseRemoteConfigRepository @Inject constructor() : RemoteConfigRepository {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600L) // Cache for 1 hour
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
        }
    }

    override fun getString(key: String): String = remoteConfig.getString(key)

    override fun getBoolean(key: String): Boolean = remoteConfig.getBoolean(key)

    override fun getLong(key: String): Long = remoteConfig.getLong(key)

    override fun getDouble(key: String): Double = remoteConfig.getDouble(key)
}