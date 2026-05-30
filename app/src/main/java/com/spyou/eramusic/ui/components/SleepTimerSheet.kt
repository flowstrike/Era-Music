package com.spyou.eramusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spyou.eramusic.ui.formatDuration

/** Bottom sheet for choosing a sleep-timer duration, or cancelling a running one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long,
    onPick: (minutes: Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            if (remainingMs > 0) {
                ListItem(
                    headlineContent = { Text("Stopping in ${formatDuration(remainingMs)}") },
                    supportingContent = { Text("Tap to cancel") },
                    leadingContent = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCancel() },
                )
            }
            listOf(15, 30, 45, 60).forEach { minutes ->
                ListItem(
                    headlineContent = { Text("$minutes minutes") },
                    leadingContent = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(minutes) },
                )
            }
        }
    }
}
