package com.aura.music.domain.repository

import com.aura.music.data.db.SavedTrackEntity
import com.aura.music.data.db.SavedTrackWithTags
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.SongWithTags
import com.aura.music.data.db.TagEntity

/**
 * One library track of either kind — a downloaded song or a saved streaming
 * bookmark — carrying the full backing entity so playback needs no extra
 * lookups: offline rows queue as-is (instant, local file), saved rows
 * resolve to stream transients. [tags] rides along for filtering and rows.
 * Exactly one of [song]/[saved] is set.
 */
data class UnifiedTrack(
    val song: SongEntity?,
    val saved: SavedTrackEntity?,
    val tags: List<TagEntity> = emptyList()
) {
    val isOffline: Boolean get() = song != null

    /** songs.id or saved_tracks.id — stable key for rows and selection. */
    val id: Long get() = song?.id ?: saved?.id ?: -1L

    /** Canonical watch URL — the identity used for dedupe and sessions. */
    val url: String get() = song?.sourceUrl ?: saved?.url.orEmpty()

    val title: String get() = song?.title ?: saved?.title.orEmpty()
    val artist: String? get() = song?.artist ?: saved?.artist
    val durationMs: Long get() = song?.durationMs ?: saved?.durationMs ?: 0L
    val thumbnail: String? get() = song?.thumbnailPath ?: saved?.thumbnailUrl
    val dateAdded: Long get() = song?.dateAdded ?: saved?.dateSaved ?: 0L

    companion object {
        fun fromSong(item: SongWithTags): UnifiedTrack =
            UnifiedTrack(song = item.song, saved = null, tags = item.tags)

        fun fromSaved(item: SavedTrackWithTags): UnifiedTrack =
            UnifiedTrack(song = null, saved = item.track, tags = item.tags)
    }
}

fun List<SongWithTags>.asUnified(): List<UnifiedTrack> = map { UnifiedTrack.fromSong(it) }

fun List<SavedTrackWithTags>.asUnifiedSaved(): List<UnifiedTrack> = map { UnifiedTrack.fromSaved(it) }
