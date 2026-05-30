package com.spyou.eramusic.data.spotify

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpotifyMetadataService(
    private val authService: SpotifyAuthService,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetchPlaylist(playlistId: String): SpotifyPlaylistInfo =
        withContext(Dispatchers.IO) {
            val token = authService.getAccessToken()

            val playlistRequest = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId?fields=id,name,description,images,tracks.total")
                .header("Authorization", "Bearer $token")
                .build()
            val playlistJson = client.newCall(playlistRequest).execute()
                .body?.string() ?: error("Empty playlist response")
            val playlistData = gson.fromJson(playlistJson, PlaylistResponse::class.java)

            val tracks = fetchAllTracks(playlistId, token, playlistData.tracks.total)

            SpotifyPlaylistInfo(
                id = playlistData.id,
                name = playlistData.name,
                description = playlistData.description,
                artworkUrl = playlistData.images.firstOrNull()?.url,
                tracks = tracks,
            )
        }

    private fun fetchAllTracks(
        playlistId: String,
        token: String,
        total: Int,
    ): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 100
        do {
            val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks" +
                "?offset=$offset&limit=$limit" +
                "&fields=items(track(id,name,duration_ms,album(name,images),artists(name)))"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val json = client.newCall(request).execute()
                .body?.string() ?: break
            val page = gson.fromJson(json, TracksPageResponse::class.java)
            for (item in page.items) {
                val track = item.track ?: continue
                tracks.add(
                    SpotifyTrack(
                        id = track.id,
                        title = track.name,
                        artist = track.artists.firstOrNull()?.name ?: "Unknown",
                        album = track.album.name,
                        durationMs = track.durationMs,
                        artworkUrl = track.album.images.firstOrNull()?.url,
                    )
                )
            }
            offset += limit
        } while (page.items.size == limit && offset < total)
        return tracks
    }
}
