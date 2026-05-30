package com.spyou.eramusic.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.spyou.eramusic.data.playable.PlayableTrack

/**
 * A single audio track read from [MediaStore].
 *
 * [uri] and [albumArtUri] are computed lazily from the ids so the model itself
 * stays free of Android framework calls at construction time (keeps it unit-testable).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val dateAddedSec: Long,
) : PlayableTrack {
    val uri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )

    override val trackId: String get() = id.toString()
    override val trackTitle: String get() = title
    override val trackArtist: String get() = artist
    override val trackArtworkUri: Uri? get() = albumArtUri
}
