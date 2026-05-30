package com.spyou.eramusic.data.spotify

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpotifyMetadataService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    companion object {
        private const val TAG = "SpotifyMetadata"
        private const val EMBED_BASE = "https://open.spotify.com/embed/playlist"
    }

    suspend fun fetchPlaylist(playlistId: String): SpotifyPlaylistInfo =
        withContext(Dispatchers.IO) {
            val url = "$EMBED_BASE/$playlistId"
            Log.d(TAG, "Fetching embed page: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: error("Empty response from Spotify embed")
            Log.d(TAG, "Got embed page: ${response.code}, length=${html.length}")

            if (!response.isSuccessful) {
                throw RuntimeException("Spotify embed page failed (${response.code})")
            }

            val jsonStart = html.indexOf("""<script id="__NEXT_DATA__" type="application/json">""")
            if (jsonStart < 0) error("__NEXT_DATA__ script not found in embed page")
            val jsonFrom = html.indexOf(">", jsonStart) + 1
            val jsonEnd = html.indexOf("</script>", jsonFrom)
            val json = html.substring(jsonFrom, jsonEnd)

            val nextData = gson.fromJson(json, NextDataResponse::class.java)
            val entity = nextData.props.pageProps.state.data.entity
            Log.d(TAG, "Playlist: ${entity.title}, tracks: ${entity.trackList.size}")

            SpotifyPlaylistInfo(
                id = entity.id,
                name = entity.title,
                description = entity.subtitle,
                artworkUrl = entity.coverArt?.sources?.firstOrNull()?.url,
                tracks = entity.trackList.map { track ->
                    val trackId = track.uri.removePrefix("spotify:track:")
                    SpotifyTrack(
                        id = trackId,
                        title = track.title,
                        artist = track.subtitle ?: "Unknown",
                        album = "",
                        durationMs = track.duration.toLong(),
                        artworkUrl = null,
                    )
                },
            )
        }
}

private data class NextDataResponse(
    val props: PropsResponse,
)

private data class PropsResponse(
    val pageProps: PagePropsResponse,
)

private data class PagePropsResponse(
    val state: StateResponse,
)

private data class StateResponse(
    val data: DataResponse,
)

private data class DataResponse(
    val entity: EntityResponse,
)

private data class EntityResponse(
    val id: String,
    val title: String,
    val subtitle: String?,
    val coverArt: CoverArtResponse?,
    val trackList: List<EmbedTrackResponse>,
)

private data class CoverArtResponse(
    val sources: List<ImageSourceResponse>,
)

private data class ImageSourceResponse(
    val url: String,
)

private data class EmbedTrackResponse(
    val uri: String,
    val title: String,
    val subtitle: String?,
    val duration: Int,
)
