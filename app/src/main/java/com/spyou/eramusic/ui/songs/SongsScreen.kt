package com.spyou.eramusic.ui.songs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spyou.eramusic.data.SortOrder
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.data.db.PlaylistWithCount
import com.spyou.eramusic.data.db.SpotifyPlaylistEntity
import com.spyou.eramusic.data.quotes.Quote
import com.spyou.eramusic.ui.components.AddToPlaylistSheet
import com.spyou.eramusic.ui.components.QuoteCard
import com.spyou.eramusic.ui.components.RowAction
import com.spyou.eramusic.ui.components.SongRow
import com.spyou.eramusic.ui.playlists.PlaylistsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    currentSongId: String?,
    darkMode: SettingsStore.DarkMode,
    onDarkModeChange: (SettingsStore.DarkMode) -> Unit,
    syncedPlaylists: List<SpotifyPlaylistEntity>,
    localPlaylists: List<PlaylistWithCount>,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSyncedPlaylist: (String, String) -> Unit,
    quote: Quote?,
) {
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val query by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by libraryViewModel.sortOrder.collectAsStateWithLifecycle()
    val loaded by libraryViewModel.loaded.collectAsStateWithLifecycle()
    val favoriteIds by playlistsViewModel.favoriteIds.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()

    var searching by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var addToPlaylistSongId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { libraryViewModel.onQueryChange(it) },
                            placeholder = { Text("Search songs, artists, albums") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text("Era Music")
                    }
                },
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = {
                            searching = false
                            libraryViewModel.onQueryChange("")
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search")
                        }
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = {
                            val next = when (darkMode) {
                                SettingsStore.DarkMode.SYSTEM -> SettingsStore.DarkMode.DARK
                                SettingsStore.DarkMode.DARK -> SettingsStore.DarkMode.LIGHT
                                SettingsStore.DarkMode.LIGHT -> SettingsStore.DarkMode.SYSTEM
                            }
                            onDarkModeChange(next)
                        }) {
                            Icon(
                                imageVector = when (darkMode) {
                                    SettingsStore.DarkMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                                    SettingsStore.DarkMode.DARK -> Icons.Rounded.DarkMode
                                    SettingsStore.DarkMode.LIGHT -> Icons.Rounded.LightMode
                                },
                                contentDescription = "Theme",
                            )
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    trailingIcon = {
                                        if (order == sortOrder) Icon(Icons.Rounded.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        libraryViewModel.setSort(order)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                songs.isEmpty() -> EmptyMessage(
                    if (query.isBlank()) "No music found on this device" else "No results for \"$query\"",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                    if (!searching && quote != null) {
                        item { QuoteCard(quote) }
                    }
                    if (!searching) {
                        item {
                            PlaylistTilesRow(
                                syncedPlaylists = syncedPlaylists,
                                localPlaylists = localPlaylists,
                                onOpenPlaylist = onOpenPlaylist,
                                onOpenSyncedPlaylist = onOpenSyncedPlaylist,
                            )
                        }
                    }
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id.toString() == currentSongId,
                            onClick = { libraryViewModel.play(index) },
                            isFavorite = song.id in favoriteIds,
                            onToggleFavorite = { playlistsViewModel.toggleFavorite(song.id) },
                            menuActions = listOf(
                                RowAction("Add to playlist", Icons.Rounded.PlaylistAdd) {
                                    addToPlaylistSongId = song.id
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    val songId = addToPlaylistSongId
    if (songId != null) {
        AddToPlaylistSheet(
            playlists = playlists,
            onDismiss = { addToPlaylistSongId = null },
            onPick = { playlistId ->
                playlistsViewModel.addToPlaylist(playlistId, songId)
                addToPlaylistSongId = null
            },
            onCreateAndAdd = { name ->
                playlistsViewModel.createAndAdd(name, songId)
                addToPlaylistSongId = null
            },
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun PlaylistTilesRow(
    syncedPlaylists: List<SpotifyPlaylistEntity>,
    localPlaylists: List<PlaylistWithCount>,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSyncedPlaylist: (String, String) -> Unit,
) {
    val hasPlaylists = syncedPlaylists.isNotEmpty() || localPlaylists.isNotEmpty()
    if (!hasPlaylists) return

    Column {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(syncedPlaylists, key = { "synced_${it.spotifyId}" }) { playlist ->
                PlaylistTile(
                    name = playlist.name,
                    subtitle = "${playlist.trackCount} songs",
                    artworkUrl = playlist.artworkUrl,
                    isSynced = true,
                    onClick = { onOpenSyncedPlaylist(playlist.spotifyId, playlist.name) },
                )
            }
            items(localPlaylists, key = { "local_${it.id}" }) { playlist ->
                PlaylistTile(
                    name = playlist.name,
                    subtitle = "${playlist.songCount} songs",
                    artworkUrl = null,
                    isSynced = false,
                    isFavorite = playlist.isFavorites,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    name: String,
    subtitle: String,
    artworkUrl: String?,
    isSynced: Boolean,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
) {
    val tileWidth = 130.dp
    Column(
        modifier = Modifier
            .size(width = tileWidth, height = 175.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .size(tileWidth)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isSynced -> Icons.Rounded.CloudDone
                            isFavorite -> Icons.Rounded.Favorite
                            else -> Icons.Rounded.QueueMusic
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp),
        )
    }
}
