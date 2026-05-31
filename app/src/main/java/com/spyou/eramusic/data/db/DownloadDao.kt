package com.spyou.eramusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: SpotifyPlaylistEntity)

    @Query("SELECT * FROM spotify_playlists ORDER BY name ASC")
    fun observePlaylists(): Flow<List<SpotifyPlaylistEntity>>

    @Query("SELECT * FROM spotify_playlists WHERE spotifyId = :id")
    suspend fun playlist(id: String): SpotifyPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadedTrackEntity)

    @Query("UPDATE downloaded_tracks SET downloadStatus = :status, downloadedAt = :downloadedAt WHERE spotifyId = :id")
    suspend fun updateStatus(id: String, status: String, downloadedAt: Long)

    @Query("DELETE FROM downloaded_tracks WHERE spotifyId = :id")
    suspend fun deleteTrack(id: String)

    @Query("SELECT * FROM downloaded_tracks WHERE spotifyId = :id")
    suspend fun track(id: String): DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE spotifyId IN (:ids) AND downloadStatus = 'COMPLETE'")
    suspend fun tracksByIds(ids: List<String>): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE downloadStatus = 'COMPLETE'")
    fun observeCompletedTracks(): Flow<List<DownloadedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE spotifyPlaylistId = :playlistId AND spotifyTrackId = :trackId")
    suspend fun removeCrossRef(playlistId: String, trackId: String)

    @Query("SELECT spotifyTrackId FROM playlist_track_cross_ref WHERE spotifyPlaylistId = :playlistId")
    suspend fun trackIdsForPlaylist(playlistId: String): List<String>

    @Query("SELECT spotifyTrackId FROM playlist_track_cross_ref WHERE spotifyPlaylistId = :playlistId")
    fun observeTrackIdsForPlaylist(playlistId: String): Flow<List<String>>

    @Query("SELECT DISTINCT spotifyPlaylistId FROM playlist_track_cross_ref WHERE spotifyTrackId = :trackId")
    suspend fun playlistsForTrack(trackId: String): List<String>

    @Query("UPDATE downloaded_tracks SET isFavorite = NOT isFavorite WHERE spotifyId = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE downloaded_tracks SET artworkUrl = :url WHERE spotifyId = :id")
    suspend fun updateArtwork(id: String, url: String?)

    @Query("SELECT isFavorite FROM downloaded_tracks WHERE spotifyId = :id")
    suspend fun isFavorite(id: String): Boolean?

    @Query("SELECT spotifyId FROM downloaded_tracks WHERE isFavorite = 1")
    fun observeFavoriteTrackIds(): Flow<List<String>>
}
