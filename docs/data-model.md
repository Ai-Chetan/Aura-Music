# Data Model & Internal API Contracts

## 1. Room entities

### Song (downloaded vault entry)

```kotlin
@Entity(
    tableName = "songs",
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String?,
    val sourceUrl: String,            // canonical YouTube watch URL (unique)
    val sourcePlatform: String,       // "youtube"
    val localFilePath: String,        // absolute path in app-specific storage
    val thumbnailPath: String?,       // cached local thumbnail path
    val durationMs: Long,
    val bitrateKbps: Int?,
    val audioFormat: String,          // "opus" | "aac" | "unknown"
    val isLossless: Boolean = false,  // reserved; YouTube sources are never lossless
    val dateAdded: Long,              // epoch millis
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val skipCount: Int = 0            // early skips — the engine's negative signal
)
```

### Tag

```kotlin
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,        // unique, lowercase, e.g. "sad", "english", "party"
    val colorHex: String?    // for pill/chip color in UI
)
```

### SongTagCrossRef (many-to-many)

```kotlin
@Entity(
    tableName = "song_tag_cross_ref",
    primaryKeys = ["songId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class SongTagCrossRef(
    val songId: Long,
    val tagId: Long
)
```

### SavedTrack (streaming bookmark — metadata only, no audio bytes)

```kotlin
@Entity(
    tableName = "saved_tracks",
    indices = [Index(value = ["url"], unique = true)]
)
data class SavedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,                  // canonical watch URL (unique)
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val dateSaved: Long,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val skipCount: Int = 0
)
// SavedTrackTagCrossRef mirrors SongTagCrossRef for bookmarks;
// SavedTrackWithTags is the @Transaction relation used by the Saved tab.
```

### QueueState (persisted so the queue survives app restart)

```kotlin
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,   // singleton row
    val songIdsJson: String,       // ordered list of song IDs, comma-encoded
    val currentIndex: Int,
    val currentSongId: Long,       // anchor for resume (streaming tails shift indices)
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: String         // "off" | "one" | "all"
)
```

> **Schema version: 8.** Every upgrade path has an explicit migration (user vaults are never wiped; destructive fallback applies to downgrades only): 1→2 unique `songs.sourceUrl` index (dedupes first), 2→3 `saved_tracks` table, 3→4 saved-track tags cross-ref, 4→5 saved-track play stats, 5→6 drop of the never-shipped `playlists` tables, 6→7 `queue_state.currentSongId`, 7→8 `skipCount` on `songs` and `saved_tracks`.

### ListenStatsStore (not Room — DataStore)

Play/skip telemetry for **transient streaming tracks** that never get a Room row, plus per-artist aggregates. One JSON blob in the `aura_listen_stats` DataStore (write-through, bounded at 250 tracks / 150 artists, evicting the weakest signals):

```kotlin
data class TrackStat(url, title, artist, plays, skips, lastPlayedAt)
data class ArtistStat(plays, skips)

@Singleton class ListenStatsStore {
    suspend fun recordPlay(url, title, artist)
    suspend fun recordSkip(url, title, artist)
    fun affinity(artist: String?): Double   // ln(1+plays) − 1.6·ln(1+skips), clamped
    val stats: StateFlow<ListenStats>
}
```

The recommendation engine reads stream-track stats as radio seeds and artist stats as affinity/skip signals. `PlaybackPreferences` (DataStore) holds the Spice-up switch.

## 2. Relationship helper

```kotlin
data class SongWithTags(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SongTagCrossRef::class, parentColumn = "songId", entityColumn = "tagId")
    )
    val tags: List<TagEntity>
)
```

## 3. DAO contracts (key queries)

