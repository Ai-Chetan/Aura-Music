package com.aura.music.data.repository

import com.aura.music.data.db.PlaylistDao
import com.aura.music.data.db.PlaylistEntity
import com.aura.music.data.db.PlaylistSavedCrossRef
import com.aura.music.data.db.PlaylistSongCrossRef
import com.aura.music.data.db.PlaylistWithCount
import com.aura.music.data.db.SavedTrackDao
import com.aura.music.data.db.SongDao
import com.aura.music.domain.repository.PlaylistExportEntry
import com.aura.music.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val savedTrackDao: SavedTrackDao
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<PlaylistWithCount>> =
        playlistDao.observeAllWithCounts()

    override fun observePlaylist(id: Long): Flow<PlaylistEntity?> =
        playlistDao.observeById(id)

    override fun observeSongIdsIn(playlistId: Long): Flow<Set<Long>> =
        playlistDao.observeSongIds(playlistId).map { it.toSet() }

    override fun observeSavedIdsIn(playlistId: Long): Flow<Set<Long>> =
        playlistDao.observeSavedIds(playlistId).map { it.toSet() }

    override suspend fun getOrCreate(name: String): Long {
        val normalized = name.trim()
        if (normalized.isEmpty()) return -1L
        playlistDao.getByName(normalized)?.let { return it.id }
        val inserted = playlistDao.insertPlaylist(PlaylistEntity(name = normalized))
        return if (inserted == -1L) {
            playlistDao.getByName(normalized)?.id ?: -1L
        } else {
            inserted
        }
    }

    override suspend fun rename(playlistId: Long, name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        playlistDao.rename(playlistId, normalized)
    }

    override suspend fun delete(playlistId: Long) {
        // Cross-ref rows cascade away with the playlist row.
        playlistDao.deleteById(playlistId)
    }

    override suspend fun addSong(playlistId: Long, songId: Long) {
        playlistDao.addSong(PlaylistSongCrossRef(playlistId, songId))
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) {
        playlistDao.removeSong(playlistId, songId)
    }

    override suspend fun addSaved(playlistId: Long, savedTrackId: Long) {
        playlistDao.addSaved(PlaylistSavedCrossRef(playlistId, savedTrackId))
    }

    override suspend fun removeSaved(playlistId: Long, savedTrackId: Long) {
        playlistDao.removeSaved(playlistId, savedTrackId)
    }

    override suspend fun playlistIdsForSong(songId: Long): Set<Long> =
        playlistDao.playlistIdsForSong(songId).toSet()

    override suspend fun playlistIdsForSaved(savedTrackId: Long): Set<Long> =
        playlistDao.playlistIdsForSaved(savedTrackId).toSet()

    override suspend fun snapshotForExport(): List<PlaylistExportEntry> {
        // One-shot read; playlists are few so per-playlist URL queries are fine.
        return playlistDao.observeAllWithCounts().first().map { playlist ->
            PlaylistExportEntry(
                name = playlist.name,
                songUrls = playlistDao.songUrlsIn(playlist.id),
                savedUrls = playlistDao.savedUrlsIn(playlist.id)
            )
        }
    }

    override suspend fun restoreFromExport(entries: List<PlaylistExportEntry>) {
        entries.forEach { entry ->
            val playlistId = getOrCreate(entry.name)
            if (playlistId <= 0) return@forEach
            entry.songUrls.forEach { url ->
                val canonical = com.aura.music.util.YoutubeUrls.canonicalUrl(url)
                songDao.getBySourceUrl(canonical)?.let { song ->
                    playlistDao.addSong(PlaylistSongCrossRef(playlistId, song.id))
                }
            }
            entry.savedUrls.forEach { url ->
                val canonical = com.aura.music.util.YoutubeUrls.canonicalUrl(url)
                savedTrackDao.getByUrl(canonical)?.let { saved ->
                    playlistDao.addSaved(PlaylistSavedCrossRef(playlistId, saved.id))
                }
            }
        }
    }
}
