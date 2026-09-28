package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, " +
            "(SELECT COUNT(*) FROM playlist_song_cross_ref ps WHERE ps.playlistId = p.id) + " +
            "(SELECT COUNT(*) FROM playlist_saved_cross_ref pv WHERE pv.playlistId = p.id) " +
            "AS itemCount FROM playlists p ORDER BY p.createdAt DESC"
    )
    fun observeAllWithCounts(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PlaylistEntity?

    /** IGNORE + unique name: -1 means the name exists — resolve via [getByName]. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT songId FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    fun observeSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT savedTrackId FROM playlist_saved_cross_ref WHERE playlistId = :playlistId")
    fun observeSavedIds(playlistId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSaved(ref: PlaylistSavedCrossRef)

    @Query(
        "DELETE FROM playlist_saved_cross_ref " +
            "WHERE playlistId = :playlistId AND savedTrackId = :savedTrackId"
    )
    suspend fun removeSaved(playlistId: Long, savedTrackId: Long)

    @Query("SELECT playlistId FROM playlist_song_cross_ref WHERE songId = :songId")
    suspend fun playlistIdsForSong(songId: Long): List<Long>

    @Query("SELECT playlistId FROM playlist_saved_cross_ref WHERE savedTrackId = :savedTrackId")
    suspend fun playlistIdsForSaved(savedTrackId: Long): List<Long>

    // ---- Backup snapshot support ----

    @Query(
        "SELECT s.sourceUrl FROM songs s " +
            "INNER JOIN playlist_song_cross_ref r ON s.id = r.songId " +
            "WHERE r.playlistId = :playlistId"
    )
    suspend fun songUrlsIn(playlistId: Long): List<String>

    @Query(
        "SELECT t.url FROM saved_tracks t " +
            "INNER JOIN playlist_saved_cross_ref r ON t.id = r.savedTrackId " +
            "WHERE r.playlistId = :playlistId"
    )
    suspend fun savedUrlsIn(playlistId: Long): List<String>
}
