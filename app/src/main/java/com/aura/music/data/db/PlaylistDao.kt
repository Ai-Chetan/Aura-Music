package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)

    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.position ASC
        """
    )
    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query(
        "UPDATE playlist_song_cross_ref SET position = :position WHERE playlistId = :playlistId AND songId = :songId"
    )
    suspend fun updateSongPosition(playlistId: Long, songId: Long, position: Int)
}