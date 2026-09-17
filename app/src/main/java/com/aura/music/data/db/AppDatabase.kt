package com.aura.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        TagEntity::class,
        SongTagCrossRef::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        QueueStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun tagDao(): TagDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueStateDao(): QueueStateDao

    companion object {
        const val DATABASE_NAME = "aura_music.db"
    }
}