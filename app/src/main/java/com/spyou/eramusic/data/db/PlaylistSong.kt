package com.spyou.eramusic.data.db

import androidx.room.Entity
import androidx.room.Index

/** Join row linking a song (by MediaStore id) to a playlist, with an explicit order. */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("playlistId")],
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)
