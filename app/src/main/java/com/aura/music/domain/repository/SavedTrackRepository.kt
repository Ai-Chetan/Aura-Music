package com.aura.music.domain.repository

import com.aura.music.data.db.SavedTrackEntity
import com.aura.music.data.db.SavedTrackWithTags
import kotlinx.coroutines.flow.Flow

/**
 * Bookmarked streaming tracks: metadata only, plays via internet.
 * Complements downloaded [SongEntity] rows (offline-capable).
 */
interface SavedTrackRepository {
    fun observeSavedTracks(): Flow<List<SavedTrackEntity>>

    /** Bookmarks with their tags — drives the Saved tab. */
    fun observeSavedWithTags(): Flow<List<SavedTrackWithTags>>

    /** Watch URLs currently bookmarked — drives save/unsave icon state. */
    fun observeSavedUrls(): Flow<Set<String>>

    /** Returns false when the URL was already saved. */
    suspend fun save(track: YouTubeTrack): Boolean

    suspend fun unsaveByUrl(url: String)

    suspend fun unsaveById(id: Long)

    suspend fun isSaved(url: String): Boolean

    suspend fun assignTag(trackId: Long, tagId: Long)

    suspend fun unassignTag(trackId: Long, tagId: Long)

    /** Counts a stream play — feeds recency-based recommendations. */
    suspend fun recordPlay(url: String)
}
