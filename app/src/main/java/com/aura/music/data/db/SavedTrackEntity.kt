package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bookmarked YouTube track that streams on demand (needs internet).
 * Unlike [SongEntity] (fully downloaded, offline-capable), a saved track
 * stores only metadata + watch URL — no audio bytes. Tapping one resolves
 * the audio URL (ideally pre-warmed) and plays near-instantly via the
 * ExoPlayer disk cache.
 */
@Entity(
    tableName = "saved_tracks",
    indices = [Index(value = ["url"], unique = true)]
)
data class SavedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val dateSaved: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null
)
