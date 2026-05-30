package com.spyou.eramusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // --- Playlists ---

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id AND isFavorites = 0")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id AND isFavorites = 0")
    suspend fun deletePlaylist(id: Long)

    @Query(
        """
        SELECT p.id, p.name, p.isFavorites,
               (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount
        FROM playlists p
        ORDER BY p.isFavorites DESC, p.name COLLATE NOCASE ASC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT id FROM playlists WHERE isFavorites = 1 LIMIT 1")
    suspend fun favoritesId(): Long

    // --- Playlist songs ---

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun songIds(playlistId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(item: PlaylistSong)

    @Transaction
    suspend fun addSong(playlistId: Long, songId: Long) {
        insertSong(PlaylistSong(playlistId, songId, maxPosition(playlistId) + 1))
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun contains(playlistId: Long, songId: Long): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_songs ps
            JOIN playlists p ON p.id = ps.playlistId
            WHERE p.isFavorites = 1 AND ps.songId = :songId
        )
        """
    )
    fun observeIsFavorite(songId: Long): Flow<Boolean>

    @Query(
        """
        SELECT ps.songId FROM playlist_songs ps
        JOIN playlists p ON p.id = ps.playlistId
        WHERE p.isFavorites = 1
        """
    )
    fun observeFavoriteSongIds(): Flow<List<Long>>

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPosition(playlistId: Long, songId: Long, position: Int)
}
