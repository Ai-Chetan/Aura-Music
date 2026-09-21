package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTrackDao {
    @Query("SELECT * FROM saved_tracks ORDER BY dateSaved DESC")
    fun observeAll(): Flow<List<SavedTrackEntity>>

    @Transaction
    @Query("SELECT * FROM saved_tracks ORDER BY dateSaved DESC")
    fun observeAllWithTags(): Flow<List<SavedTrackWithTags>>

    @Query("SELECT * FROM saved_tracks WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): SavedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: SavedTrackEntity): Long

    @Query("DELETE FROM saved_tracks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_tracks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assignTag(crossRef: SavedTrackTagCrossRef)

    @Query("DELETE FROM saved_track_tag_cross_ref WHERE savedTrackId = :trackId AND tagId = :tagId")
    suspend fun unassignTag(trackId: Long, tagId: Long)

    @Query(
        "UPDATE saved_tracks SET playCount = playCount + 1, lastPlayedAt = :now " +
            "WHERE url = :url"
    )
    suspend fun recordPlay(url: String, now: Long)

    @Query("UPDATE saved_tracks SET skipCount = skipCount + 1 WHERE url = :url")
    suspend fun recordSkip(url: String)
}
