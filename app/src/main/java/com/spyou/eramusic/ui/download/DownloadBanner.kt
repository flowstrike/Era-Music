package com.spyou.eramusic.ui.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spyou.eramusic.data.download.DownloadProgress

@Composable
fun DownloadBanner(
    progress: DownloadProgress,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVisible = when (progress) {
        is DownloadProgress.Idle -> false
        is DownloadProgress.Complete -> true
        is DownloadProgress.Syncing -> true
        is DownloadProgress.Downloading -> true
        is DownloadProgress.Error -> true
    }

    AnimatedVisibility(visible = isVisible) {
        Surface(
            tonalElevation = 2.dp,
            modifier = modifier.fillMaxWidth(),
        ) {
            when (progress) {
                is DownloadProgress.Idle -> {}
                is DownloadProgress.Syncing -> SyncingContent(progress, onDismiss)
                is DownloadProgress.Downloading -> DownloadingContent(progress, onDismiss)
                is DownloadProgress.Complete -> CompleteContent(progress, onDismiss)
                is DownloadProgress.Error -> ErrorContent(progress, onDismiss, onStartDownload)
            }
        }
    }
}

@Composable
private fun SyncingContent(
    progress: DownloadProgress.Syncing,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = "Fetching playlist ${progress.playlistIndex}/${progress.totalPlaylists}...",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
        }
    }
}

@Composable
private fun DownloadingContent(
    progress: DownloadProgress.Downloading,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = "Downloading: ${progress.trackTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Playlist ${progress.playlistIndex}/${progress.totalPlaylists} · " +
                        "Track ${progress.trackIndex}/${progress.totalNewTracks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
            }
        }
        val fraction = if (progress.totalNewTracks > 0) {
            progress.trackIndex.toFloat() / progress.totalNewTracks
        } else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun CompleteContent(
    progress: DownloadProgress.Complete,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = "Download complete: ${progress.totalDownloaded} tracks",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (progress.totalFailed > 0) {
                Text(
                    text = "${progress.totalFailed} tracks failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
        }
    }
}

@Composable
private fun ErrorContent(
    progress: DownloadProgress.Error,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Download failed: ${progress.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FilledTonalButton(onClick = onRetry) {
            Text("Retry")
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
        }
    }
}

@Composable
fun DownloadPromptBanner(
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download playlists for offline use",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "3 playlists · ~250 MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onDownload) {
                Text("Download")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
            }
        }
    }
}
