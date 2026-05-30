package com.spyou.eramusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spotify_playlists")
data class SpotifyPlaylistEntity(
    @PrimaryKey val spotifyId: String,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val trackCount: Int,
    val lastSyncedAt: Long,
)
