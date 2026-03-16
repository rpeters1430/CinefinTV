package com.rpeters.cinefintv.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackPreferencesDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineProgressDataStore

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @PlaybackPreferencesDataStore
    fun providePlaybackPreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: kotlinx.coroutines.CoroutineScope,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("playback_preferences") },
        )
    }

    @Provides
    @Singleton
    @OfflineProgressDataStore
    fun provideOfflineProgressDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: kotlinx.coroutines.CoroutineScope,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { context.preferencesDataStoreFile("offline_progress_updates") },
        )
    }
}