```kotlin
@Dao
interface SongDao {
    @Transaction
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongsWithTags(): Flow<List<SongWithTags>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getBySourceUrl(sourceUrl: String): SongEntity?

    @Query("SELECT sourceUrl FROM songs WHERE sourceUrl IN (:sourceUrls)")
    suspend fun getExistingSourceUrls(sourceUrls: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)  // -1 on duplicate URL; callers resolve the existing id
    suspend fun insertSong(song: SongEntity): Long

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, now: Long)

    @Query("UPDATE songs SET skipCount = skipCount + 1 WHERE id = :songId")
    suspend fun incrementSkipCount(songId: Long)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)
}

@Dao
interface SavedTrackDao {
    fun observeAll(): Flow<List<SavedTrackEntity>>
    @Transaction fun observeAllWithTags(): Flow<List<SavedTrackWithTags>>
    suspend fun getByUrl(url: String): SavedTrackEntity?
    suspend fun insert(track: SavedTrackEntity): Long       // IGNORE on unique url
    suspend fun deleteById(id: Long)
    suspend fun deleteByUrl(url: String)
    suspend fun recordPlay(url: String, now: Long)          // playCount + lastPlayedAt
    suspend fun recordSkip(url: String)                     // skipCount
}
```

## 4. Repository interfaces (domain layer contracts)

```kotlin
interface SongRepository {
    fun observeAllSongs(): Flow<List<SongWithTags>>
    fun observeSongsByTags(tagNames: List<String>, matchAll: Boolean): Flow<List<SongEntity>>
    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>
    fun searchSongs(query: String): Flow<List<SongEntity>>
    suspend fun addSongFromUrl(url: String): Result<Long>       // blocking enqueue + await
    suspend fun enqueueDownload(url: String, initialDelaySeconds: Long = 0): UUID
    fun observeDownloadWork(workId: UUID): Flow<WorkInfo?>
    fun observeActiveDownloads(): Flow<List<ActiveDownload>>    // live singles queue
    suspend fun cancelDownload(workId: UUID)
    // Starter-batch tracking (Getting Started selections):
    fun trackStarterBatch(items: List<BatchItem>)
    fun observeStarterBatch(): Flow<StarterBatchStatus?>
    suspend fun retryStarterBatch()
    fun clearStarterBatch()
    suspend fun previewFromUrl(url: String): Result<ExtractedStreamInfo>
    suspend fun deleteSong(songId: Long)                        // also removes audio + artwork files
    suspend fun recordPlay(songId: Long)
    suspend fun exportLibraryJson(excludeTagNames: Set<String>, onlySongIds: Set<Long>?): Result<String>
    suspend fun describeImport(json: String): Result<ImportPreview>
    suspend fun importLibraryJson(json: String, onProgress: ...): Result<ImportSummary>
    suspend fun describePlaylist(url: String): Result<PlaylistPreview>
    suspend fun enqueuePlaylistImport(url: String, playlistTag: String?): UUID   // background worker
    fun observePlaylistImports(): Flow<List<PlaylistImportState>>                // live playlist progress
}

interface TagRepository {
    fun observeAllTags(): Flow<List<TagEntity>>
    suspend fun getOrCreateTag(name: String, colorHex: String?): Long
    suspend fun addTagToSong(songId: Long, tagId: Long)
    suspend fun removeTagFromSong(songId: Long, tagId: Long)
}

interface SavedTrackRepository {
    fun observeSavedTracks(): Flow<List<SavedTrackEntity>>
    fun observeSavedWithTags(): Flow<List<SavedTrackWithTags>>
    fun observeSavedUrls(): Flow<Set<String>>
    suspend fun save(track: YouTubeTrack): Boolean      // false when already saved
    suspend fun unsaveByUrl(url: String)
    suspend fun unsaveById(id: Long)
    suspend fun isSaved(url: String): Boolean
    suspend fun assignTag(trackId: Long, tagId: Long)
    suspend fun unassignTag(trackId: Long, tagId: Long)
    suspend fun recordPlay(url: String)
}

interface ExtractionRepository {
    suspend fun resolveStreamInfo(url: String): ExtractedStreamInfo   // gated + retried (YtGate/ytRetry)
    suspend fun resolvePlaylist(url: String): ExtractedPlaylist       // gated + retried
    suspend fun searchMusic(query: String, maxResults: Int = 25): List<YouTubeTrack>  // YT Music song filter
    suspend fun getTrendingMusic(maxResults: Int = 30): List<YouTubeTrack>            // Trending kiosk
    suspend fun getRelatedTracks(url: String, maxResults: Int = 20): List<YouTubeTrack> // "up next" graph
    suspend fun downloadAudio(streamInfo: ExtractedStreamInfo, destination: File): DownloadResult
}

/** One YouTube Music search hit — streamable, downloadable, not yet saved. */
data class YouTubeTrack(
    val url: String,          // canonical watch URL
    val title: String,
    val artist: String?,
    val durationMs: Long,     // 0 when unknown (e.g. live)
    val thumbnailUrl: String?
)

data class ExtractedStreamInfo(
    val title: String,
    val uploader: String?,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val bestAudioStreamUrl: String,
    val bitrateKbps: Int,
    val codec: String   // "opus" | "aac"
)
```

