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

private val Context.dataUsageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_data_usage"
)

/**
 * One data rule for the whole app: everything is on by default, on any
 * connection. The only option is opting *out* of mobile-data consumption —
 * then only the downloaded vault is available on metered connections.
 */
@Singleton
class DataUsagePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val useMobileDataKey = booleanPreferencesKey("use_mobile_data")

    /** False = on mobile data, only downloads are accessible. Default true. */
    val useMobileData: Flow<Boolean> = context.dataUsageDataStore.data
        .map { prefs -> prefs[useMobileDataKey] ?: true }

    suspend fun setUseMobileData(use: Boolean) {
        context.dataUsageDataStore.edit { prefs -> prefs[useMobileDataKey] = use }
    }
}
