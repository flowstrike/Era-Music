package com.spyou.eramusic.data.download

sealed class DownloadProgress {
    data object Idle : DownloadProgress()
    data class Syncing(
        val playlistIndex: Int,
        val totalPlaylists: Int,
    ) : DownloadProgress()
    data class Downloading(
        val playlistIndex: Int,
        val totalPlaylists: Int,
        val trackIndex: Int,
        val totalNewTracks: Int,
        val trackTitle: String,
        val trackArtist: String,
        val completedSoFar: Int,
        val failedSoFar: Int,
    ) : DownloadProgress()
    data class Complete(
        val totalDownloaded: Int,
        val totalFailed: Int,
    ) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
