# Data Model & Internal API Contracts

## 1. Room entities

### Song

```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String?,
    val sourceUrl: String,            // canonical YouTube watch URL
    val sourcePlatform: String,       // "youtube"
    val localFilePath: String,        // absolute path in app-specific storage
    val thumbnailPath: String?,       // cached local thumbnail path
    val durationMs: Long,
    val bitrateKbps: Int?,
    val audioFormat: String,          // "opus" | "aac" | "unknown"
    val isLossless: Boolean = false,  // reserved; YouTube sources are never lossless
    val dateAdded: Long,              // epoch millis
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null
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

### Playlist

```kotlin
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSmart: Boolean = false,      // true = defined by a tag filter, not manual song list
    val smartTagQuery: String? = null  // e.g. serialized "sad AND english"
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int   // manual ordering within playlist
)
```

### QueueState (persisted so the queue survives app restart)

```kotlin
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,   // singleton row
    val songIdsJson: String,       // ordered list of song IDs, JSON-encoded
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: String         // "off" | "one" | "all"
)
```

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

    // AND-filter: song must carry every requested tag
    @Transaction
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_tag_cross_ref ON songs.id = song_tag_cross_ref.songId
        INNER JOIN tags ON tags.id = song_tag_cross_ref.tagId
        WHERE tags.name IN (:tagNames)
        GROUP BY songs.id
        HAVING COUNT(DISTINCT tags.name) = :requiredMatchCount
    """)
    fun getSongsMatchingAllTags(tagNames: List<String>, requiredMatchCount: Int): Flow<List<SongEntity>>

    // OR-filter: song carries any requested tag
    @Query("""
        SELECT DISTINCT songs.* FROM songs
        INNER JOIN song_tag_cross_ref ON songs.id = song_tag_cross_ref.songId
        INNER JOIN tags ON tags.id = song_tag_cross_ref.tagId
        WHERE tags.name IN (:tagNames)
    """)
    fun getSongsMatchingAnyTag(tagNames: List<String>): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert
    suspend fun insertSong(song: SongEntity): Long

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, now: Long)

    @Delete
    suspend fun deleteSong(song: SongEntity)
}
```

## 4. Repository interfaces (domain layer contracts)

```kotlin
interface SongRepository {
    fun observeAllSongs(): Flow<List<SongWithTags>>
    fun observeSongsByTags(tagNames: List<String>, matchAll: Boolean): Flow<List<SongEntity>>
    fun searchSongs(query: String): Flow<List<SongEntity>>
    suspend fun addSongFromUrl(url: String): Result<Long>       // blocking enqueue + await
    suspend fun enqueueDownload(url: String): UUID              // non-blocking, observed via WorkInfo
    fun observeDownloadWork(workId: UUID): Flow<WorkInfo?>
    suspend fun previewFromUrl(url: String): Result<ExtractedStreamInfo>
    suspend fun deleteSong(songId: Long)                        // also removes audio + artwork files
    suspend fun recordPlay(songId: Long)
    suspend fun exportLibraryJson(excludeTagNames: Set<String>, onlySongIds: Set<Long>?): Result<String>
    suspend fun describeImport(json: String): Result<ImportPreview>
    suspend fun importLibraryJson(json: String, onProgress: ...): Result<ImportSummary>
    suspend fun describePlaylist(url: String): Result<PlaylistPreview>
    suspend fun importPlaylist(url: String, playlistTag: String?, onProgress: ...): Result<PlaylistImportSummary>
}

interface TagRepository {
    fun observeAllTags(): Flow<List<TagEntity>>
    suspend fun createTag(name: String, colorHex: String?): Long
    suspend fun addTagToSong(songId: Long, tagId: Long)
    suspend fun removeTagFromSong(songId: Long, tagId: Long)
}

interface ExtractionRepository {
    suspend fun resolveStreamInfo(url: String): ExtractedStreamInfo
    suspend fun resolvePlaylist(url: String): ExtractedPlaylist
    suspend fun downloadAudio(streamInfo: ExtractedStreamInfo, destination: File): DownloadResult
}

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
