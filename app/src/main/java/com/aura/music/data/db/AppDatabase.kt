package com.aura.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        TagEntity::class,
        SongTagCrossRef::class,
        QueueStateEntity::class,
        SavedTrackEntity::class,
        SavedTrackTagCrossRef::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun tagDao(): TagDao
    abstract fun queueStateDao(): QueueStateDao
    abstract fun savedTrackDao(): SavedTrackDao

    companion object {
        const val DATABASE_NAME = "aura_music.db"
    }
}