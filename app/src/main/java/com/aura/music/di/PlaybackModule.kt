package com.aura.music.di

import com.aura.music.playback.Media3PlaybackController
import com.aura.music.playback.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackController(
        impl: Media3PlaybackController
    ): PlaybackController
}