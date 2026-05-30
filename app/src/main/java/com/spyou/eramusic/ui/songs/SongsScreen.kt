package com.spyou.eramusic.ui.songs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.data.SortOrder
import com.spyou.eramusic.ui.components.AddToPlaylistSheet
import com.spyou.eramusic.ui.components.RowAction
import com.spyou.eramusic.ui.components.SongRow
import com.spyou.eramusic.ui.playlists.PlaylistsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    libraryViewModel: LibraryViewModel,
    playlistsViewModel: PlaylistsViewModel,
    currentSongId: Long?,
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
                        Text("Songs")
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
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.id == currentSongId,
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
