package com.aura.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.PlaylistWithCount
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.TagEntity
import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.StreamResolver
import com.aura.music.data.stream.UpNextManager
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.LibrarySegment
import com.aura.music.domain.repository.LibrarySortMode
import com.aura.music.domain.repository.PlaylistImportState
import com.aura.music.domain.repository.PlaylistRepository
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.StarterBatchStatus
import com.aura.music.domain.repository.TagRepository
import com.aura.music.domain.repository.TagUsage
import com.aura.music.domain.repository.UnifiedTrack
import com.aura.music.domain.repository.filterUnifiedTracks
import com.aura.music.domain.repository.mergeUnifiedTracks
import com.aura.music.playback.PlaybackController
import com.aura.music.playback.UnifiedPlayback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    /** True until both tables' first emissions — skeletons, not fake "empty". */
    val isLoading: Boolean = true,
    /** The list the user sees: filtered, sorted, segmented. */
    val tracks: List<UnifiedTrack> = emptyList(),
    val totalCount: Int = 0,
    val offlineCount: Int = 0,
    val savedOnlyCount: Int = 0,
    val tags: List<TagEntity> = emptyList(),
    val selectedTagNames: Set<String> = emptySet(),
    /** Tags whose tracks are hidden from the list AND never queued for playback. */
    val excludedTagNames: Set<String> = emptySet(),
    val matchAll: Boolean = true,
    val searchQuery: String = "",
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT,
    val segment: LibrarySegment = LibrarySegment.ALL
) {
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() || selectedTagNames.isNotEmpty() ||
            excludedTagNames.isNotEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val tagRepository: TagRepository,
    private val savedTrackRepository: SavedTrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val unifiedPlayback: UnifiedPlayback,
    private val streamResolver: StreamResolver,
    private val upNext: UpNextManager,
    private val playbackController: PlaybackController,
    private val gate: NetworkGate
) : ViewModel() {

    private val selectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val excludedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val matchAll = MutableStateFlow(true)
    private val searchQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(LibrarySortMode.RECENT)
    private val segment = MutableStateFlow(LibrarySegment.ALL)

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

    /** Playlists rail: all playlists with live item counts. */
    val playlists: StateFlow<List<PlaylistWithCount>> =
        playlistRepository.observePlaylists()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** One-shot UI messages ("Queued …", "Deleted …"). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            upNext.events.collect { _messages.emit(it) }
        }
    }

    /** The merged, unfiltered library (downloads + bookmarks, deduped by URL). */
    private val mergedTracks: StateFlow<List<UnifiedTrack>> = combine(
        songRepository.observeAllSongs(),
        savedTrackRepository.observeSavedWithTags(),
        tagRepository.observeAllTags()
    ) { songs, saved, tags ->
        Triple(songs, saved, tags)
    }.map { (songs, saved, _) ->
        mergeUnifiedTracks(songs, saved)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val uiState: StateFlow<LibraryUiState> = combine(
        mergedTracks,
        selectedTagNames,
        excludedTagNames,
        matchAll,
        searchQuery,
        sortMode,
        segment
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val tracks = args[0] as List<UnifiedTrack>
        val selected = args[1] as Set<String>
        val excluded = args[2] as Set<String>
        val matchAllValue = args[3] as Boolean
        val query = args[4] as String
        val sort = args[5] as LibrarySortMode
        val segmentValue = args[6] as LibrarySegment

        LibraryUiState(
            isLoading = false,
            tracks = filterUnifiedTracks(
                tracks, query, selected, excluded, matchAllValue, segmentValue, sort
            ),
            totalCount = tracks.size,
            offlineCount = tracks.count { it.isOffline },
            savedOnlyCount = tracks.count { !it.isOffline },
            tags = tracks.flatMap { it.tags }.distinctBy { it.id }.sortedBy { it.name },
            selectedTagNames = selected,
            excludedTagNames = excluded,
            matchAll = matchAllValue,
            searchQuery = query,
            sortMode = sort,
            segment = segmentValue
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    // ---- Filters / sort / segment ----

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

    fun setSegment(value: LibrarySegment) {
        segment.value = value
    }

    // ---- Tag management (global delete + per-track editor) ----

    /** Per-tag usage counts for the Manage Tags dialog (loaded when it opens). */
    private val _tagUsage = MutableStateFlow<Map<Long, TagUsage>>(emptyMap())
    val tagUsage: StateFlow<Map<Long, TagUsage>> = _tagUsage.asStateFlow()

    fun loadTagUsage() {
        viewModelScope.launch {
            try {
                _tagUsage.value = tagRepository.getTagUsage()
            } catch (_: Exception) {
                // Dialog still works — rows just show without counts.
            }
        }
    }

    /**
     * Deletes a tag everywhere — the DB cascades the assignments away.
     * Dropped from the filters too, so a stale selection can't keep hiding
     * tracks.
     */
    fun deleteTag(tag: TagEntity) {
        viewModelScope.launch {
            try {
                tagRepository.deleteTag(tag.id)
                selectedTagNames.update { it - tag.name }
                excludedTagNames.update { it - tag.name }
                loadTagUsage()
                _messages.emit("Deleted tag \"${tag.name}\"")
            } catch (e: Exception) {
                _messages.emit("Couldn't delete the tag. Try again.")
            }
        }
    }

    /** URL of the track whose tag editor is open (item looked up fresh). */
    private val _tagEditorUrl = MutableStateFlow<String?>(null)
    val tagEditorUrl: StateFlow<String?> = _tagEditorUrl.asStateFlow()

    /** The editor's track, live from the merged list (rotation-safe). */
    val tagEditorTrack: StateFlow<UnifiedTrack?> = combine(mergedTracks, _tagEditorUrl) { tracks, url ->
        url?.let { target -> tracks.firstOrNull { it.url == target } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openTagEditor(track: UnifiedTrack) {
        _tagEditorUrl.value = track.url
    }

    fun closeTagEditor() {
        _tagEditorUrl.value = null
    }

    /**
     * Toggles a tag on the editor's track — applied to BOTH sides when the
     * track is downloaded and bookmarked, so the tag follows the track
     * whichever way it plays.
     */
    fun toggleTagOnTrack(track: UnifiedTrack, tag: TagEntity) {
        viewModelScope.launch {
            try {
                val isAssigned = track.tags.any { it.id == tag.id }
                track.song?.let { song ->
                    if (isAssigned) tagRepository.removeTagFromSong(song.id, tag.id)
                    else tagRepository.addTagToSong(song.id, tag.id)
                }
                track.saved?.let { saved ->
                    if (isAssigned) savedTrackRepository.unassignTag(saved.id, tag.id)
                    else savedTrackRepository.assignTag(saved.id, tag.id)
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't update tags. Try again.")
            }
        }
    }

    /** Creates a tag (if needed) and assigns it to the editor's track. */
    fun createTagOnTrack(track: UnifiedTrack, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val tagId = tagRepository.getOrCreateTag(trimmed, null)
                if (tagId > 0) {
                    track.song?.let { tagRepository.addTagToSong(it.id, tagId) }
                    track.saved?.let { savedTrackRepository.assignTag(it.id, tagId) }
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't create that tag. Try again.")
            }
        }
    }

    // ---- Playlists ----

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val id = playlistRepository.getOrCreate(trimmed)
                if (id > 0) {
                    _messages.emit("Playlist \"$trimmed\" created")
                    onCreated(id)
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't create that playlist. Try again.")
            }
        }
    }

    /** URL of the track whose playlist picker is open (membership re-loaded). */
    private val _playlistPickerUrl = MutableStateFlow<String?>(null)
    val playlistPickerUrl: StateFlow<String?> = _playlistPickerUrl.asStateFlow()

    private val _pickerMembership = MutableStateFlow<Set<Long>>(emptySet())
    val pickerMembership: StateFlow<Set<Long>> = _pickerMembership.asStateFlow()

    val playlistPickerTrack: StateFlow<UnifiedTrack?> =
        combine(mergedTracks, _playlistPickerUrl) { tracks, url ->
            url?.let { target -> tracks.firstOrNull { it.url == target } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openPlaylistPicker(track: UnifiedTrack) {
        _playlistPickerUrl.value = track.url
        viewModelScope.launch {
            try {
                val songIds = track.song?.let { playlistRepository.playlistIdsForSong(it.id) }
                    ?: emptySet()
                val savedIds = track.saved?.let { playlistRepository.playlistIdsForSaved(it.id) }
                    ?: emptySet()
                _pickerMembership.value = songIds + savedIds
            } catch (_: Exception) {
                _pickerMembership.value = emptySet()
            }
        }
    }

    fun closePlaylistPicker() {
        _playlistPickerUrl.value = null
        _pickerMembership.value = emptySet()
    }

    /** Adds/removes the picker's track to/from one playlist. */
    fun togglePlaylistMembership(track: UnifiedTrack, playlistId: Long, isMember: Boolean) {
        viewModelScope.launch {
            try {
                if (isMember) {
                    track.song?.let { playlistRepository.removeSong(playlistId, it.id) }
                    track.saved?.let { playlistRepository.removeSaved(playlistId, it.id) }
                } else {
                    track.song?.let { playlistRepository.addSong(playlistId, it.id) }
                    track.saved?.let { playlistRepository.addSaved(playlistId, it.id) }
                }
                // Refresh membership so the chip reflects the new state.
                val songIds = track.song?.let { playlistRepository.playlistIdsForSong(it.id) }
                    ?: emptySet()
                val savedIds = track.saved?.let { playlistRepository.playlistIdsForSaved(it.id) }
                    ?: emptySet()
                _pickerMembership.value = songIds + savedIds
            } catch (e: Exception) {
                _messages.emit("Couldn't update the playlist. Try again.")
            }
        }
    }

    /** Creates a playlist and immediately adds the picker's track to it. */
    fun createPlaylistAndAdd(track: UnifiedTrack, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val id = playlistRepository.getOrCreate(trimmed)
                if (id > 0) {
                    track.song?.let { playlistRepository.addSong(id, it.id) }
                    track.saved?.let { playlistRepository.addSaved(id, it.id) }
                    _messages.emit("Added to \"$trimmed\"")
                    _pickerMembership.update { it + id }
                }
            } catch (e: Exception) {
                _messages.emit("Couldn't create that playlist. Try again.")
            }
        }
    }

    // ---- Playback ----

    /**
     * Playing-track URL only — row highlight flags collect this instead of
     * the full playback state, so the 500ms position ticks don't recompose
     * the whole list twice a second. Works for downloads (positive ids) and
     * stream transients alike, since both carry the canonical watch URL.
     */
    val currentTrackUrl: StateFlow<String?> = playbackController.playbackState
        .map { it.currentSong?.sourceUrl }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Watch URL of the row currently resolving into audio. */
    private val _resolvingUrl = MutableStateFlow<String?>(null)
    val resolvingUrl: StateFlow<String?> = _resolvingUrl

    /**
     * Plays any mixed list at [index]: the tapped track starts (downloads
     * instantly offline, streams resolve first), the rest continues in order
     * behind it — filtered or not, that's the caller's list.
     */
    fun playUnified(tracks: List<UnifiedTrack>, index: Int, onPlaying: () -> Unit = {}) {
        val item = tracks.getOrNull(index) ?: return
        if (unifiedPlayback.startsOffline(item)) {
            startUnifiedQueue(tracks, index, onPlaying)
            return
        }
        // Streaming head: resolving spends data, so gate like any stream play.
        if (!gate.runIfAllowed(action = { startUnifiedQueue(tracks, index, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    private fun startUnifiedQueue(tracks: List<UnifiedTrack>, index: Int, onPlaying: () -> Unit) {
        viewModelScope.launch {
            val item = tracks[index]
            if (!item.isOffline) _resolvingUrl.value = item.url
            try {
                unifiedPlayback.start(tracks, index)
                onPlaying()
            } catch (e: Exception) {
                _messages.emit("Couldn't play this right now. Try again.")
            } finally {
                _resolvingUrl.value = null
            }
        }
    }

    /**
     * Shuffle the list the user is looking at: a random track starts
     * instantly, the rest of the shuffled order continues behind it.
     */
    fun shuffleUnified(tracks: List<UnifiedTrack>, onPlaying: () -> Unit = {}) {
        val shuffled = tracks.shuffled()
        if (shuffled.isEmpty()) return
        playUnified(shuffled, 0, onPlaying)
    }

    /** Queue to play immediately after the current song. */
    fun playNextUnified(item: UnifiedTrack) {
        if (item.isOffline) {
            upNext.clear()
            playbackController.playNext(item.song ?: return)
            viewModelScope.launch { _messages.emit("Will play next: ${item.title}") }
            return
        }
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.playNext(unifiedPlayback.transientFor(item) ?: return@launch)
                        _messages.emit("Will play next: ${item.title}")
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

    /** Append to the end of the queue — queued songs play first-in-first-out. */
    fun queueUnified(item: UnifiedTrack) {
        if (item.isOffline) {
            upNext.clear()
            playbackController.addToQueueEnd(item.song ?: return)
            viewModelScope.launch { _messages.emit("Added to queue: ${item.title}") }
            return
        }
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.addToQueueEnd(
                            unifiedPlayback.transientFor(item) ?: return@launch
                        )
                        _messages.emit("Added to queue: ${item.title}")
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

    /** Spice-up switch state (auto recommended queue top-ups). */
    val spiceUp: StateFlow<Boolean> = upNext.spiceUp

    fun toggleSpiceUp() = upNext.toggleSpiceUp()

    /** Warm the stream cache for visible streaming rows. */
    fun prefetchStreams(urls: List<String>) {
        streamResolver.prefetch(urls.take(8))
    }

    // ---- Track-level actions ----

    /** Download a bookmark into the offline vault. */
    fun downloadUnified(item: UnifiedTrack) {
        val saved = item.saved ?: return
        viewModelScope.launch {
            try {
                songRepository.enqueueDownload(saved.url)
                _messages.emit("Downloading \"${item.title}\" — see the downloads card.")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
            }
        }
    }

    fun unsaveUnified(item: UnifiedTrack) {
        val saved = item.saved ?: return
        viewModelScope.launch {
            try {
                savedTrackRepository.unsaveById(saved.id)
                _messages.emit("Removed \"${item.title}\" from Saved")
            } catch (e: Exception) {
                _messages.emit("Couldn't remove it. Try again.")
            }
        }
    }

    fun deleteUnified(item: UnifiedTrack) {
        val song = item.song ?: return
        viewModelScope.launch {
            try {
                songRepository.deleteSong(song.id)
                _messages.emit("Deleted \"${item.title}\"")
            } catch (e: Exception) {
                _messages.emit("Couldn't delete it. Try again.")
            }
        }
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
}
