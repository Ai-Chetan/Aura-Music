package com.aura.music.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_playback"
)

/**
 * Playback extras. Home of the Spice-up switch: when on, the player keeps
 * at least two upcoming songs queued by injecting random recommended picks
 * behind whatever is playing.
 */
@Singleton
class PlaybackPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val spiceUpKey = booleanPreferencesKey("spice_up")

    val spiceUp: Flow<Boolean> = context.playbackDataStore.data
        .map { prefs -> prefs[spiceUpKey] ?: false }

    suspend fun setSpiceUp(enabled: Boolean) {
        context.playbackDataStore.edit { prefs -> prefs[spiceUpKey] = enabled }
    }
}
