package com.aura.music.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aura.music.data.db.AppDatabase
import com.aura.music.data.db.QueueStateDao
import com.aura.music.data.db.SavedTrackDao
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

/**
 * v2 → v3: saved streaming bookmarks (Saved tab). Fresh table, no data to
 * migrate — plain CREATE TABLE + unique index on url.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS saved_tracks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "url TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "artist TEXT, " +
                "thumbnailUrl TEXT, " +
                "durationMs INTEGER NOT NULL, " +
                "dateSaved INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_saved_tracks_url " +
                "ON saved_tracks(url)"
        )
    }
}

/**
 * v6 → v7: remembers which vault song was current (streaming tails can't be
 * restored, so the index is re-anchored to it on launch).
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE queue_state ADD COLUMN currentSongId INTEGER NOT NULL DEFAULT -1"
        )
    }
}
/**
 * v5 → v6: drops the never-shipped playlists tables (no UI ever used them).
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS playlist_song_cross_ref")
        db.execSQL("DROP TABLE IF EXISTS playlists")
    }
}
/**
 * v4 → v5: play stats on streaming bookmarks so recommendations learn from
 * what you actually stream, not just what you download.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE saved_tracks ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE saved_tracks ADD COLUMN lastPlayedAt INTEGER"
        )
    }
}
/**
 * v3 → v4: tags for streaming bookmarks (many-to-many, cascading deletes).
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS saved_track_tag_cross_ref (" +
                "savedTrackId INTEGER NOT NULL, " +
                "tagId INTEGER NOT NULL, " +
                "PRIMARY KEY(savedTrackId, tagId), " +
                "FOREIGN KEY(savedTrackId) REFERENCES saved_tracks(id) ON DELETE CASCADE, " +
                "FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_saved_track_tag_cross_ref_tagId " +
                "ON saved_track_tag_cross_ref(tagId)"
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            // Destructive ONLY on downgrade: every upgrade path has an explicit
            // migration, so a released vault is never wiped by an update.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideSongDao(database: AppDatabase): SongDao = database.songDao()

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao = database.tagDao()

    @Provides
    fun provideQueueStateDao(database: AppDatabase): QueueStateDao = database.queueStateDao()

    @Provides
    fun provideSavedTrackDao(database: AppDatabase): SavedTrackDao = database.savedTrackDao()
}