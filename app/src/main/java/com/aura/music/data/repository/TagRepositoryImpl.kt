package com.aura.music.data.repository

import com.aura.music.data.db.SongTagCrossRef
import com.aura.music.data.db.TagDao
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao
) : TagRepository {

    override fun observeAllTags(): Flow<List<TagEntity>> =
        tagDao.getAllTags()

    override fun observeTagsForSong(songId: Long): Flow<List<TagEntity>> =
        tagDao.getTagsForSong(songId)

    override suspend fun createTag(name: String, colorHex: String?): Long {
        val normalizedName = normalizeTagName(name)
        require(normalizedName.isNotBlank()) { "Tag name cannot be blank" }

        val existing = tagDao.getTagByName(normalizedName)
        if (existing != null) return existing.id

        return tagDao.insertTag(
            TagEntity(
                name = normalizedName,
                colorHex = colorHex
            )
        )
    }

    override suspend fun getOrCreateTag(name: String, colorHex: String?): Long {
        val normalizedName = normalizeTagName(name)
        val existing = tagDao.getTagByName(normalizedName)
        if (existing != null) return existing.id

        val insertedId = tagDao.insertTag(
            TagEntity(
                name = normalizedName,
                colorHex = colorHex
            )
        )

        // If INSERT OR IGNORE returned -1 because another coroutine inserted
        // the same tag concurrently, look it up again.
        return if (insertedId == -1L) {
            tagDao.getTagByName(normalizedName)?.id ?: -1L
        } else {
            insertedId
        }
    }

    override suspend fun addTagToSong(songId: Long, tagId: Long) {
        tagDao.addTagToSong(SongTagCrossRef(songId = songId, tagId = tagId))
    }

    override suspend fun removeTagFromSong(songId: Long, tagId: Long) {
        tagDao.removeTagFromSong(SongTagCrossRef(songId = songId, tagId = tagId))
    }

    override suspend fun deleteTag(tagId: Long) {
        tagDao.deleteTagById(tagId)
    }

    private fun normalizeTagName(name: String): String =
        name.trim().lowercase()
}