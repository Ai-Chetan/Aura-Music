package com.aura.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getTagById(tagId: Long): TagEntity?

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

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: Long)

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM song_tag_cross_ref WHERE songId = :songId) ORDER BY name ASC")
    fun getTagsForSong(songId: Long): Flow<List<TagEntity>>
}