package com.aura.music.ui.songdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SongDetailUiState(
    val song: SongEntity? = null,
    val songTags: List<TagEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList()
) {
    val availableTags: List<TagEntity>
        get() {
            val songTagIds = songTags.map { it.id }.toSet()
            return allTags.filterNot { it.id in songTagIds }
        }
}

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val tagRepository: TagRepository,
    private val playbackController: com.aura.music.playback.PlaybackController
) : ViewModel() {

    private val songId: Long = savedStateHandle.get<Long>("songId") ?: -1L

    private val song = MutableStateFlow<SongEntity?>(null)

    /** One-shot user-facing errors (tag ops, loads) — collected as toasts. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<SongDetailUiState> = combine(
        song,
        tagRepository.observeTagsForSong(songId),
        tagRepository.observeAllTags()
    ) { songValue, songTags, allTags ->
        SongDetailUiState(
            song = songValue,
            songTags = songTags,
            allTags = allTags
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongDetailUiState()
    )

    init {
        viewModelScope.launch {
            try {
                song.value = songRepository.getSongById(songId)
            } catch (_: Exception) {
                // uiState keeps song=null → the screen shows its not-found state.
            }
        }
    }

    /** Plays this song as a fresh single-track queue. */
    fun playSong() {
        val current = song.value ?: return
        playbackController.playQueue(listOf(current), 0)
    }

    fun addTagById(tagId: Long) {
        viewModelScope.launch {
            try {
                tagRepository.addTagToSong(songId, tagId)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Couldn't add tag")
            }
        }
    }

    fun removeTagById(tagId: Long) {
        viewModelScope.launch {
            try {
                tagRepository.removeTagFromSong(songId, tagId)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Couldn't remove tag")
            }
        }
    }

    fun createAndAddTag(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            try {
                val tagId = tagRepository.getOrCreateTag(trimmed)
                if (tagId > 0) {
                    tagRepository.addTagToSong(songId, tagId)
                }
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Couldn't create tag")
            }
        }
    }
}