package com.aura.music.domain.repository

import androidx.work.WorkInfo
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.SongWithTags
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface SongRepository {
    fun observeAllSongs(): Flow<List<SongWithTags>>

    fun observeSongsByTags(
        tagNames: List<String>,
        matchAll: Boolean
    ): Flow<List<SongEntity>>

    fun searchSongs(query: String): Flow<List<SongEntity>>

    suspend fun getSongById(songId: Long): SongEntity?

    suspend fun getSongsByIds(songIds: List<Long>): List<SongEntity>

    suspend fun addSongFromUrl(url: String): Result<Long>

    /**
     * Non-blocking enqueue for the progress UI: returns the
     * WorkManager id immediately; callers observe [observeDownloadWork].
     */
    suspend fun enqueueDownload(url: String): UUID

    /** Live WorkInfo for a download enqueued via [enqueueDownload]. */
    fun observeDownloadWork(workId: UUID): Flow<WorkInfo?>

    /** Resolve title/thumbnail/quality without downloading (preview card). */
    suspend fun previewFromUrl(url: String): Result<ExtractedStreamInfo>

    suspend fun deleteSong(songId: Long)

    suspend fun recordPlay(songId: Long)

    /**
     * Full or selective library JSON backup. Songs carrying ANY tag in
     * [excludeTagNames] are left out — unless [onlySongIds] is given, in which
     * case exactly those songs are exported (explicit selection wins).
     * Every entry keeps its source URL so it can be re-downloaded later.
     */
    suspend fun exportLibraryJson(
        excludeTagNames: Set<String>,
        onlySongIds: Set<Long>? = null
    ): Result<String>

    /** Validates an import file + counts what is new vs already present. */
    suspend fun describeImport(json: String): Result<ImportPreview>

    /**
     * Re-downloads every entry from its source URL and restores its tags.
     * Duplicates (same link already in library) are skipped for download but
     * still get any missing tags merged. Progress is reported per song.
     */
    suspend fun importLibraryJson(
        json: String,
        onProgress: (done: Int, total: Int, currentTitle: String) -> Unit
    ): Result<ImportSummary>

    /** Validates a playlist link + counts what is new vs already present. */
    suspend fun describePlaylist(url: String): Result<PlaylistPreview>

    /**
     * Downloads every video in the playlist in best quality. Already-present
     * links are skipped; when [playlistTag] is given it is applied to every
     * imported song. Private/deleted videos fail individually without
     * aborting the batch.
     */
    suspend fun importPlaylist(
        url: String,
        playlistTag: String?,
        onProgress: (done: Int, total: Int, currentTitle: String) -> Unit
    ): Result<PlaylistImportSummary>

    fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>
}

data class ImportPreview(
    val total: Int,
    /** Entries whose link is already in the library (tags will be merged). */
    val duplicates: Int,
    /** Entries without a usable link. */
    val invalid: Int,
    val invalidReasons: List<String>,
    /** Entries that will actually be downloaded. */
    val importable: Int
)

data class ImportSummary(
    val total: Int,
    val imported: Int,
    val duplicatesMerged: Int,
    val failed: Int,
    val errors: List<String>
)

data class PlaylistPreview(
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val totalCount: Long,
    val videoUrls: List<String>,
    val isCapped: Boolean,
    /** URLs already in the library (will be skipped, not re-downloaded). */
    val duplicates: Int
) {
    val importable: Int get() = videoUrls.size - duplicates
}

data class PlaylistImportSummary(
    val playlistTitle: String,
    val total: Int,
    val imported: Int,
    val skippedDuplicate: Int,
    val failed: Int,
    val errors: List<String>
)