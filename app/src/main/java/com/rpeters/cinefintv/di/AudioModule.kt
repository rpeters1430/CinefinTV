package com.rpeters.cinefintv.di

import com.rpeters.cinefintv.ui.player.audio.AudioControllerConnector
import com.rpeters.cinefintv.ui.player.audio.AudioMediaItemFactory
import com.rpeters.cinefintv.ui.player.audio.AudioPlaybackPositionRepository
import com.rpeters.cinefintv.ui.player.audio.DefaultAudioMediaItemFactory
import com.rpeters.cinefintv.ui.player.audio.DefaultAudioPlaybackPositionRepository
import com.rpeters.cinefintv.ui.player.audio.Media3AudioControllerConnector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioControllerConnector(
        impl: Media3AudioControllerConnector,
    ): AudioControllerConnector

    @Binds
    @Singleton
    abstract fun bindAudioPlaybackPositionRepository(
        impl: DefaultAudioPlaybackPositionRepository,
    ): AudioPlaybackPositionRepository

    @Binds
    @Singleton
    abstract fun bindAudioMediaItemFactory(
        impl: DefaultAudioMediaItemFactory,
    ): AudioMediaItemFactory
}
