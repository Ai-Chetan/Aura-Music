package com.aura.music.data.stream

import com.aura.music.data.network.NetworkGate
import com.aura.music.data.prefs.PlaybackPreferences
import com.aura.music.data.recommendations.RecommendationsRepository
import com.aura.music.domain.repository.ExtractionRepository
import com.aura.music.domain.repository.YouTubeTrack
import com.aura.music.playback.PlaybackController
import com.aura.music.playback.PlaybackUiState
import com.aura.music.playback.RepeatMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "what plays next?" for every queue in the app.
 *
 * - Tapping a result (Home / Search / Saved) starts a session over that
 *   list: the tapped track plays instantly while the next few resolve in
 *   the background and append to the live queue.
 * - When a session list runs out, radio continues from related tracks.
 * - A vault queue (downloads/saved, no session) that runs dry continues
 *   with random recommended picks instead of stopping — repeat modes still
 *   loop natively, so radio only engages on repeat OFF.
 * - Spice-up (optional): keeps at least [SPICE_AHEAD] songs queued behind
 *   anything playing by injecting random recommendations.
 */
@Singleton
class UpNextManager @Inject constructor(
    private val controller: PlaybackController,
    private val transients: TransientTrackFactory,
    private val streamResolver: StreamResolver,
    private val extraction: ExtractionRepository,
    private val recommendations: RecommendationsRepository,
    private val prefs: PlaybackPreferences,
    private val gate: NetworkGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val spiceUp: StateFlow<Boolean> = prefs.spiceUp
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun toggleSpiceUp() {
        scope.launch { prefs.setSpiceUp(!spiceUp.value) }
    }

    private var origin: List<YouTubeTrack> = emptyList()
    private var generation = 0
    private var fillJob: Job? = null
    private var radioSeedNotified: String? = null
    private var radioAnnounced = false
    private var radioExhausted = false
    private val radioPlayed = LinkedHashSet<String>()
    @Volatile private var fillBlockedUntil = 0L

    init {
        scope.launch {
            controller.playbackState.collect { state -> maybeExtend(state) }
        }
    }

    /** Begins (or replaces) a streaming session at [index] in [tracks]. */
    fun startSession(tracks: List<YouTubeTrack>, index: Int) {
        generation++
        fillJob?.cancel()
        resetRadioMemory()
        if (tracks.isEmpty()) {
            origin = emptyList()
            return
        }
        origin = tracks
        radioSeedNotified = null
        val g = generation
        val safeIndex = index.coerceIn(0, tracks.size - 1)
        fillJob = scope.launch(Dispatchers.IO) {
            var appended = 0
            for (i in safeIndex + 1 until tracks.size) {
                if (appended >= PREFILL || g != generation) break
                try {
                    val song = transients.fromTrack(tracks[i])
                    withContext(Dispatchers.Main) {
                        if (g == generation) controller.addToQueueEnd(song)
                    }
                    appended++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // One unresolvable video must not stop the queue.
                }
            }
            // Warm whatever follows so later taps/skips stay instant.
            if (g == generation) {
                streamResolver.prefetch(
                    tracks.drop(safeIndex + 1 + appended).take(6).map { it.url }
                )
            }
        }
    }

    /** Ends the session (e.g. user started an offline vault queue). */
    fun clear() {
        generation++
        fillJob?.cancel()
        origin = emptyList()
        radioSeedNotified = null
        resetRadioMemory()
    }

    private fun resetRadioMemory() {
        radioPlayed.clear()
        radioAnnounced = false
        radioExhausted = false
        fillBlockedUntil = 0L
    }

    /**
     * Fires on every playback-state emission; all guards are cheap until
     * action is actually needed. [fillBlockedUntil] stops the 2/sec state
     * ticks from re-launching a fill that just concluded there is nothing
     * to append (radio exhausted / session tail blocked) — without it an
     * empty fill coroutine spawns every 500ms until the track changes.
     */
    private fun maybeExtend(state: PlaybackUiState) {
        if (fillJob?.isActive == true) return
        if (System.currentTimeMillis() < fillBlockedUntil) return
        if (!gate.isAllowedNow()) return
        val queue = state.queue
        if (queue.isEmpty()) return
        val index = state.currentIndexInQueue
        if (index !in queue.indices) return
        val upcoming = queue.size - 1 - index
        val g = generation
        when {
            // Active streaming session sitting on its last streaming item.
            origin.isNotEmpty() && upcoming <= 0 &&
                queue.lastOrNull()?.id?.let { it < 0 } == true ->
                fillJob = scope.launch(Dispatchers.IO) { extendSession(queue, g) }
            // Vault (or any session-less) queue: tail radio on repeat OFF,
            // or proactively with Spice-up on.
            origin.isEmpty() && !radioExhausted && state.repeatMode == RepeatMode.OFF &&
                (upcoming <= 0 || (spiceUp.value && upcoming < SPICE_AHEAD)) ->
                fillJob = scope.launch(Dispatchers.IO) {
                    appendRandom(queue, g, announce = upcoming <= 0)
                }
        }
    }

    /** Blocks re-launch attempts until [ms] from now (called on Main). */
    private fun blockFillsFor(ms: Long) {
        fillBlockedUntil = System.currentTimeMillis() + ms
    }

    /** Continues an active session from its origin list, then radio. */
    private suspend fun extendSession(
        queue: List<com.aura.music.data.db.SongEntity>,
        g: Int
    ) {
        try {
            val currentUrl = withContext(Dispatchers.Main) {
                controller.playbackState.value.currentSong?.sourceUrl
            }.orEmpty()
            val queuedUrls = queue.map { it.sourceUrl }.toSet()
            val startIdx = origin.indexOfFirst { it.url == currentUrl }.takeIf { it >= 0 } ?: -1
            val next = origin.drop(startIdx + 1).firstOrNull { it.url !in queuedUrls }
                ?: radioPick(currentUrl, queuedUrls)
                ?: run {
                    withContext(Dispatchers.Main.immediate) {
                        if (g == generation) blockFillsFor(NOTHING_TO_APPEND_MS)
                    }
                    return
                }
            val song = transients.fromTrack(next)
            withContext(Dispatchers.Main) {
                if (g == generation) controller.addToQueueEnd(song)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Radio/extend is best-effort; playback of the current song continues.
            android.util.Log.w(TAG, "Session extend failed", e)
            withContext(Dispatchers.Main.immediate) {
                blockFillsFor(NOTHING_TO_APPEND_MS)
            }
        }
    }

    /**
     * Picks the next spice-up/radio track. Priority order mirrors taste
     * signal strength:
     * 1. Related graph of the track playing *right now* — the strongest
     *    "what the user is currently liking" signal.
     * 2. For-You (recency-weighted from the current rotation).
     * 3. Trending (discovery fallback when 1–2 run dry).
     */
    private suspend fun pickSpiceTrack(
        queue: List<com.aura.music.data.db.SongEntity>,
        g: Int
    ): YouTubeTrack? {
        val queuedUrls = queue.map { it.sourceUrl }.toSet()
        val currentUrl = withContext(Dispatchers.Main) {
            if (g == generation) controller.playbackState.value.currentSong?.sourceUrl else null
        }.orEmpty()
        if (currentUrl.isNotBlank()) {
            val related = try {
                extraction.getRelatedTracks(currentUrl, 12)
            } catch (_: Exception) {
                emptyList()
            }
            related
                .filter { it.url !in queuedUrls && it.url !in radioPlayed }
                .shuffled()
                .firstOrNull()
                ?.let { return it }
        }
        val data = recommendations.data.value
        return (data.forYou + data.trending)
            .filter { it.url !in queuedUrls && it.url !in radioPlayed }
            .randomOrNull()
    }

    /**
     * Appends one recommended pick (see [pickSpiceTrack]), skipping anything
     * already queued or recently radio-played.
     */
    private suspend fun appendRandom(
        queue: List<com.aura.music.data.db.SongEntity>,
        g: Int,
        announce: Boolean
    ) {
        if (radioExhausted) return
        val pick = pickSpiceTrack(queue, g) ?: run {
            withContext(Dispatchers.Main.immediate) {
                if (g == generation) {
                    radioExhausted = true
                    blockFillsFor(NOTHING_TO_APPEND_MS)
                }
            }
            return
        }
        try {
            val song = transients.fromTrack(pick)
            // All shared radio state mutates on Main (reads happen there too)
            // so skip-spam can't corrupt it from the IO side.
            withContext(Dispatchers.Main.immediate) {
                if (g == generation) {
                    controller.addToQueueEnd(song)
                    radioPlayed += pick.url
                    if (radioPlayed.size > 80) {
                        val it = radioPlayed.iterator()
                        if (it.hasNext()) {
                            it.next()
                            it.remove()
                        }
                    }
                    if (announce && !radioAnnounced) {
                        radioAnnounced = true
                        _events.emit("Radio — up next: recommended picks")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Spice-up append failed", e)
        }
    }

    /**
     * Origin exhausted: continue as radio from the current track's related
     * items. Returns the first playable pick (also becomes the new origin).
     */
    private suspend fun radioPick(
        currentWatchUrl: String,
        queuedUrls: Set<String>
    ): YouTubeTrack? {
        if (currentWatchUrl.isBlank() || radioSeedNotified == currentWatchUrl) return null
        val related = try {
            extraction.getRelatedTracks(currentWatchUrl, 10)
        } catch (_: Exception) {
            emptyList()
        }
        val pick = related.firstOrNull { it.url !in queuedUrls } ?: return null
        withContext(Dispatchers.Main.immediate) {
            origin = related
            radioSeedNotified = currentWatchUrl
            _events.emit("Radio — continuing with related tracks")
        }
        return pick
    }

    companion object {
        private const val TAG = "UpNextManager"
        /** Resolved-and-queued ahead items per session start. */
        const val PREFILL = 3
        /** Spice-up keeps this many songs queued behind the current one. */
        const val SPICE_AHEAD = 2
        /** Cooldown after a fill found nothing to append (state ticks at 2/sec). */
        const val NOTHING_TO_APPEND_MS = 15_000L
    }
}
