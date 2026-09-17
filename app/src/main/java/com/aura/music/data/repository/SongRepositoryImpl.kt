package com.aura.music.data.repository

import android.content.Context
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
import com.aura.music.domain.repository.ExtractedStreamInfo
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.ImportPreview
import com.aura.music.domain.repository.ImportSummary
import com.aura.music.domain.repository.PlaylistImportSummary
import com.aura.music.domain.repository.PlaylistPreview
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
import com.aura.music.util.YoutubeUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    override suspend fun enqueueDownload(url: String): UUID {
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
            .addTag("aura-download")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
        return workRequest.id
    }

    override fun observeDownloadWork(workId: UUID): Flow<WorkInfo?> =
        WorkManager.getInstance(context).getWorkInfoByIdFlow(workId)

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

    override suspend fun importPlaylist(
        url: String,
        playlistTag: String?,
        onProgress: (done: Int, total: Int, currentTitle: String) -> Unit
    ): Result<PlaylistImportSummary> {
        return try {
            val canonical = YoutubeUrls.canonicalPlaylistUrl(url)
            if (!YoutubeUrls.isPlaylistUrl(canonical)) {
                return Result.failure(Exception(YoutubeUrls.rejectionReason(url)))
            }
            val playlist = extractionRepository.resolvePlaylist(canonical)
            val urls = playlist.videoUrls

            val tagId = playlistTag?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                try {
                    tagRepository.getOrCreateTag(name, null).takeIf { it > 0 }
                } catch (_: Exception) {
                    null
                }
            }

            var imported = 0
            var skippedDuplicate = 0
            var failed = 0
            val errors = mutableListOf<String>()

            urls.forEachIndexed { index, videoUrl ->
                onProgress(index, urls.size, "Song ${index + 1} of ${urls.size}")
                try {
                    val existing = songDao.getBySourceUrl(videoUrl)
                    val songId = if (existing != null) {
                        skippedDuplicate++
                        if (tagId != null) {
                            try { tagRepository.addTagToSong(existing.id, tagId) } catch (_: Exception) { }
                        }
                        existing.id
                    } else {
                        val songId = addSongFromUrl(videoUrl).getOrElse { throw it }
                        imported++
                        if (tagId != null) {
                            try { tagRepository.addTagToSong(songId, tagId) } catch (_: Exception) { }
                        }
                        songId
                    }
                    songId
                } catch (e: Exception) {
                    // Private/deleted/region-locked videos fail individually.
                    failed++
                    if (errors.size < 8) {
                        errors.add("Song ${index + 1}: ${e.message ?: "download failed"}")
                    }
                }
            }
            onProgress(urls.size, urls.size, "")

            Result.success(
                PlaylistImportSummary(
                    playlistTitle = playlist.title,
                    total = urls.size,
                    imported = imported,
                    skippedDuplicate = skippedDuplicate,
                    failed = failed,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
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