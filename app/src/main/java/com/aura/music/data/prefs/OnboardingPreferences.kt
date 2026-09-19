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

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_onboarding"
)

/**
 * Persists whether the Getting Started flow was finished or skipped.
 * Shown once on first launch; reopenable from Library.
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val completedKey = booleanPreferencesKey("onboarding_completed")

    val onboardingCompleted: Flow<Boolean> = context.onboardingDataStore.data
        .map { prefs -> prefs[completedKey] == true }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.onboardingDataStore.edit { prefs ->
            prefs[completedKey] = completed
        }
    }
}
