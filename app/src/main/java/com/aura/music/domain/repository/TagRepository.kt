package com.aura.music.domain.repository

import com.aura.music.data.db.TagEntity
import kotlinx.coroutines.flow.Flow

/** How many items carry a tag: downloaded songs and saved bookmarks. */
data class TagUsage(val songs: Int, val savedTracks: Int)

interface TagRepository {
    fun observeAllTags(): Flow<List<TagEntity>>

    fun observeTagsForSong(songId: Long): Flow<List<TagEntity>>

    suspend fun getOrCreateTag(name: String, colorHex: String? = null): Long

    suspend fun addTagToSong(songId: Long, tagId: Long)

    suspend fun removeTagFromSong(songId: Long, tagId: Long)

    /** Permanently deletes a tag — assignments cascade away in the DB. */
    suspend fun deleteTag(tagId: Long)

    /** Usage count per tag id, for the Manage Tags dialog. */
    suspend fun getTagUsage(): Map<Long, TagUsage>
}