package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String?,
    val sourceUrl: String,
    val sourcePlatform: String,
    val localFilePath: String,
    val thumbnailPath: String?,
    val durationMs: Long,
    val bitrateKbps: Int?,
    val audioFormat: String,
    val isLossless: Boolean,
    val dateAdded: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null
)