package com.aura.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.SongWithTags
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.ActiveDownload
import com.aura.music.domain.repository.PlaylistImportState
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
    val songs: List<SongWithTags> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val selectedTagNames: Set<String> = emptySet(),
    /** Tags whose songs are hidden from the list AND never queued for playback. */
    val excludedTagNames: Set<String> = emptySet(),
    val matchAll: Boolean = true,
    val searchQuery: String = "",
    val sortMode: LibrarySortMode = LibrarySortMode.RECENT
)

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
    private val playbackController: PlaybackController
) : ViewModel() {

    private val selectedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val excludedTagNames = MutableStateFlow<Set<String>>(emptySet())
    private val matchAll = MutableStateFlow(true)
    private val searchQuery = MutableStateFlow("")
    private val sortMode = MutableStateFlow(LibrarySortMode.RECENT)

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

    fun retryStarterBatch() {
        viewModelScope.launch {
            try {
                songRepository.retryStarterBatch()
            } catch (e: Exception) {
                _messages.emit("Couldn't retry: ${e.message}")
            }
        }
    }

    fun dismissStarterBatch() {
        songRepository.clearStarterBatch()
    }

    /** One-shot UI messages ("Queued …", "Deleted …"). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

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
        val normalizedQuery = query.trim()

        val filteredSongs = songs.filter { item ->
            val songTagNames = item.tags.map { it.name }.toSet()

            // Excluded tags win over everything: hidden + never queued.
            if (songTagNames.any { it in excluded }) return@filter false

            val matchesTags = when {
                selected.isEmpty() -> true
                matchAllValue -> songTagNames.containsAll(selected)
                else -> songTagNames.any { it in selected }
            }

            val matchesQuery = normalizedQuery.isEmpty() ||
                item.song.title.contains(normalizedQuery, ignoreCase = true) ||
                item.song.artist?.contains(normalizedQuery, ignoreCase = true) == true

            matchesTags && matchesQuery
        }.let { sortSongs(it, sort) }

        LibraryUiState(
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

    val playbackState = playbackController.playbackState

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
        playbackController.playQueue(songs, if (index >= 0) index else 0)
    }

    /** Queue to play immediately after the current song. */
    fun playNext(song: SongEntity) {
        playbackController.playNext(song)
        _messages.tryEmit("Will play next: ${song.title}")
    }

    /** Append to the end of the queue — queued songs play first-in-first-out. */
    fun addToQueue(song: SongEntity) {
        playbackController.addToQueueEnd(song)
        _messages.tryEmit("Added to queue: ${song.title}")
    }

    fun deleteSong(songId: Long, title: String) {
        viewModelScope.launch {
            try {
                songRepository.deleteSong(songId)
                _messages.emit("Deleted \"$title\"")
            } catch (e: Exception) {
                _messages.emit("Couldn't delete: ${e.message}")
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
}
