package com.spyou.eramusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spyou.eramusic.data.db.PlaylistWithCount

/** Bottom sheet to add a song to an existing playlist or create a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<PlaylistWithCount>,
    onDismiss: () -> Unit,
    onPick: (playlistId: Long) -> Unit,
    onCreateAndAdd: (name: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Add to playlist",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            ListItem(
                headlineContent = { Text("New playlist") },
                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                modifier = Modifier.clickable { showCreate = true },
            )
            playlists.filterNot { it.isFavorites }.forEach { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("${playlist.songCount} songs") },
                    leadingContent = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                    modifier = Modifier.clickable { onPick(playlist.id) },
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                onCreateAndAdd(name)
            },
        )
    }
}