## 5. Playback contract (what the UI talks to)

```kotlin
interface PlaybackController {
    val playbackState: StateFlow<PlaybackUiState>
    val waveform: StateFlow<FloatArray>

    fun playQueue(songs: List<SongEntity>, startIndex: Int = 0)
    fun playNext(song: SongEntity)          // insert right after current
    fun addToQueueEnd(song: SongEntity)
    fun playAtIndex(index: Int)
    fun removeFromQueue(index: Int)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun setShuffleEnabled(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)      // OFF, ONE, ALL
    fun reorderQueue(fromIndex: Int, toIndex: Int)
}

data class PlaybackUiState(
    val currentSong: SongEntity?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val queue: List<SongEntity>,
    val currentIndexInQueue: Int,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    val errorMessage: String? = null
)

enum class RepeatMode { OFF, ONE, ALL }
```

This maps almost directly onto Media3's `Player` interface — `PlaybackController` is a thin, testable wrapper around a `MediaController` bound to the `MediaSessionService`.

### UpNextManager (queue-filling brain)

```kotlin
// data/stream/UpNextManager.kt
@Singleton class UpNextManager {
    fun startPlaylistSession(tracks: List<YouTubeTrack>, index: Int)  // Saved/Downloads: order, then radio
    fun startRadio()                                                  // Top hits/For You/search: engine fills all
    fun clear()                                                       // hand back to a plain vault queue
    val spiceUp: StateFlow<Boolean>                                   // Library switch: pre-queue engine picks
    fun toggleSpiceUp()
    val events: SharedFlow<String>                                    // "Radio — recommendations…" toasts
}

// data/recommendations/RecommendationEngine.kt
sealed interface RadioPick {
    data class FromVault(val song: SongEntity) : RadioPick   // plays offline, no resolve step
    data class FromStream(val track: YouTubeTrack) : RadioPick
}

@Singleton class RecommendationEngine {
    suspend fun radioPicks(currentUrl: String?, excludeUrls: Set<String>, count: Int): List<RadioPick>
}
```

Skip/plays recording points: `PlaybackService` counts vault plays on track transitions; `Media3PlaybackController` detects skips on `SEEK` transitions (under 70% played / 15s+ short) and writes `skipCount` (Room) + `ListenStatsStore` (per-URL/artist).

## 6. Backup format

Versioned JSON. Every song entry keeps its canonical source URL (the re-download key) plus title/artist/duration/bitrate/format and its tags. Local file paths and thumbnails are intentionally not stored — they are device-specific and re-created on import.

```json
{
  "app": "aura", "version": 1, "exportedAt": 1758100000000,
  "songs": [
    { "title": "...", "artist": "...",
      "sourceUrl": "https://www.youtube.com/watch?v=...",
      "sourcePlatform": "youtube", "durationMs": 214000,
      "bitrateKbps": 160, "audioFormat": "opus",
      "tags": [{"name": "sad", "colorHex": "#2563EB"}] }
  ]
}
```
