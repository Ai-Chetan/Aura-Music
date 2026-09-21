package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Transaction
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongsWithTags(): Flow<List<SongWithTags>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<Long>): List<SongEntity>

    /**
     * IGNORE + unique sourceUrl: returns -1 when the URL already exists
     * (lost duplicate-check race) instead of throwing — callers resolve
     * the existing id via [getBySourceUrl].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: SongEntity): Long

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, now: Long)

    @Query("UPDATE songs SET skipCount = skipCount + 1 WHERE id = :songId")
    suspend fun incrementSkipCount(songId: Long)

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    @Query("SELECT * FROM songs WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getBySourceUrl(sourceUrl: String): SongEntity?

    /** One-query duplicate check for playlist/import previews (vs 50 sequential lookups). */
    @Query("SELECT sourceUrl FROM songs WHERE sourceUrl IN (:sourceUrls)")
    suspend fun getExistingSourceUrls(sourceUrls: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int
}
