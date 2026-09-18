package com.aura.music.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * v1 → v2: unique index on songs.sourceUrl. Pre-existing duplicate URLs
 * (from concurrent downloads before the constraint) are collapsed first,
 * keeping the oldest row, so the migration can't fail on user data.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM songs WHERE id NOT IN " +
                "(SELECT MIN(id) FROM songs GROUP BY sourceUrl)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_songs_sourceUrl " +
                "ON songs(sourceUrl)"
        )
    }
}

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
            .addMigrations(MIGRATION_1_2)
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