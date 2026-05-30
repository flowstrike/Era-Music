package com.spyou.eramusic.data.db

/** Projection used to show a playlist alongside how many songs it holds. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val isFavorites: Boolean,
    val songCount: Int,
)
