package com.spyou.eramusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.playback.PlayerConnection

/** Persistent compact player shown above the bottom navigation. Hidden when nothing is loaded. */
@Composable
fun MiniPlayer(
    player: PlayerConnection,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasItem by player.hasItem.collectAsStateWithLifecycle()
    if (!hasItem) return

    val title by player.title.collectAsStateWithLifecycle()
    val artist by player.artist.collectAsStateWithLifecycle()
    val artwork by player.artworkUri.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val position by player.positionMs.collectAsStateWithLifecycle()
    val duration by player.durationMs.collectAsStateWithLifecycle()

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .clickable(onClick = onExpand)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(uri = artwork, size = 44.dp, cornerRadius = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = { player.playPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                }
            }
            val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
