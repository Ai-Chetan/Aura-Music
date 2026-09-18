package com.aura.music.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aura.music.data.backup.LibraryBackup
import com.aura.music.data.db.SongDao
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.SongWithTags
import com.aura.music.data.download.DownloadAudioWorker
import com.aura.music.data.download.PlaylistImportWorker
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.BatchItem
import com.aura.music.domain.repository.ExtractedStreamInfo
import com.aura.music.domain.repository.FailedDownload
import com.aura.music.domain.repository.PlaylistImportState
import com.aura.music.domain.repository.STARTER_STAGGER_SECONDS
import com.aura.music.domain.repository.StarterBatchStatus
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.ImportPreview
import com.aura.music.domain.repository.ImportSummary
import com.aura.music.domain.repository.PlaylistPreview
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
import com.aura.music.util.YoutubeUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val extractionRepository: ExtractionRepository,
    private val tagRepository: TagRepository,
    @ApplicationContext private val context: Context
) : SongRepository {

    override fun observeAllSongs(): Flow<List<SongWithTags>> =
        songDao.getAllSongsWithTags()

    override fun observeSongsByTags(
        tagNames: List<String>,
        matchAll: Boolean
    ): Flow<List<SongEntity>> {
        return if (matchAll) {
            songDao.getSongsMatchingAllTags(tagNames, tagNames.size)
        } else {
            songDao.getSongsMatchingAnyTag(tagNames)
        }
    }

    override fun searchSongs(query: String): Flow<List<SongEntity>> =
        songDao.searchSongs(query)

    override suspend fun getSongById(songId: Long): SongEntity? =
        songDao.getSongById(songId)

    override suspend fun getSongsByIds(songIds: List<Long>): List<SongEntity> =
        songDao.getSongsByIds(songIds)

    override suspend fun addSongFromUrl(url: String): Result<Long> {
        return try {
            val canonical = YoutubeUrls.canonicalUrl(url)
            if (!YoutubeUrls.isYouTubeUrl(canonical)) {
                return Result.failure(Exception(YoutubeUrls.rejectionReason(url)))
            }
            val workId = enqueueDownload(canonical)
            val workManager = WorkManager.getInstance(context)

            // Await the terminal WorkManager state before reporting success/failure.
            val finished = workManager.getWorkInfoByIdFlow(workId)
                .first { it?.state?.isFinished == true }
                ?: return Result.failure(Exception("Download was cancelled"))

            if (finished.state == WorkInfo.State.SUCCEEDED) {
                val songId = finished.outputData.getLong(DownloadAudioWorker.KEY_SONG_ID, -1L)
                if (songId > 0) {
                    Result.success(songId)
                } else {
                    Result.failure(Exception("Download completed but no song ID returned"))
                }
            } else if (finished.state == WorkInfo.State.FAILED) {
                val error = finished.outputData.getString(DownloadAudioWorker.KEY_ERROR)
                    ?: "Download failed"
                Result.failure(Exception(error))
            } else {
                Result.failure(Exception("Download was cancelled"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enqueueDownload(url: String, initialDelaySeconds: Long): UUID {
        val canonical = YoutubeUrls.canonicalUrl(url)
        require(YoutubeUrls.isYouTubeUrl(canonical)) { YoutubeUrls.rejectionReason(url) }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(DownloadAudioWorker.KEY_URL, canonical)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadAudioWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            // Transient YouTube errors (403/429) auto-retry in the worker;
            // back off exponentially so retries don't hammer the server.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply {
                // Staggered batch starts: firing dozens of resolutions at once
                // gets throttled (403 / "video unavailable" on valid links).
                if (initialDelaySeconds > 0) {
                    setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                }
            }
            .addTag(DownloadAudioWorker.DOWNLOAD_TAG)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }

    override fun observeDownloadWork(workId: UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(workId)

    override fun observeActiveDownloads(): Flow<List<ActiveDownload>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(DownloadAudioWorker.DOWNLOAD_TAG)
            .map { infos ->
                infos
                    .filter { !it.state.isFinished }
                    .map { info ->
                        val progress = info.progress
                        ActiveDownload(
                            workId = info.id,
                            url = progress.getString(DownloadAudioWorker.KEY_URL).orEmpty(),
                            title = progress.getString(DownloadAudioWorker.KEY_TITLE),
                            stage = progress.getString(DownloadAudioWorker.KEY_PROGRESS)
                                ?: DownloadAudioWorker.PROGRESS_RESOLVING,
                            percent = progress.getInt(DownloadAudioWorker.KEY_PERCENT, 0),
                            bytesDone = progress.getLong(DownloadAudioWorker.KEY_BYTES_DONE, -1L)
                                .takeIf { it >= 0 },
                            bytesTotal = progress.getLong(DownloadAudioWorker.KEY_BYTES_TOTAL, -1L)
                                .takeIf { it > 0 }
                        )
                    }
            }

    override suspend fun cancelDownload(workId: UUID) {
        WorkManager.getInstance(context).cancelWorkById(workId)
    }

    private val starterBatch = MutableStateFlow<List<BatchItem>?>(null)

    override fun trackStarterBatch(items: List<BatchItem>) {
        starterBatch.value = items.ifEmpty { null }
    }

    override fun clearStarterBatch() {
        starterBatch.value = null
    }

    override fun observeStarterBatch(): Flow<StarterBatchStatus?> =
        starterBatch.flatMapLatest { items ->
            if (items.isNullOrEmpty()) {
                flowOf(null)
            } else {
                // One shared tag-flow for the whole batch (not one flow per
                // id — a 40-track batch would otherwise fan out to 40
                // listeners re-firing on every progress tick).
                WorkManager.getInstance(context)
                    .getWorkInfosByTagFlow(DownloadAudioWorker.DOWNLOAD_TAG)
                    .map { infos -> buildBatchStatus(items, infos.associateBy { it.id }) }
            }
        }

    private fun buildBatchStatus(
        items: List<BatchItem>,
        byId: Map<UUID, WorkInfo?>
    ): StarterBatchStatus {
        var succeeded = 0
        val failed = mutableListOf<FailedDownload>()
        items.forEach { item ->
            val id = item.workId
            if (id == null) {
                failed += FailedDownload(
                    label = item.label,
                    url = item.url,
                    error = item.enqueueError ?: "Couldn't start download."
                )
                return@forEach
            }
            val info = byId[id]
            when {
                info == null || !info.state.isFinished -> Unit // still running
                info.state == WorkInfo.State.SUCCEEDED -> succeeded++
                info.state == WorkInfo.State.CANCELLED ->
                    failed += FailedDownload(item.label, item.url, "Download cancelled.")
                else ->
                    failed += FailedDownload(
                        label = item.label,
                        url = item.url,
                        error = info.outputData.getString(DownloadAudioWorker.KEY_ERROR)
                            ?: "Download failed."
                    )
            }
        }
        return StarterBatchStatus(
            total = items.size,
            succeeded = succeeded,
            failed = failed,
            finished = succeeded + failed.size >= items.size
        )
    }

    override suspend fun retryStarterBatch() = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(context)
        val current = starterBatch.value ?: return@withContext
        // Stagger retries too, or the whole failed set gets throttled again.
        var retryPosition = 0
        starterBatch.value = current.map { item ->
            val id = item.workId
            val succeeded = try {
                id != null &&
                    workManager.getWorkInfoById(id).get()?.state == WorkInfo.State.SUCCEEDED
            } catch (_: Exception) {
                false
            }
            if (succeeded) {
                item
            } else {
                val delaySeconds = (retryPosition++ * STARTER_STAGGER_SECONDS)
                try {
                    item.copy(
                        workId = enqueueDownload(item.url, delaySeconds),
                        enqueueError = null
                    )
                } catch (e: Exception) {
                    item.copy(workId = null, enqueueError = e.message ?: "Couldn't start download.")
                }
            }
        }
    }

    override suspend fun previewFromUrl(url: String): Result<ExtractedStreamInfo> {
        return try {
            val canonical = YoutubeUrls.canonicalUrl(url)
            if (!YoutubeUrls.isYouTubeUrl(canonical)) {
                return Result.failure(Exception(YoutubeUrls.rejectionReason(url)))
            }
            // Surface duplicates instantly before starting a new download.
            songDao.getBySourceUrl(canonical)?.let {
                return Result.failure(
                    Exception("Already in your library as \"${it.title}\".")
                )
            }
            Result.success(extractionRepository.resolveStreamInfo(canonical))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun describePlaylist(url: String): Result<PlaylistPreview> {
        return try {
            val canonical = YoutubeUrls.canonicalPlaylistUrl(url)
            if (!YoutubeUrls.isPlaylistUrl(canonical)) {
                return Result.failure(Exception(YoutubeUrls.rejectionReason(url)))
            }
            val playlist = extractionRepository.resolvePlaylist(canonical)
            var duplicates = 0
            playlist.videoUrls.forEach { videoUrl ->
                if (songDao.getBySourceUrl(videoUrl) != null) duplicates++
            }
            Result.success(
                PlaylistPreview(
                    title = playlist.title,
                    author = playlist.author,
                    thumbnailUrl = playlist.thumbnailUrl,
                    totalCount = playlist.totalCount,
                    videoUrls = playlist.videoUrls,
                    isCapped = playlist.isCapped,
                    duplicates = duplicates
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enqueuePlaylistImport(url: String, playlistTag: String?): UUID {
        val canonical = YoutubeUrls.canonicalPlaylistUrl(url)
        require(YoutubeUrls.isPlaylistUrl(canonical)) { YoutubeUrls.rejectionReason(url) }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(PlaylistImportWorker.KEY_URL, canonical)
            .apply {
                playlistTag?.trim()?.takeIf { it.isNotEmpty() }?.let { tag ->
                    putString(PlaylistImportWorker.KEY_TAG, tag)
                }
            }
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PlaylistImportWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(PlaylistImportWorker.PLAYLIST_TAG)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }

    override fun observePlaylistImports(): Flow<List<PlaylistImportState>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(PlaylistImportWorker.PLAYLIST_TAG)
            .map { infos ->
                infos
                    .filter { !it.state.isFinished }
                    .map { info ->
                        val progress = info.progress
                        PlaylistImportState(
                            workId = info.id,
                            playlistTitle = progress.getString(PlaylistImportWorker.P_TITLE).orEmpty(),
                            done = progress.getInt(PlaylistImportWorker.P_DONE, 0),
                            total = progress.getInt(PlaylistImportWorker.P_TOTAL, 0),
                            current = progress.getString(PlaylistImportWorker.P_CURRENT).orEmpty()
                        )
                    }
            }

    override suspend fun deleteSong(songId: Long) {
        val song = songDao.getSongById(songId)
        songDao.deleteSongById(songId)
        // Delete orphaned audio + artwork files from disk.
        song?.let {
            try { File(it.localFilePath).takeIf { f -> f.exists() }?.delete() } catch (_: Exception) { }
            try { it.thumbnailPath?.let { p -> File(p).takeIf { f -> f.exists() }?.delete() } } catch (_: Exception) { }
        }
    }

    override suspend fun recordPlay(songId: Long) {
        songDao.incrementPlayCount(songId, System.currentTimeMillis())
    }

    override suspend fun exportLibraryJson(
        excludeTagNames: Set<String>,
        onlySongIds: Set<Long>?
    ): Result<String> {
        return try {
            val all = songDao.getAllSongsWithTags().first()
            val kept = if (onlySongIds != null) {
                // Selective export: exactly what the user picked.
                all.filter { it.song.id in onlySongIds }
            } else {
                val normalized = excludeTagNames.map { it.trim().lowercase() }.toSet()
                if (normalized.isEmpty()) all
                else all.filter { item ->
                    item.tags.none { tag -> tag.name.lowercase() in normalized }
                }
            }
            Result.success(LibraryBackup.exportJson(kept))
        } catch (e: Exception) {
            Result.failure(Exception("Export failed: ${e.message}"))
        }
    }

    override suspend fun describeImport(json: String): Result<ImportPreview> {
        val parsed = LibraryBackup.parseImport(json).getOrElse { return Result.failure(it) }
        var duplicates = 0
        parsed.entries.forEach { entry ->
            val canonical = YoutubeUrls.canonicalUrl(entry.sourceUrl)
            if (songDao.getBySourceUrl(canonical) != null) duplicates++
        }
        return Result.success(
            ImportPreview(
                total = parsed.entries.size,
                duplicates = duplicates,
                invalid = parsed.invalid.size,
                invalidReasons = parsed.invalid,
                importable = parsed.entries.size - duplicates
            )
        )
    }

    override suspend fun importLibraryJson(
        json: String,
        onProgress: (done: Int, total: Int, currentTitle: String) -> Unit
    ): Result<ImportSummary> {        val parsed = LibraryBackup.parseImport(json).getOrElse { return Result.failure(it) }
        val entries = parsed.entries
        var imported = 0
        var duplicatesMerged = 0
        var failed = 0
        val errors = mutableListOf<String>()
        errors.addAll(parsed.invalid)

        entries.forEachIndexed { index, entry ->
            onProgress(index, entries.size, entry.title)
            val canonical = YoutubeUrls.canonicalUrl(entry.sourceUrl)
            if (!YoutubeUrls.isYouTubeUrl(canonical)) {
                failed++
                errors.add("\"${entry.title}\": only YouTube links can be re-downloaded — skipped.")
                return@forEachIndexed
            }
            try {
                val existing = songDao.getBySourceUrl(canonical)
                val songId = if (existing != null) {
                    duplicatesMerged++
                    existing.id
                } else {
                    val result = addSongFromUrl(canonical)
                    result.getOrElse { throw it }
                        .also { imported++ }
                }
                // Restore tags (merge — never removes what is already there).
                entry.tags.forEach { tag ->
                    try {
                        val tagId = tagRepository.getOrCreateTag(tag.name, tag.colorHex)
                        if (tagId > 0) tagRepository.addTagToSong(songId, tagId)
                    } catch (_: Exception) {
                        // One bad tag must not fail the whole song.
                    }
                }
            } catch (e: Exception) {
                failed++
                errors.add("\"${entry.title}\": ${e.message ?: "download failed"}")
            }
        }
        onProgress(entries.size, entries.size, "")

        return Result.success(
            ImportSummary(
                total = entries.size,
                imported = imported,
                duplicatesMerged = duplicatesMerged,
                failed = failed,
                errors = errors
            )
        )
    }

    override fun observeMostPlayed(limit: Int): Flow<List<SongEntity>> =
        songDao.getMostPlayed(limit)

    override fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>> =
        songDao.getRecentlyPlayed(limit)
}