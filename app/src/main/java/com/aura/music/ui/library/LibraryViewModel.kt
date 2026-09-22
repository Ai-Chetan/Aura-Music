package com.aura.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.SongWithTags
import com.aura.music.data.db.SavedTrackEntity
import com.aura.music.data.db.SavedTrackWithTags
import com.aura.music.data.db.TagEntity
import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.StreamResolver
import com.aura.music.data.stream.TransientTrackFactory
import com.aura.music.data.stream.UpNextManager
import com.aura.music.data.stream.asTracks
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.PlaylistImportState
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.StarterBatchStatus
import com.aura.music.domain.repository.TagRepository
import com.aura.music.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    /** True until the DB's first emission — the vault shows a skeleton, not a fake "empty". */
    val isLoading: Boolean = true,
    val songs: List<SongWithTags> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val selectedTagNames: Set<String> = emptySet(),
    /** Tags whose songs are hidden from the list AND never queued for playback. */
    val excludedTagNames: Set<String> = emptySet(),
    val matchAll: Boolean = true,
    val searchQuery: String = "",
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT
)

/** Tag filter state for the Saved tab (mirrors the Downloaded filter). */
data class SavedFilterUiState(
    val selectedTagNames: Set<String> = emptySet(),
    val excludedTagNames: Set<String> = emptySet(),
    val matchAll: Boolean = true
)

/**
 * Single filter implementation for both vault lists. Excluded tags always
 * win (hidden + never queued); includes are AND/OR per [matchAll].
 */
