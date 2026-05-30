package com.spyou.eramusic.ui.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spyou.eramusic.appContainer
import com.spyou.eramusic.data.MusicRepository
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.data.Song
import com.spyou.eramusic.data.SortOrder
import com.spyou.eramusic.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val settingsStore: SettingsStore,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val allSongs = MutableStateFlow<List<Song>>(emptyList())
    private val query = MutableStateFlow("")
    private val _loaded = MutableStateFlow(false)

    val searchQuery: StateFlow<String> = query.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val sortOrder: StateFlow<SortOrder> =
        settingsStore.sortOrder.stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.TITLE)

    val songs: StateFlow<List<Song>> =
        combine(allSongs, query, sortOrder) { list, q, order ->
            MusicRepository.sort(MusicRepository.search(list, q), order)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Re-queries MediaStore. Safe to call once audio permission is granted. */
    fun refresh() {
        viewModelScope.launch {
            allSongs.value = musicRepository.queryAll()
            _loaded.value = true
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setSort(order: SortOrder) {
        viewModelScope.launch { settingsStore.setSortOrder(order) }
    }

    /** Plays the current (filtered/sorted) list starting at [index]. */
    fun play(index: Int) {
        playerConnection.setQueue(songs.value, index)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                LibraryViewModel(
                    container.musicRepository,
                    container.settingsStore,
                    container.playerConnection,
                )
            }
        }
    }
}
