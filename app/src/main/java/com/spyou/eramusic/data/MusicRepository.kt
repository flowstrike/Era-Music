package com.spyou.eramusic.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's audio library from [MediaStore].
 *
 * [sort] and [search] are pure functions (companion) so they can be unit-tested
 * without an Android runtime; [queryAll] performs the actual cursor read.
 */
class MusicRepository(private val context: Context) {

    suspend fun queryAll(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            order,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                songs += Song(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    album = cursor.getString(albumCol) ?: "",
                    durationMs = cursor.getLong(durationCol),
                    albumId = cursor.getLong(albumIdCol),
                    dateAddedSec = cursor.getLong(dateCol),
                )
            }
        }
        songs
    }

    companion object {
        fun sort(songs: List<Song>, order: SortOrder): List<Song> = when (order) {
            SortOrder.TITLE -> songs.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SortOrder.DATE_ADDED -> songs.sortedByDescending { it.dateAddedSec }
            SortOrder.DURATION -> songs.sortedBy { it.durationMs }
        }

        fun search(songs: List<Song>, query: String): List<Song> {
            if (query.isBlank()) return songs
            val q = query.trim().lowercase()
            return songs.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }
}
