package com.spyou.eramusic.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.data.db.DownloadDao
import com.spyou.eramusic.data.playable.DownloadedTrack
import com.spyou.eramusic.playback.PlayerConnection
import com.spyou.eramusic.ui.components.SongRow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedPlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    downloadDao: DownloadDao,
    playerConnection: PlayerConnection,
    currentSongId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trackIds by remember(playlistId) {
        downloadDao.observeTrackIdsForPlaylist(playlistId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val tracks = remember(trackIds) {
        trackIds.mapNotNull { id ->
            val entity = runCatching {
                runBlocking { downloadDao.track(id) }
            }.getOrNull()
            entity?.let {
                DownloadedTrack(
                    spotifyId = it.spotifyId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    durationMs = it.durationMs,
                    artworkUrl = it.artworkUrl,
                    localFile = File(context.filesDir, it.localFilePath),
                )
            }
        }
    }

    val favoriteIds by remember {
        downloadDao.observeFavoriteTrackIds()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tracks.isNotEmpty()) {
                        IconButton(onClick = { playerConnection.setDownloadedQueue(tracks, 0) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tracks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No tracks downloaded yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                itemsIndexed(
                    tracks,
                    key = { _, track -> track.spotifyId },
                ) { index, track ->
                    SongRow(
                        song = track,
                        isCurrent = track.spotifyId == currentSongId,
                        isFavorite = track.spotifyId in favoriteIds,
                        onClick = { playerConnection.setDownloadedQueue(tracks, index) },
                        onToggleFavorite = {
                            scope.launch {
                                downloadDao.toggleFavorite(track.spotifyId)
                            }
                        },
                    )
                }
            }
        }
    }
}
