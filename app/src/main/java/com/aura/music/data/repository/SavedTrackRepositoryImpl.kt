package com.aura.music.data.repository

import com.aura.music.data.db.SavedTrackDao
import com.aura.music.data.db.SavedTrackEntity
import com.aura.music.data.db.SavedTrackTagCrossRef
import com.aura.music.data.db.SavedTrackWithTags
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.YouTubeTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedTrackRepositoryImpl @Inject constructor(
    private val dao: SavedTrackDao
) : SavedTrackRepository {

    override fun observeSavedTracks(): Flow<List<SavedTrackEntity>> =
        dao.observeAll()

    override fun observeSavedWithTags(): Flow<List<SavedTrackWithTags>> =
        dao.observeAllWithTags()

    override fun observeSavedUrls(): Flow<Set<String>> =
        dao.observeAll().map { list -> list.map { it.url }.toSet() }

    override suspend fun save(track: YouTubeTrack): Boolean {
        if (dao.getByUrl(track.url) != null) return false
        dao.insert(
            SavedTrackEntity(
                url = track.url,
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                durationMs = track.durationMs,
                dateSaved = System.currentTimeMillis()
            )
        )
        return true
    }

    override suspend fun unsaveByUrl(url: String) {
        dao.deleteByUrl(url)
    }

    override suspend fun unsaveById(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun isSaved(url: String): Boolean =
        dao.getByUrl(url) != null

    override suspend fun assignTag(trackId: Long, tagId: Long) {
        dao.assignTag(SavedTrackTagCrossRef(trackId, tagId))
    }

    override suspend fun unassignTag(trackId: Long, tagId: Long) {
        dao.unassignTag(trackId, tagId)
    }

    override suspend fun recordPlay(url: String) {
        try {
            dao.recordPlay(url, System.currentTimeMillis())
        } catch (_: Exception) {
        }
    }
}
