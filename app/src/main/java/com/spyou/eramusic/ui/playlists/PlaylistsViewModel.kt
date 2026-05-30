package com.spyou.eramusic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spyou.eramusic.appContainer
import com.spyou.eramusic.data.MusicRepository
import com.spyou.eramusic.data.PlaylistRepository
import com.spyou.eramusic.data.Song
import com.spyou.eramusic.data.db.PlaylistWithCount
import com.spyou.eramusic.playback.PlayerConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithCount>> =
        playlistRepository.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<Long>> =
        playlistRepository.observeFavoriteSongIds()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** All device songs keyed by id, used to resolve a playlist's stored song ids to [Song]s. */
    private val songsById = MutableStateFlow<Map<Long, Song>>(emptyMap())

    fun refreshSongs() {
        viewModelScope.launch {
            songsById.value = musicRepository.queryAll().associateBy { it.id }
        }
    }

    /** Cold flow of the resolved songs in a playlist, in stored order. */
    fun songsIn(playlistId: Long): Flow<List<Song>> =
        combine(playlistRepository.observeSongIds(playlistId), songsById) { ids, byId ->
            ids.mapNotNull { byId[it] }
        }

    fun create(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { playlistRepository.rename(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { playlistRepository.delete(id) }
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepository.addToPlaylist(playlistId, songId) }
    }

    fun createAndAdd(name: String, songId: Long) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name)
            playlistRepository.addToPlaylist(id, songId)
        }
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepository.removeFromPlaylist(playlistId, songId) }
    }

    fun move(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playlistRepository.move(playlistId, fromIndex, toIndex) }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { playlistRepository.toggleFavorite(songId) }
    }

    fun play(songs: List<Song>, index: Int) {
        playerConnection.setQueue(songs, index)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                PlaylistsViewModel(
                    container.playlistRepository,
                    container.musicRepository,
                    container.playerConnection,
                )
            }
        }
    }
}
