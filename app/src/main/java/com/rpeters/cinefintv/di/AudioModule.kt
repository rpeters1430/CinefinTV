package com.rpeters.cinefintv.di

// TODO Task 22: restore when audio player is copied
// import com.rpeters.cinefintv.ui.player.audio.AudioServiceForegroundIntentProvider
// import com.rpeters.cinefintv.ui.player.audio.DefaultAudioServiceForegroundIntentProvider
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    // TODO Task 22: restore when audio player is copied
    // @Binds
    // @Singleton
    // abstract fun bindAudioServiceForegroundIntentProvider(
    //     impl: DefaultAudioServiceForegroundIntentProvider,
    // ): AudioServiceForegroundIntentProvider
}
