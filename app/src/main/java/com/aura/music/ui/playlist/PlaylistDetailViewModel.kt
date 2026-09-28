package com.aura.music.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.PlaylistEntity
import com.aura.music.data.db.PlaylistWithCount
import com.aura.music.data.db.TagEntity
import com.aura.music.data.network.NetworkGate
import com.aura.music.data.stream.UpNextManager
import com.aura.music.domain.repository.LibrarySortMode
import com.aura.music.domain.repository.PlaylistRepository
import com.aura.music.domain.repository.SavedTrackRepository
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
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

data class PlaylistDetailUiState(
    val loading: Boolean = true,
    val playlist: PlaylistEntity? = null,
    /** Playlist contents after tag filters + search + sort — what plays. */
    val tracks: List<UnifiedTrack> = emptyList(),
    /** Full playlist contents (for the "N of M" hint and the add-tracks picker). */
    val allTracks: List<UnifiedTrack> = emptyList(),
    /** The whole library (add-tracks picker source). */
    val libraryTracks: List<UnifiedTrack> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val selectedTagNames: Set<String> = emptySet(),
    val excludedTagNames: Set<String> = emptySet(),
    val matchAll: Boolean = true,
    val searchQuery: String = "",
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT
) {
    val offlineCount: Int get() = allTracks.count { it.isOffline }
    val streamCount: Int get() = allTracks.count { !it.isOffline }
    val totalDurationMs: Long get() = allTracks.sumOf { it.durationMs }
    val filtersActive: Boolean
        get() = selectedTagNames.isNotEmpty() || excludedTagNames.isNotEmpty() ||
            searchQuery.isNotBlank()
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val savedTrackRepository: SavedTrackRepository,
    private val tagRepository: TagRepository,
    private val unifiedPlayback: UnifiedPlayback,
    private val upNext: UpNextManager,
    private val playbackController: PlaybackController,
    private val gate: NetworkGate
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: -1L

    private val selectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val excludedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val matchAll = MutableStateFlow(true)
    private val searchQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(LibrarySortMode.RECENT)

    /** One-shot UI messages. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Fired once after a playlist is deleted — the screen pops back. */
    private val _close = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val close: SharedFlow<Unit> = _close.asSharedFlow()

    init {
        viewModelScope.launch {
            upNext.events.collect { _messages.emit(it) }
        }
    }

    private val membership = combine(
        playlistRepository.observeSongIdsIn(playlistId),
        playlistRepository.observeSavedIdsIn(playlistId)
    ) { songIds, savedIds -> songIds to savedIds }

    private val library = combine(
        songRepository.observeAllSongs(),
        savedTrackRepository.observeSavedWithTags()
    ) { songs, saved -> mergeUnifiedTracks(songs, saved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlistRepository.observePlaylist(playlistId),
        membership,
        library,
        selectedTagNames,
        excludedTagNames,
        matchAll,
        searchQuery,
        sortMode
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val playlist = args[0] as PlaylistEntity?
        val songIds = args[1] as Pair<Set<Long>, Set<Long>>
        val libraryTracks = args[2] as List<UnifiedTrack>
        val selected = args[3] as Set<String>
        val excluded = args[4] as Set<String>
        val matchAllValue = args[5] as Boolean
        val query = args[6] as String
        val sort = args[7] as LibrarySortMode

        val contents = libraryTracks.filter { track ->
            (track.song != null && track.song.id in songIds.first) ||
                (track.saved != null && track.saved.id in songIds.second)
        }
        PlaylistDetailUiState(
            loading = false,
            playlist = playlist,
            tracks = filterUnifiedTracks(
                contents, query, selected, excluded, matchAllValue,
                com.aura.music.domain.repository.LibrarySegment.ALL, sort
            ),
            allTracks = contents,
            libraryTracks = libraryTracks,
            tags = libraryTracks.flatMap { it.tags }.distinctBy { it.id }.sortedBy { it.name },
            selectedTagNames = selected,
            excludedTagNames = excluded,
            matchAll = matchAllValue,
            searchQuery = query,
            sortMode = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistDetailUiState()
    )

    /** URL of the row currently resolving into audio. */
    private val _resolvingUrl = MutableStateFlow<String?>(null)
    val resolvingUrl: StateFlow<String?> = _resolvingUrl

    val currentTrackUrl: StateFlow<String?> = playbackController.playbackState
        .map { it.currentSong?.sourceUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- Playlist editing ----

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                playlistRepository.rename(playlistId, trimmed)
                _messages.emit("Playlist renamed")
            } catch (e: Exception) {
                _messages.emit("Couldn't rename. Try again.")
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                playlistRepository.delete(playlistId)
                _messages.emit("Playlist deleted")
                _close.tryEmit(Unit)
            } catch (e: Exception) {
                _messages.emit("Couldn't delete the playlist. Try again.")
            }
        }
    }

    /** Adds the track to the playlist (both sides of a downloaded+saved pair). */
    fun addToPlaylist(track: UnifiedTrack) {
        viewModelScope.launch {
            try {
                track.song?.let { playlistRepository.addSong(playlistId, it.id) }
                track.saved?.let { playlistRepository.addSaved(playlistId, it.id) }
            } catch (e: Exception) {
                _messages.emit("Couldn't add that track. Try again.")
            }
        }
    }

    /** Removes the track from the playlist (file and bookmark stay put). */
    fun removeFromPlaylist(track: UnifiedTrack) {
        viewModelScope.launch {
            try {
                track.song?.let { playlistRepository.removeSong(playlistId, it.id) }
                track.saved?.let { playlistRepository.removeSaved(playlistId, it.id) }
            } catch (e: Exception) {
                _messages.emit("Couldn't remove that track. Try again.")
            }
        }
    }

    // ---- Filters ----

    fun toggleTag(name: String) {
        excludedTagNames.update { it - name }
        selectedTagNames.update {
            if (name in it) it - name else it + name
        }
    }

    fun toggleExcludedTag(name: String) {
        selectedTagNames.update { it - name }
        excludedTagNames.update {
            if (name in it) it - name else it + name
        }
    }

    fun setMatchAll(value: Boolean) {
        matchAll.value = value
    }

    fun clearFilters() {
        selectedTagNames.value = emptySet()
        excludedTagNames.value = emptySet()
        searchQuery.value = ""
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun setSortMode(mode: LibrarySortMode) {
        sortMode.value = mode
    }

    // ---- Playback: the FILTERED list is what plays ----

    /**
     * Plays the filtered playlist at [index] — tag filters decide what is in
     * the queue, exactly like the Library list.
     */
    fun playFiltered(index: Int, onPlaying: () -> Unit = {}) {
        val tracks = uiState.value.tracks
        val item = tracks.getOrNull(index) ?: return
        if (unifiedPlayback.startsOffline(item)) {
            startQueue(tracks, index, onPlaying)
            return
        }
        if (!gate.runIfAllowed(action = { startQueue(tracks, index, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    private fun startQueue(tracks: List<UnifiedTrack>, index: Int, onPlaying: () -> Unit) {
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

    /** Shuffles the filtered playlist and plays it from the first random pick. */
    fun shuffleFiltered(onPlaying: () -> Unit = {}) {
        val shuffled = uiState.value.tracks.shuffled()
        if (shuffled.isEmpty()) return
        if (unifiedPlayback.startsOffline(shuffled.first())) {
            startQueue(shuffled, 0, onPlaying)
            return
        }
        if (!gate.runIfAllowed(action = { startQueue(shuffled, 0, onPlaying) })) {
            viewModelScope.launch {
                _messages.emit(gate.snapshot().reason?.message() ?: "Couldn't stream.")
            }
        }
    }

    fun playNext(track: UnifiedTrack) {
        if (track.isOffline) {
            upNext.clear()
            playbackController.playNext(track.song ?: return)
            viewModelScope.launch { _messages.emit("Will play next: ${track.title}") }
            return
        }
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.playNext(
                            unifiedPlayback.transientFor(track) ?: return@launch
                        )
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

    fun queue(track: UnifiedTrack) {
        if (track.isOffline) {
            upNext.clear()
            playbackController.addToQueueEnd(track.song ?: return)
            viewModelScope.launch { _messages.emit("Added to queue: ${track.title}") }
            return
        }
        if (!gate.runIfAllowed(action = {
                viewModelScope.launch {
                    try {
                        playbackController.addToQueueEnd(
                            unifiedPlayback.transientFor(track) ?: return@launch
                        )
                        _messages.emit("Added to queue: ${track.title}")
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

    // ---- Track-level actions (download / unsave / delete) ----

    /** Downloads a bookmarked track in the playlist into the offline vault. */
    fun downloadTrack(track: UnifiedTrack) {
        val saved = track.saved ?: return
        viewModelScope.launch {
            try {
                songRepository.enqueueDownload(saved.url)
                _messages.emit("Downloading \"${track.title}\" — see the Library.")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Couldn't start download.")
            }
        }
    }

    fun unsaveTrack(track: UnifiedTrack) {
        val saved = track.saved ?: return
        viewModelScope.launch {
            try {
                savedTrackRepository.unsaveById(saved.id)
                _messages.emit("Removed \"${track.title}\" from Saved")
            } catch (e: Exception) {
                _messages.emit("Couldn't remove it. Try again.")
            }
        }
    }

    fun deleteTrack(track: UnifiedTrack) {
        val song = track.song ?: return
        viewModelScope.launch {
            try {
                songRepository.deleteSong(song.id)
                _messages.emit("Deleted \"${track.title}\"")
            } catch (e: Exception) {
                _messages.emit("Couldn't delete it. Try again.")
            }
        }
    }
}
