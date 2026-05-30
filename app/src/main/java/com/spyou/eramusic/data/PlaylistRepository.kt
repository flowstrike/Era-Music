package com.spyou.eramusic.data

import com.spyou.eramusic.data.db.Playlist
import com.spyou.eramusic.data.db.PlaylistDao
import com.spyou.eramusic.data.db.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

/** Playlist + favorites operations backed by Room. */
class PlaylistRepository(private val dao: PlaylistDao) {

    fun observePlaylists(): Flow<List<PlaylistWithCount>> = dao.observePlaylists()

    fun observeSongIds(playlistId: Long): Flow<List<Long>> = dao.observeSongIds(playlistId)

    fun observeIsFavorite(songId: Long): Flow<Boolean> = dao.observeIsFavorite(songId)

    fun observeFavoriteSongIds(): Flow<List<Long>> = dao.observeFavoriteSongIds()

    suspend fun songIds(playlistId: Long): List<Long> = dao.songIds(playlistId)

    suspend fun favoritesId(): Long = dao.favoritesId()

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(Playlist(name = name))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    suspend fun delete(id: Long) {
        dao.clearPlaylistSongs(id)
        dao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: Long, songId: Long) = dao.addSong(playlistId, songId)

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = dao.removeSong(playlistId, songId)

    suspend fun toggleFavorite(songId: Long) {
        val favId = dao.favoritesId()
        if (dao.contains(favId, songId)) dao.removeSong(favId, songId) else dao.addSong(favId, songId)
    }

    /** Moves the song currently at [fromIndex] to [toIndex], rewriting all positions in order. */
    suspend fun move(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val ids = dao.songIds(playlistId).toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return
        val moved = ids.removeAt(fromIndex)
        ids.add(toIndex, moved)
        ids.forEachIndexed { index, songId -> dao.setPosition(playlistId, songId, index) }
    }
}
