package com.rpeters.cinefintv.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote config interface. For CinefinTV (TV app) there is no Firebase dependency,
 * so all values return safe defaults. The full Firebase implementation is deferred
 * to Task 24 if remote config is needed for TV.
 */
interface RemoteConfigRepository {
    suspend fun fetchAndActivate(): Boolean
    fun getString(key: String): String
    fun getBoolean(key: String): Boolean
    fun getLong(key: String): Long
    fun getDouble(key: String): Double
}

@Singleton
class NoOpRemoteConfigRepository @Inject constructor() : RemoteConfigRepository {
    override suspend fun fetchAndActivate(): Boolean = false
    override fun getString(key: String): String = ""
    override fun getBoolean(key: String): Boolean = false
    override fun getLong(key: String): Long = 0L
    override fun getDouble(key: String): Double = 0.0
}
