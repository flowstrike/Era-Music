package com.spyou.eramusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user playlist. The single row with [isFavorites] = true is the reserved Favorites list. */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isFavorites: Boolean = false,
)
