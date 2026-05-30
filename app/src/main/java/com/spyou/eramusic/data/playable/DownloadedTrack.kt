package com.spyou.eramusic.data.playable

import android.net.Uri
import java.io.File

data class DownloadedTrack(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val localFile: File,
) : PlayableTrack {
    override val trackId: String get() = spotifyId
    override val trackTitle: String get() = title
    override val trackArtist: String get() = artist
    override val trackArtworkUri: Uri? get() = artworkUrl?.let { Uri.parse(it) }
}
