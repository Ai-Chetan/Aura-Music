package com.aura.music.domain.repository

import com.aura.music.data.db.TagEntity
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeAllTags(): Flow<List<TagEntity>>

    fun observeTagsForSong(songId: Long): Flow<List<TagEntity>>

    suspend fun getOrCreateTag(name: String, colorHex: String? = null): Long

    suspend fun addTagToSong(songId: Long, tagId: Long)

    suspend fun removeTagFromSong(songId: Long, tagId: Long)
}