fun matchesLibraryFilters(
    title: String,
    artist: String?,
    tagNames: Set<String>,
    query: String,
    selected: Set<String>,
    excluded: Set<String>,
    matchAll: Boolean
): Boolean {
    if (tagNames.any { it in excluded }) return false
    val matchesTags = when {
        selected.isEmpty() -> true
        matchAll -> tagNames.containsAll(selected)
        else -> tagNames.any { it in selected }
    }
    val normalizedQuery = query.trim()
    val matchesQuery = normalizedQuery.isEmpty() ||
        title.contains(normalizedQuery, ignoreCase = true) ||
        (artist?.contains(normalizedQuery, ignoreCase = true) == true)
    return matchesTags && matchesQuery
}

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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val tagRepository: TagRepository,
    private val savedTrackRepository: SavedTrackRepository,
    private val streamResolver: StreamResolver,
    private val transients: TransientTrackFactory,
    private val upNext: UpNextManager,
    private val playbackController: PlaybackController,
    private val gate: NetworkGate
) : ViewModel() {

    private val selectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val excludedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val matchAll = MutableStateFlow(true)
    private val searchQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(LibrarySortMode.RECENT)

    /** Saved-tab tag filter (parallel to the Downloaded filter above). */
    private val savedSelectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val savedExcludedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val savedMatchAll = MutableStateFlow(true)

    /** Live in-progress downloads (starter tracks, pasted links). */
    val activeDownloads: StateFlow<List<ActiveDownload>> =
        songRepository.observeActiveDownloads()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** Live playlist imports (keep running even if the Add screen is gone). */
    val playlistImports: StateFlow<List<PlaylistImportState>> =
        songRepository.observePlaylistImports()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** Starter-batch report (Getting Started selections), if any. */
    val starterBatch: StateFlow<StarterBatchStatus?> =
        songRepository.observeStarterBatch()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    /**
     * Saved streaming bookmarks with tags. The Library's second tab.
     * Unlike downloaded songs these hold no audio bytes — taps resolve and
     * stream via the ExoPlayer disk cache.
     */
    val savedTracks: StateFlow<List<SavedTrackWithTags>> =
        savedTrackRepository.observeSavedWithTags()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** True until the Saved list's first emission (mirrors [LibraryUiState.isLoading]). */
    val savedLoading: StateFlow<Boolean> =
        savedTrackRepository.observeSavedWithTags()
            .map { false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true
            )

    /** Saved-tab sort (parallel to the Downloaded sort). */
    private val _savedSortMode = MutableStateFlow(LibrarySortMode.RECENT)
    val savedSortMode: StateFlow<LibrarySortMode> = _savedSortMode

    fun setSavedSortMode(mode: LibrarySortMode) {
        _savedSortMode.value = mode
    }

    /** Saved-tab filter state for the shared FilterSection. */
    val savedFilterState: StateFlow<SavedFilterUiState> = combine(
        savedSelectedTagNames,
        savedExcludedTagNames,
        savedMatchAll
    ) { selected, excluded, matchAllValue ->
        SavedFilterUiState(selected, excluded, matchAllValue)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SavedFilterUiState()
    )

    fun toggleSavedTag(name: String) {
        savedExcludedTagNames.update { it - name }
        savedSelectedTagNames.update { current ->
            if (name in current) current - name else current + name
        }
    }

    fun toggleSavedExcludedTag(name: String) {
        savedSelectedTagNames.update { it - name }
        savedExcludedTagNames.update { current ->
            if (name in current) current - name else current + name
        }
    }

    fun setSavedMatchAll(value: Boolean) {
        savedMatchAll.value = value
    }

    fun clearSavedFilters() {
        savedSelectedTagNames.value = emptySet()
        savedExcludedTagNames.value = emptySet()
    }

    /** Watch URL of the Saved row currently resolving into audio. */
    private val _savedResolvingUrl = MutableStateFlow<String?>(null)
    val savedResolvingUrl: StateFlow<String?> = _savedResolvingUrl

    fun retryStarterBatch() {
        viewModelScope.launch {
            try {
                songRepository.retryStarterBatch()
            } catch (e: Exception) {
                _messages.emit("Couldn't retry. Try again.")
            }
        }
    }

    fun dismissStarterBatch() {
        songRepository.clearStarterBatch()
    }

    /** One-shot UI messages ("Queued …", "Deleted …"). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            upNext.events.collect { _messages.emit(it) }
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        songRepository.observeAllSongs(),
        tagRepository.observeAllTags(),
        selectedTagNames,
        excludedTagNames,
        matchAll,
        searchQuery,
        sortMode
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val songs = args[0] as List<SongWithTags>
        val tags = args[1] as List<TagEntity>
        val selected = args[2] as Set<String>
        val excluded = args[3] as Set<String>
        val matchAllValue = args[4] as Boolean
        val query = args[5] as String
        val sort = args[6] as LibrarySortMode

        val filteredSongs = songs.filter { item ->
            matchesLibraryFilters(
                title = item.song.title,
                artist = item.song.artist,
                tagNames = item.tags.map { it.name }.toSet(),
                query = query,
                selected = selected,
                excluded = excluded,
                matchAll = matchAllValue
            )
        }.let { sortSongs(it, sort) }

        LibraryUiState(
            isLoading = false,
            songs = filteredSongs,
            tags = tags,
            selectedTagNames = selected,
            excludedTagNames = excluded,
            matchAll = matchAllValue,
            searchQuery = query,
            sortMode = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    fun toggleTag(name: String) {
        // A tag can't be included and excluded at once.
        excludedTagNames.update { it - name }
        selectedTagNames.update { current ->
            if (name in current) current - name else current + name
        }
    }

    fun toggleExcludedTag(name: String) {
        selectedTagNames.update { it - name }
        excludedTagNames.update { current ->
            if (name in current) current - name else current + name
        }
    }

    fun clearExcludedTags() {
        excludedTagNames.value = emptySet()
    }

    fun clearSelectedTags() {
        selectedTagNames.value = emptySet()
    }

    fun setMatchAll(value: Boolean) {
        matchAll.value = value
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun setSortMode(mode: LibrarySortMode) {
        sortMode.value = mode
    }

    fun cancelDownload(workId: java.util.UUID) {
        viewModelScope.launch {
            try {
                songRepository.cancelDownload(workId)
            } catch (e: Exception) {
                _messages.emit("Couldn't cancel download")
            }
        }
    }

    /**
     * Playing-song id only — row highlight flags collect this instead of the
     * full playback state, so the 500ms position ticks don't recompose the
     * whole list twice a second.
     */
    val currentTrackId: StateFlow<Long?> = playbackController.playbackState
        .map { it.currentSong?.id }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun playSong(songWithTags: SongWithTags) {
        val songs = uiState.value.songs.map { it.song }
        val index = songs.indexOfFirst { it.id == songWithTags.song.id }
        // Vault queues own the player — end any streaming up-next session so
        // prefetch appends can't leak into the offline queue.
        upNext.clear()
        if (index >= 0) {
            playbackController.playQueue(songs, index)
        } else {
            // Tapped row already filtered out of view — play it alone rather
            // than the wrong first song.
            playbackController.playQueue(listOf(songWithTags.song), 0)
        }
    }

    /**
     * Shuffle the downloads vault: the whole (filtered) list plays in random
     * order, straight from local files. Radio recommendations still continue
     * when the shuffled list runs out.
     */
    fun shuffleDownloads(songs: List<SongEntity>) {
        val shuffled = songs.shuffled()
        if (shuffled.isEmpty()) return
        upNext.clear()
        playbackController.playQueue(shuffled, 0)
        _messages.tryEmit("Shuffling ${shuffled.size} downloads")
    }

    /**
     * Streaming play for a Saved bookmark: instant start, then the rest of
     * the Saved list continues in order — with radio recommendations at the
     * dead end, and engine picks pre-queued while Spice-up is on.
     */
    fun playSaved(
        track: SavedTrackEntity,
        all: List<SavedTrackEntity>,
        onPlaying: () -> Unit = {}
    ) {
        if (_savedResolvingUrl.value != null) return
        if (!gate.runIfAllowed(action = { resolveAndPlaySaved(track, all, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    private fun resolveAndPlaySaved(
        track: SavedTrackEntity,
        all: List<SavedTrackEntity>,
        onPlaying: () -> Unit
    ) {
        viewModelScope.launch {
            _savedResolvingUrl.value = track.url
            try {
                val transient = transients.fromSaved(track)
                playbackController.playQueue(listOf(transient), 0)
                val tracks = all.asTracks()
                upNext.startPlaylistSession(tracks, tracks.indexOfFirst { it.url == track.url })
                savedTrackRepository.recordPlay(track.url)
                onPlaying()
            } catch (e: Exception) {
                _messages.emit("Couldn't play this right now. Try again.")
            } finally {
                _savedResolvingUrl.value = null
            }
        }
    }

    /**
     * Shuffle the Saved list: a random track starts instantly, the rest of
     * the shuffled order continues behind it (needs internet to resolve).
     */
    fun shuffleSaved(tracks: List<SavedTrackEntity>, onPlaying: () -> Unit = {}) {
        val shuffled = tracks.shuffled()
        if (shuffled.isEmpty()) return
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    _savedResolvingUrl.value = shuffled.first().url
                    try {
                        val first = transients.fromSaved(shuffled.first())
                        playbackController.playQueue(listOf(first), 0)
                        upNext.startPlaylistSession(shuffled.asTracks(), 0)
                        savedTrackRepository.recordPlay(shuffled.first().url)
                        _messages.emit("Shuffling ${shuffled.size} saved tracks")
                        onPlaying()
                    } catch (e: Exception) {
                        _messages.emit("Couldn't play this right now. Try again.")
                    } finally {
                        _savedResolvingUrl.value = null
                    }
                }
            })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    /** Spice-up switch state (auto recommended queue top-ups). */
    val spiceUp: StateFlow<Boolean> = upNext.spiceUp

    fun toggleSpiceUp() = upNext.toggleSpiceUp()

    /** Warm the stream cache for Saved rows as the tab appears. */
    fun prefetchSaved(urls: List<String>) {
        streamResolver.prefetch(urls.take(8))
    }

    /**
     * Queue a Saved bookmark to play after the current song. Resolving
     * spends data, so this is gated like any other streaming action.
     */
    fun queueSaved(track: SavedTrackEntity) {
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.addToQueueEnd(transients.fromSaved(track))
                        _messages.emit("Queued \"${track.title}\" — plays after this song")
                    } catch (e: Exception) {
                        _messages.emit("Couldn't add this to the queue. Try again.")
                    }
                }
            })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't queue.")
            }
        }
    }

    /** A Saved bookmark slotted to play immediately after the current song. */
    fun playNextSaved(track: SavedTrackEntity) {
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.playNext(transients.fromSaved(track))
                        _messages.emit("Will play next: ${track.title}")
                    } catch (e: Exception) {
                        _messages.emit("Couldn't add this to the queue. Try again.")
                    }
                }
            })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't queue.")
            }
        }
    }

    /** Toggle one tag on a Saved bookmark (used by the tag picker). */
    fun toggleSavedTagAssignment(track: SavedTrackWithTags, tag: TagEntity) {
        viewModelScope.launch {
            try {
                if (track.tags.any { it.id == tag.id }) {
                    savedTrackRepository.unassignTag(track.track.id, tag.id)
                } else {
                    savedTrackRepository.assignTag(track.track.id, tag.id)
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't update tags. Try again.")
            }
        }
    }

    /** Creates a tag (if needed) and assigns it to a Saved bookmark. */
    fun createAndAssignSavedTag(track: SavedTrackWithTags, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val tagId = tagRepository.getOrCreateTag(trimmed, null)
                if (tagId > 0) {
                    savedTrackRepository.assignTag(track.track.id, tagId)
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't create that tag. Try again.")
            }
        }
    }

    /** Download a Saved bookmark into the offline vault. */
    fun downloadSaved(track: SavedTrackEntity) {
        viewModelScope.launch {
            try {
                songRepository.enqueueDownload(track.url)
                _messages.emit("Downloading \"${track.title}\" — see downloads above.")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
            }
        }
    }

    fun unsaveTrack(track: SavedTrackEntity) {
        viewModelScope.launch {
            try {
                savedTrackRepository.unsaveById(track.id)
                _messages.emit("Removed \"${track.title}\" from Saved")
            } catch (e: Exception) {
                _messages.emit("Couldn't remove it. Try again.")
            }
        }
    }

    /** Queue to play immediately after the current song. */
    fun playNext(song: SongEntity) {
        // Manual curation wins: a hand-picked vault song ends the streaming
        // session so radio appends can't land behind it.
        upNext.clear()
        playbackController.playNext(song)
        viewModelScope.launch { _messages.emit("Will play next: ${song.title}") }
    }

    /** Append to the end of the queue — queued songs play first-in-first-out. */
    fun addToQueue(song: SongEntity) {
        upNext.clear()
        playbackController.addToQueueEnd(song)
        viewModelScope.launch { _messages.emit("Added to queue: ${song.title}") }
    }

    fun deleteSong(songId: Long, title: String) {
        viewModelScope.launch {
            try {
                songRepository.deleteSong(songId)
                _messages.emit("Deleted \"$title\"")
            } catch (e: Exception) {
                _messages.emit("Couldn't delete it. Try again.")
            }
        }
    }

    private fun sortSongs(
        songs: List<SongWithTags>,
        sort: LibrarySortMode
    ): List<SongWithTags> = when (sort) {
        LibrarySortMode.RECENT -> songs.sortedByDescending { it.song.dateAdded }
        LibrarySortMode.OLDEST -> songs.sortedBy { it.song.dateAdded }
        LibrarySortMode.TITLE_ASC -> songs.sortedBy { it.song.title.lowercase() }
        LibrarySortMode.TITLE_DESC -> songs.sortedByDescending { it.song.title.lowercase() }
        LibrarySortMode.ARTIST_ASC -> songs.sortedBy { (it.song.artist ?: "").lowercase() }
        LibrarySortMode.DURATION_LONG -> songs.sortedByDescending { it.song.durationMs }
        LibrarySortMode.DURATION_SHORT -> songs.sortedBy { it.song.durationMs }
    }

    /** Same ordering vocabulary for Saved bookmarks (by save date). */
    fun sortSaved(
        tracks: List<SavedTrackWithTags>,
        sort: LibrarySortMode
    ): List<SavedTrackWithTags> = when (sort) {
        LibrarySortMode.RECENT -> tracks.sortedByDescending { it.track.dateSaved }
        LibrarySortMode.OLDEST -> tracks.sortedBy { it.track.dateSaved }
        LibrarySortMode.TITLE_ASC -> tracks.sortedBy { it.track.title.lowercase() }
        LibrarySortMode.TITLE_DESC -> tracks.sortedByDescending { it.track.title.lowercase() }
        LibrarySortMode.ARTIST_ASC -> tracks.sortedBy { (it.track.artist ?: "").lowercase() }
        LibrarySortMode.DURATION_LONG -> tracks.sortedByDescending { it.track.durationMs }
        LibrarySortMode.DURATION_SHORT -> tracks.sortedBy { it.track.durationMs }
    }
}
