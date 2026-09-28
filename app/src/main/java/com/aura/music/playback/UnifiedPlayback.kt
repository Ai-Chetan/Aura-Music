package com.aura.music.playback

import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.TransientTrackFactory
import com.aura.music.data.stream.UpNextManager
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.UnifiedTrack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts mixed offline/streaming queues for the unified Library and
 * playlists. The tapped track plays immediately (downloads straight from
 * their local file, saved bookmarks via a resolved stream transient), the
 * rest of the list hands to an up-next session that continues in order —
 * downloads stay free, streams resolve lazily, radio takes over at the end.
 */
@Singleton
class UnifiedPlayback @Inject constructor(
    private val controller: PlaybackController,
    private val transients: TransientTrackFactory,
    private val upNext: UpNextManager,
    private val gate: NetworkGate,
    private val savedTrackRepository: SavedTrackRepository
) {
    /** True when playback can start without passing the network gate. */
    fun startsOffline(item: UnifiedTrack): Boolean = item.isOffline

    /**
     * Plays [list][index] with the whole list as the up-next session.
     * Must be called from a coroutine; throws on resolve failure — callers
     * translate that into their toast message.
     */
    suspend fun start(list: List<UnifiedTrack>, index: Int) {
        val item = list.getOrNull(index) ?: return
        val first: com.aura.music.data.db.SongEntity = if (item.isOffline) {
            item.song ?: return
        } else {
            transients.fromSaved(item.saved ?: return)
        }
        // Vault-owned queue first (clears any streaming session), then the
        // session fills the tail lazily — mirrors the Saved-tab flow.
        upNext.clear()
        controller.playQueue(listOf(first), 0)
        upNext.startPlaylistSession(list, index)
        item.saved?.let { saved ->
            try {
                savedTrackRepository.recordPlay(saved.url)
            } catch (_: Exception) {
            }
        }
    }

    /** Network gate for a queue whose head is a stream (downloads skip it). */
    fun gate(): NetworkGate = gate

    /** Builds a stream transient for a single bookmark (queue/play-next). */
    suspend fun transientFor(item: UnifiedTrack): com.aura.music.data.db.SongEntity? =
        item.song ?: item.saved?.let { transients.fromSaved(it) }
}
