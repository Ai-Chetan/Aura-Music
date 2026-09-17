package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Transaction
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongsWithTags(): Flow<List<SongWithTags>>

    @Transaction
    @Query(
        """
        SELECT songs.* FROM songs
        INNER JOIN song_tag_cross_ref ON songs.id = song_tag_cross_ref.songId
        INNER JOIN tags ON tags.id = song_tag_cross_ref.tagId
        WHERE tags.name IN (:tagNames)
        GROUP BY songs.id
        HAVING COUNT(DISTINCT tags.name) = :requiredMatchCount
        ORDER BY songs.dateAdded DESC
        """
    )
    fun getSongsMatchingAllTags(tagNames: List<String>, requiredMatchCount: Int): Flow<List<SongEntity>>

    @Query(
        """
        SELECT DISTINCT songs.* FROM songs
        INNER JOIN song_tag_cross_ref ON songs.id = song_tag_cross_ref.songId
        INNER JOIN tags ON tags.id = song_tag_cross_ref.tagId
        WHERE tags.name IN (:tagNames)
        ORDER BY songs.dateAdded DESC
        """
    )
    fun getSongsMatchingAnyTag(tagNames: List<String>): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<Long>): List<SongEntity>

    @Insert
    suspend fun insertSong(song: SongEntity): Long

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, now: Long)

    @Query("SELECT * FROM songs ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Delete
    suspend fun deleteSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    @Query("SELECT * FROM songs WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getBySourceUrl(sourceUrl: String): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
}
