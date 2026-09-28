package com.aura.music.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user playlist: an ordered-by-addition collection that can hold BOTH
 * downloaded songs and saved streaming bookmarks. Membership lives in the
 * two cross-ref tables below, mirroring the tag architecture.
 */
@Entity(tableName = "playlists", indices = [Index(value = ["name"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["songId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long
)

@Entity(
    tableName = "playlist_saved_cross_ref",
    primaryKeys = ["playlistId", "savedTrackId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["savedTrackId"])],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedTrackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSavedCrossRef(
    val playlistId: Long,
    val savedTrackId: Long
)

/** Playlist row + live item count, for the Library playlists rail. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int
)
