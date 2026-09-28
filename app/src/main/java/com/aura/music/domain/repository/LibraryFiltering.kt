package com.aura.music.domain.repository

import com.aura.music.data.db.SavedTrackWithTags
import com.aura.music.data.db.SongWithTags

/** Library ordering. RECENT (newest first) is the default. */
enum class LibrarySortMode(val label: String) {
    RECENT("Recently added"),
    OLDEST("Oldest first"),
    TITLE_ASC("Title A–Z"),
    TITLE_DESC("Title Z–A"),
    ARTIST_ASC("Artist A–Z"),
    DURATION_LONG("Longest first"),
    DURATION_SHORT("Shortest first")
}

/** Quick type segment over the merged library list. */
enum class LibrarySegment(val label: String) {
    ALL("All"),
    OFFLINE("Offline"),
    SAVED("Saved")
}

/**
 * Merges downloads and saved bookmarks into one deduped list keyed by
 * canonical URL: a track that exists in both keeps the download as its
 * playback side and the bookmark as its streaming twin, with the union of
 * both sides' tags.
 */
fun mergeUnifiedTracks(
    songs: List<SongWithTags>,
    saved: List<SavedTrackWithTags>
): List<UnifiedTrack> {
    val byUrl = LinkedHashMap<String, UnifiedTrack>()
    songs.forEach { item ->
        val song = UnifiedTrack.fromSong(item)
        byUrl[song.url] = song
    }
    saved.forEach { item ->
        val bookmark = UnifiedTrack.fromSaved(item)
        val existing = byUrl[bookmark.url]
        byUrl[bookmark.url] = if (existing == null) {
            bookmark
        } else {
            // Download wins playback; tags union so nothing is hidden.
            existing.copy(
                saved = bookmark.saved,
                tags = (existing.tags + bookmark.tags).distinctBy { it.id }
            )
        }
    }
    return byUrl.values.toList()
}

/**
 * Single filter implementation for the unified list and playlists.
 * Excluded tags always win (hidden + never queued); includes are ALL/ANY
 * per [matchAll]; the segment narrows by track kind.
 */
fun matchesLibraryFilters(
    track: UnifiedTrack,
    query: String,
    selected: Set<String>,
    excluded: Set<String>,
    matchAll: Boolean,
    segment: LibrarySegment = LibrarySegment.ALL
): Boolean {
    if (segment == LibrarySegment.OFFLINE && !track.isOffline) return false
    if (segment == LibrarySegment.SAVED && track.saved == null) return false

    val tagNames = track.tags.map { it.name }.toSet()
    if (tagNames.any { it in excluded }) return false
    val matchesTags = when {
        selected.isEmpty() -> true
        matchAll -> tagNames.containsAll(selected)
        else -> tagNames.any { it in selected }
    }
    val normalizedQuery = query.trim()
    val matchesQuery = normalizedQuery.isEmpty() ||
        track.title.contains(normalizedQuery, ignoreCase = true) ||
        (track.artist?.contains(normalizedQuery, ignoreCase = true) == true)
    return matchesTags && matchesQuery
}

/** Shared ordering vocabulary over unified tracks. */
fun sortUnifiedTracks(
    tracks: List<UnifiedTrack>,
    sort: LibrarySortMode
): List<UnifiedTrack> = when (sort) {
    LibrarySortMode.RECENT -> tracks.sortedByDescending { it.dateAdded }
    LibrarySortMode.OLDEST -> tracks.sortedBy { it.dateAdded }
    LibrarySortMode.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
    LibrarySortMode.TITLE_DESC -> tracks.sortedByDescending { it.title.lowercase() }
    LibrarySortMode.ARTIST_ASC -> tracks.sortedBy { (it.artist ?: "").lowercase() }
    LibrarySortMode.DURATION_LONG -> tracks.sortedByDescending { it.durationMs }
    LibrarySortMode.DURATION_SHORT -> tracks.sortedBy { it.durationMs }
}

fun filterUnifiedTracks(
    tracks: List<UnifiedTrack>,
    query: String,
    selected: Set<String>,
    excluded: Set<String>,
    matchAll: Boolean,
    segment: LibrarySegment,
    sort: LibrarySortMode
): List<UnifiedTrack> = sortUnifiedTracks(
    tracks.filter {
        matchesLibraryFilters(it, query, selected, excluded, matchAll, segment)
    },
    sort
)
