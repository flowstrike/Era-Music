package com.spyou.eramusic.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val sortKey = intPreferencesKey("sort_order")
    private val downloadsInitKey = booleanPreferencesKey("pref_downloads_initialized")
    private val lastSyncKey = longPreferencesKey("pref_last_sync_time")
    private val darkModeKey = intPreferencesKey("pref_dark_mode")

    val sortOrder: Flow<SortOrder> = appContext.dataStore.data.map { prefs ->
        val ordinal = prefs[sortKey] ?: SortOrder.TITLE.ordinal
        SortOrder.entries.getOrElse(ordinal) { SortOrder.TITLE }
    }

    suspend fun setSortOrder(order: SortOrder) {
        appContext.dataStore.edit { it[sortKey] = order.ordinal }
    }

    val downloadsInitialized: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[downloadsInitKey] ?: false
    }

    suspend fun setDownloadsInitialized(value: Boolean) {
        appContext.dataStore.edit { it[downloadsInitKey] = value }
    }

    val lastSyncTime: Flow<Long> = appContext.dataStore.data.map { prefs ->
        prefs[lastSyncKey] ?: 0L
    }

    suspend fun setLastSyncTime(timeMs: Long) {
        appContext.dataStore.edit { it[lastSyncKey] = timeMs }
    }

    enum class DarkMode(val value: Int) { SYSTEM(0), LIGHT(1), DARK(2) }

    val darkMode: Flow<DarkMode> = appContext.dataStore.data.map { prefs ->
        val v = prefs[darkModeKey] ?: 0
        DarkMode.entries.getOrElse(v) { DarkMode.SYSTEM }
    }

    suspend fun setDarkMode(mode: DarkMode) {
        appContext.dataStore.edit { it[darkModeKey] = mode.value }
    }
}
