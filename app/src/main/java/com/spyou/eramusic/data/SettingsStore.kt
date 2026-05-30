package com.spyou.eramusic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persists lightweight user preferences (currently the library sort order). */
class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val sortKey = intPreferencesKey("sort_order")

    val sortOrder: Flow<SortOrder> = appContext.dataStore.data.map { prefs ->
        val ordinal = prefs[sortKey] ?: SortOrder.TITLE.ordinal
        SortOrder.entries.getOrElse(ordinal) { SortOrder.TITLE }
    }

    suspend fun setSortOrder(order: SortOrder) {
        appContext.dataStore.edit { it[sortKey] = order.ordinal }
    }
}
