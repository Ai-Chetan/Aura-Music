package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    // Duplicate check (getBySourceUrl) runs per download and per playlist
    // video — the index keeps those lookups off full-table scans.
    // Uniqueness is enforced (canonical URLs); inserts use IGNORE so a
    // lost-update race resolves to the existing row instead of crashing.
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
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
    val lastPlayedAt: Long? = null,
    /** Skipped before meaningfully played — the recommendation engine's negative signal. */
    val skipCount: Int = 0
)