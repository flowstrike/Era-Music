package com.spyou.eramusic.data.download

import android.content.Context
import android.util.Log
import com.spyou.eramusic.data.db.DownloadedTrackEntity
import com.spyou.eramusic.data.db.PlaylistTrackCrossRef
import com.spyou.eramusic.data.db.SpotifyPlaylistEntity
import com.spyou.eramusic.data.db.DownloadDao
import com.spyou.eramusic.data.spotify.SpotifyMetadataService
import com.spyou.eramusic.data.youtube.YouTubeDownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SyncOrchestrator(
    private val spotifyMetadataService: SpotifyMetadataService,
    private val youTubeDownloadService: YouTubeDownloadService,
    private val downloadDao: DownloadDao,
    private val context: Context,
) {
    private val _progress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    companion object {
        private const val TAG = "SyncOrchestrator"
        val PLAYLIST_IDS = listOf(
            "2IqhvoGlZzmKQawiK8L6jA",
            "6s3PWOnScpZwBAXvcxpJFo",
            "0GewAFN4Nf5Ce7qBGKmgay",
        )
    }

    suspend fun syncAll() {
        Log.d(TAG, "syncAll() starting for ${PLAYLIST_IDS.size} playlists")
        _progress.value = DownloadProgress.Idle
        var totalDownloaded = 0
        var totalFailed = 0

        try {
            for ((index, playlistId) in PLAYLIST_IDS.withIndex()) {
                val playlistNum = index + 1
                Log.d(TAG, "Fetching playlist $playlistNum/$PLAYLIST_IDS.size: $playlistId")
                _progress.value = DownloadProgress.Syncing(playlistNum, PLAYLIST_IDS.size)

                val spotifyPlaylist = spotifyMetadataService.fetchPlaylist(playlistId)
                Log.d(TAG, "Got playlist '${spotifyPlaylist.name}' with ${spotifyPlaylist.tracks.size} tracks")

                downloadDao.upsertPlaylist(
                    SpotifyPlaylistEntity(
                        spotifyId = spotifyPlaylist.id,
                        name = spotifyPlaylist.name,
                        description = spotifyPlaylist.description,
                        artworkUrl = spotifyPlaylist.artworkUrl,
                        trackCount = spotifyPlaylist.tracks.size,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                )

                val existingTrackIds = downloadDao.trackIdsForPlaylist(playlistId).toSet()
                val remoteTrackIds = spotifyPlaylist.tracks.map { it.id }.toSet()
                Log.d(TAG, "Existing: ${existingTrackIds.size}, Remote: ${remoteTrackIds.size}, New: ${remoteTrackIds - existingTrackIds.size}")

                val removedTrackIds = existingTrackIds - remoteTrackIds
                for (trackId in removedTrackIds) {
                    val otherPlaylists = downloadDao.playlistsForTrack(trackId)
                    if (otherPlaylists.size <= 1) {
                        val track = downloadDao.track(trackId)
                        if (track != null) {
                            File(context.filesDir, track.localFilePath).delete()
                            downloadDao.deleteTrack(trackId)
                        }
                    }
                    downloadDao.removeCrossRef(playlistId, trackId)
                }

                val newTracks = spotifyPlaylist.tracks.filter { it.id !in existingTrackIds }
                for ((trackIdx, track) in newTracks.withIndex()) {
                    Log.d(TAG, "Downloading [${trackIdx + 1}/${newTracks.size}]: ${track.title} by ${track.artist}")
                    _progress.value = DownloadProgress.Downloading(
                        playlistIndex = playlistNum,
                        totalPlaylists = PLAYLIST_IDS.size,
                        trackIndex = trackIdx + 1,
                        totalNewTracks = newTracks.size,
                        trackTitle = track.title,
                        trackArtist = track.artist,
                        completedSoFar = totalDownloaded,
                        failedSoFar = totalFailed,
                    )

                    val relativePath = "music/${track.id}.m4a"
                    val file = File(context.filesDir, relativePath)
                    file.parentFile?.mkdirs()

                    downloadDao.insertTrack(
                        DownloadedTrackEntity(
                            spotifyId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = track.durationMs,
                            localFilePath = relativePath,
                            artworkUrl = track.artworkUrl,
                            downloadStatus = "DOWNLOADING",
                            downloadedAt = 0L,
                        )
                    )
                    downloadDao.insertCrossRef(
                        PlaylistTrackCrossRef(playlistId, track.id)
                    )

                    val success = youTubeDownloadService.searchAndDownload(
                        title = track.title,
                        artist = track.artist,
                        targetDurationMs = track.durationMs,
                        outputFile = file,
                    )

                    if (success) {
                        downloadDao.updateStatus(track.id, "COMPLETE", System.currentTimeMillis())
                        totalDownloaded++
                        Log.d(TAG, "SUCCESS: ${track.title} → ${file.absolutePath} (${file.length()} bytes)")
                    } else {
                        downloadDao.updateStatus(track.id, "FAILED", 0L)
                        totalFailed++
                        Log.w(TAG, "FAILED: ${track.title}")
                    }
                }
            }
            Log.d(TAG, "syncAll complete: $totalDownloaded downloaded, $totalFailed failed")
            _progress.value = DownloadProgress.Complete(totalDownloaded, totalFailed)
        } catch (e: Exception) {
            Log.e(TAG, "syncAll FAILED", e)
            _progress.value = DownloadProgress.Error(e.message ?: "Sync failed")
        }
    }
}
