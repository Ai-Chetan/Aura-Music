package com.aura.music.domain.repository

import com.aura.music.data.db.PlaylistEntity
import com.aura.music.data.db.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

/** One playlist in a backup file: members referenced by canonical URL. */
data class PlaylistExportEntry(
    val name: String,
    val songUrls: List<String>,
    val savedUrls: List<String>
)

interface PlaylistRepository {
    /** All playlists with live item counts, newest first — the Library rail. */
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    /** Song ids currently in the playlist (membership drives the detail list). */
    fun observeSongIdsIn(playlistId: Long): Flow<Set<Long>>

    /** Saved-track ids currently in the playlist. */
    fun observeSavedIdsIn(playlistId: Long): Flow<Set<Long>>

    /** Creates (or finds) a playlist by name; returns its id. */
    suspend fun getOrCreate(name: String): Long

    suspend fun rename(playlistId: Long, name: String)

    suspend fun delete(playlistId: Long)

    suspend fun addSong(playlistId: Long, songId: Long)

    suspend fun removeSong(playlistId: Long, songId: Long)

    suspend fun addSaved(playlistId: Long, savedTrackId: Long)

    suspend fun removeSaved(playlistId: Long, savedTrackId: Long)

    /** Playlist ids containing the song — seeds the picker dialog membership. */
    suspend fun playlistIdsForSong(songId: Long): Set<Long>

    suspend fun playlistIdsForSaved(savedTrackId: Long): Set<Long>

    /** Full playlist → URL-membership snapshot for backup export. */
    suspend fun snapshotForExport(): List<PlaylistExportEntry>

    /**
     * Backup restore: creates each playlist by name and re-links members by
     * URL against the CURRENT library (songs + bookmarks). Tracks that don't
     * exist yet are skipped — re-importing the same file is idempotent.
     */
    suspend fun restoreFromExport(entries: List<PlaylistExportEntry>)
}
