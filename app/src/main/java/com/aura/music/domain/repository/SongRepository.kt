package com.aura.music.domain.repository

import androidx.work.WorkInfo
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.SongWithTags
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface SongRepository {
    fun observeAllSongs(): Flow<List<SongWithTags>>

    suspend fun getSongById(songId: Long): SongEntity?

    suspend fun getSongsByIds(songIds: List<Long>): List<SongEntity>

    suspend fun addSongFromUrl(url: String): Result<Long>

    /**
     * Non-blocking enqueue for the progress UI: returns the
     * WorkManager id immediately; callers observe [observeDownloadWork].
     * [initialDelaySeconds] staggers batch enqueues so dozens of simultaneous
     * YouTube resolutions don't trip throttling (403/unavailable).
     */
    suspend fun enqueueDownload(url: String, initialDelaySeconds: Long = 0): UUID

    /** Live WorkInfo for a download enqueued via [enqueueDownload]. */
    fun observeDownloadWork(workId: UUID): Flow<WorkInfo?>

    /**
     * Live list of in-progress downloads (enqueued/running/blocked), newest
     * first. Powers the "Downloading" section in Library so the user can see
     * what is being fetched and how far along each item is.
     */
    fun observeActiveDownloads(): Flow<List<ActiveDownload>>

    /** Cancels a queued/running download. */
    suspend fun cancelDownload(workId: UUID)

    /**
     * Remembers a batch of starter-track downloads (Getting Started → select
     * → Download) so Library can report per-song progress, print each
     * failure's reason, and offer a retry. Replaces any previous batch.
     */
    fun trackStarterBatch(items: List<BatchItem>)

    /** Live starter-batch status, or null when there is no batch to report. */
    fun observeStarterBatch(): Flow<StarterBatchStatus?>

    /** Re-enqueues every batch item that did not succeed. */
    suspend fun retryStarterBatch()

    /** Dismisses the starter-batch report card. */
    fun clearStarterBatch()

    /** Resolve title/thumbnail/quality without downloading (preview card). */
    suspend fun previewFromUrl(url: String): Result<ExtractedStreamInfo>

    suspend fun deleteSong(songId: Long)

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
     * Enqueues a background worker that imports the whole playlist.
     * Because the work lives in WorkManager (not a screen's ViewModel),
     * leaving the Download screen can't abort it halfway — every video
     * saves independently. Observe with [observePlaylistImports].
     */
    suspend fun enqueuePlaylistImport(url: String, playlistTag: String?): UUID

    /** Live playlist imports (unfinished only), for progress cards. */
    fun observePlaylistImports(): Flow<List<PlaylistImportState>>

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

/** One in-flight playlist import, mapped from WorkManager state. */
data class PlaylistImportState(
    val workId: UUID,
    val playlistTitle: String,
    val done: Int,
    val total: Int,
    val current: String
) {
    val percent: Int get() = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
}

/** One in-flight audio download, mapped from WorkManager state. */
data class ActiveDownload(
    val workId: UUID,
    val url: String,
    /** Resolved video title once extraction finished; null while resolving. */
    val title: String?,
    val stage: String,
    val percent: Int,
    val bytesDone: Long?,
    val bytesTotal: Long?
)

/** One starter-batch entry. Exactly one of [workId]/[enqueueError] is set. */
data class BatchItem(
    val label: String,
    val url: String,
    val workId: UUID?,
    val enqueueError: String?
)

/**
 * Seconds between starter-batch download starts. Just enough to avoid a
 * thundering herd on enqueue — throttling itself is handled by the YtGate
 * concurrency cap plus per-download backoff retries, so this stays small.
 * First track always starts immediately (index 0 = no delay).
 */
const val STARTER_STAGGER_SECONDS = 2L

/** A starter-batch song that did not make it, with the reason printed. */
data class FailedDownload(
    val label: String,
    val url: String,
    val error: String
)

/** Aggregated starter-batch report for the Library card. */
data class StarterBatchStatus(
    val total: Int,
    val succeeded: Int,
    val failed: List<FailedDownload>,
    val finished: Boolean
) {
    val done: Int get() = succeeded + failed.size
}