package com.spyou.eramusic.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.ui.components.NameDialog
import com.spyou.eramusic.ui.components.RowAction
import com.spyou.eramusic.ui.components.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistsViewModel,
    currentSongId: String?,
    onBack: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val songsFlow = remember(playlistId) { viewModel.songsIn(playlistId) }
    val songs by songsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val editable = playlist?.isFavorites == false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (songs.isNotEmpty()) {
                        IconButton(onClick = { viewModel.play(songs, 0) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all")
                        }
                    }
                    if (editable) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = "No songs yet.\nAdd songs from the Songs tab.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id.toString() == currentSongId,
                        onClick = { viewModel.play(songs, index) },
                        isFavorite = song.id in favoriteIds,
                        onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                        menuActions = buildList {
                            if (editable) {
                                add(
                                    RowAction("Move up", Icons.Rounded.KeyboardArrowUp, enabled = index > 0) {
                                        viewModel.move(playlistId, index, index - 1)
                                    },
                                )
                                add(
                                    RowAction("Move down", Icons.Rounded.KeyboardArrowDown, enabled = index < songs.lastIndex) {
                                        viewModel.move(playlistId, index, index + 1)
                                    },
                                )
                            }
                            add(
                                RowAction("Remove from playlist", Icons.Rounded.RemoveCircleOutline) {
                                    viewModel.removeFromPlaylist(playlistId, song.id)
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (showRename && playlist != null) {
        NameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialValue = playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                viewModel.rename(playlistId, name)
                showRename = false
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"${playlist?.name}\" will be removed. Your audio files are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(playlistId)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}
