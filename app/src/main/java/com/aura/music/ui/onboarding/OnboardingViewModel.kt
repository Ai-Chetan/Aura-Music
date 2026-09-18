package com.aura.music.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.prefs.OnboardingPreferences
import com.aura.music.domain.repository.BatchItem
import com.aura.music.domain.repository.STARTER_STAGGER_SECONDS
import com.aura.music.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: OnboardingPreferences,
    private val songRepository: SongRepository
) : ViewModel() {

    /** Null while DataStore is loading; true = already seen, false = show guide. */
    val onboardingCompleted: StateFlow<Boolean?> = prefs.onboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete() {
        viewModelScope.launch { prefs.setOnboardingCompleted(true) }
    }

    /**
     * Enqueues one background download per selected starter track and
     * registers the batch so Library can print per-song results/errors.
     * Enqueue-time rejections are recorded with their reason instead of
     * being swallowed. Starts are staggered so dozens of simultaneous
     * resolutions don't trip YouTube throttling (403 / unavailable).
     */
    fun downloadStarterTracks(tracks: List<StarterTrack>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val items = tracks.mapIndexed { index, track ->
                try {
                    BatchItem(
                        label = "${track.title} — ${track.artist}",
                        url = track.url,
                        workId = songRepository.enqueueDownload(
                            track.url.trim(),
                            index * STARTER_STAGGER_SECONDS
                        ),
                        enqueueError = null
                    )
                } catch (e: Exception) {
                    BatchItem(
                        label = "${track.title} — ${track.artist}",
                        url = track.url,
                        workId = null,
                        enqueueError = e.message ?: "Couldn't start download."
                    )
                }
            }
            songRepository.trackStarterBatch(items)
        }
    }
}
