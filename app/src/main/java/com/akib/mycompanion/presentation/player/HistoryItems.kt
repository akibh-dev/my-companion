package com.akib.mycompanion.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.model.Playlist
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun LazyListScope.historyItems(
    history: List<PlaybackHistoryEntry>,
    playlists: List<Playlist>,
    actions: PlayerActions,
) {
    item {
        Row(Modifier.fillMaxWidth()) {
            Text("Listening history", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) TextButton(onClick = actions::onClearHistory) { Text("Clear history") }
        }
        Text("Played in My Companion · stored on this phone", style = MaterialTheme.typography.bodySmall)
    }
    if (history.isEmpty()) {
        item { Text("No history yet. Videos appear after you listen for five seconds.") }
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var previousGroup: String? = null
    history.forEach { entry ->
        val date = Instant.ofEpochMilli(entry.lastPlayedAt).atZone(zone).toLocalDate()
        val group = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> "Earlier"
        }
        if (group != previousGroup) {
            item(key = "history-group-$group") { Text(group, style = MaterialTheme.typography.titleSmall) }
            previousGroup = group
        }
        item(key = "history-${entry.video.id}") { HistoryRow(entry, playlists, actions) }
    }
}

@Composable
private fun HistoryRow(entry: PlaybackHistoryEntry, playlists: List<Playlist>, actions: PlayerActions) {
    ElevatedCard(Modifier.fillMaxWidth().clickable { actions.onReplayHistory(entry, false) }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoRow(entry.video)
            val played = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .format(Instant.ofEpochMilli(entry.lastPlayedAt).atZone(ZoneId.systemDefault()))
            Text("Last played $played", style = MaterialTheme.typography.bodySmall)
            Text(
                "${historyPosition(entry.positionMs)} / ${historyPosition(entry.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                TextButton(onClick = { actions.onReplayHistory(entry, false) }) { Text("Replay") }
                if (entry.positionMs > 0 && (entry.durationMs > 0L && entry.positionMs < entry.durationMs - 1_000)) {
                    TextButton(onClick = { actions.onReplayHistory(entry, true) }) { Text("Resume") }
                }
                var menu by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { menu = true }
                ) { Icon(Icons.Default.MoreVert, "History actions for ${entry.video.title}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                        menu = false
                        actions.onQueueHistory(entry)
                    })
                    playlists.forEach { playlist ->
                        DropdownMenuItem(text = { Text("Add to ${playlist.name}") }, onClick = {
                            menu = false
                            actions.onPlaylistHistory(entry, playlist.id)
                        })
                    }
                    if (playlists.isEmpty()) {
                        DropdownMenuItem(text = { Text("Create playlist") }, onClick = {
                            menu = false
                            actions.onNewPlaylist()
                        })
                    }
                    DropdownMenuItem(text = { Text("Remove from history") }, onClick = {
                        menu = false
                        actions.onRemoveHistory(entry)
                    })
                }
            }
        }
    }
}

private fun historyPosition(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
