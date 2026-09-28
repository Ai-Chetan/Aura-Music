package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row per group-by: how many items carry a tag. */
data class TagUsageRow(val tagId: Long, val uses: Int)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getTagByName(name: String): TagEntity?

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun getTagCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToSong(crossRef: SongTagCrossRef)

    @Delete
    suspend fun removeTagFromSong(crossRef: SongTagCrossRef)

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM song_tag_cross_ref WHERE songId = :songId) ORDER BY name ASC")
    fun getTagsForSong(songId: Long): Flow<List<TagEntity>>

    /** Deletes the tag itself; both cross-ref tables cascade away with it. */
    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Query("SELECT tagId, COUNT(*) AS uses FROM song_tag_cross_ref GROUP BY tagId")
    suspend fun countSongUsage(): List<TagUsageRow>

    @Query("SELECT tagId, COUNT(*) AS uses FROM saved_track_tag_cross_ref GROUP BY tagId")
    suspend fun countSavedUsage(): List<TagUsageRow>
}