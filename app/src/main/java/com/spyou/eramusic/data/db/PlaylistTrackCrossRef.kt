package com.spyou.eramusic.data.db

import androidx.room.Entity

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["spotifyPlaylistId", "spotifyTrackId"],
)
data class PlaylistTrackCrossRef(
    val spotifyPlaylistId: String,
    val spotifyTrackId: String,
)
