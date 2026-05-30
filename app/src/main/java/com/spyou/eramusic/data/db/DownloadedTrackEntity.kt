package com.spyou.eramusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val spotifyId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val localFilePath: String,
    val artworkUrl: String?,
    val downloadStatus: String,
    val downloadedAt: Long,
)
