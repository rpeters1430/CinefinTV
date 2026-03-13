package com.rpeters.cinefintv.di

import com.rpeters.cinefintv.data.repository.FirebaseRemoteConfigRepository
import com.rpeters.cinefintv.data.repository.RemoteConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides a no-op RemoteConfigRepository for CinefinTV (TV app has no Firebase dependency).
 * If Firebase Remote Config is added later, replace NoOpRemoteConfigRepository with the
 * Firebase-backed implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteConfigModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(
        impl: FirebaseRemoteConfigRepository,
    ): RemoteConfigRepository
}
