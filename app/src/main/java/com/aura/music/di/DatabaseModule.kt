package com.aura.music.di

import android.content.Context
import androidx.room.Room
import com.aura.music.data.db.AppDatabase
import com.aura.music.data.db.PlaylistDao
import com.aura.music.data.db.QueueStateDao
import com.aura.music.data.db.SongDao
import com.aura.music.data.db.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao = database.tagDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideQueueStateDao(database: AppDatabase): QueueStateDao = database.queueStateDao()
}