package com.aura.music.di

import com.aura.music.data.repository.SavedTrackRepositoryImpl
import com.aura.music.data.repository.SongRepositoryImpl
import com.aura.music.data.repository.TagRepositoryImpl
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(
        impl: SongRepositoryImpl
    ): SongRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(
        impl: TagRepositoryImpl
    ): TagRepository

    @Binds
    @Singleton
    abstract fun bindSavedTrackRepository(
        impl: SavedTrackRepositoryImpl
    ): SavedTrackRepository
}