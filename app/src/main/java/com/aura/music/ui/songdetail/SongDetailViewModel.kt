package com.aura.music.ui.songdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.db.SongEntity
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.SongRepository
import com.aura.music.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val tagRepository: TagRepository
) : ViewModel() {

    private val songId: Long = savedStateHandle.get<Long>("songId") ?: -1L

    private val song = MutableStateFlow<SongEntity?>(null)

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
            song.value = songRepository.getSongById(songId)
        }
    }

    fun toggleTag(tag: TagEntity) {
        viewModelScope.launch {
            val currentSongTags = uiState.value.songTags
            if (currentSongTags.any { it.id == tag.id }) {
                tagRepository.removeTagFromSong(songId, tag.id)
            } else {
                tagRepository.addTagToSong(songId, tag.id)
            }
        }
    }

    fun addTagById(tagId: Long) {
        viewModelScope.launch {
            tagRepository.addTagToSong(songId, tagId)
        }
    }

    fun removeTagById(tagId: Long) {
        viewModelScope.launch {
            tagRepository.removeTagFromSong(songId, tagId)
        }
    }

    fun createAndAddTag(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch

            val tagId = tagRepository.getOrCreateTag(trimmed)
            if (tagId > 0) {
                tagRepository.addTagToSong(songId, tagId)
            }
        }
    }
}