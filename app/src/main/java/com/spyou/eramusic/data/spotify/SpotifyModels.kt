package com.spyou.eramusic.data.spotify

import com.google.gson.annotations.SerializedName

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
)

data class SpotifyPlaylistInfo(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val tracks: List<SpotifyTrack>,
)

internal data class PlaylistResponse(
    val id: String,
    val name: String,
    val description: String?,
    val images: List<ImageResponse>,
    val tracks: PlaylistTracksSummary,
)

internal data class PlaylistTracksSummary(val total: Int)

internal data class ImageResponse(val url: String)

internal data class TracksPageResponse(val items: List<TrackItemResponse>)

internal data class TrackItemResponse(val track: TrackDataResponse?)

internal data class TrackDataResponse(
    val id: String,
    val name: String,
    @SerializedName("duration_ms") val durationMs: Long,
    val album: AlbumResponse,
    val artists: List<ArtistResponse>,
)

internal data class AlbumResponse(val name: String, val images: List<ImageResponse>)

internal data class ArtistResponse(val name: String)
