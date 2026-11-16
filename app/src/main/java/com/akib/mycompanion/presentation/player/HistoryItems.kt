package com.akib.mycompanion.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.ui.components.DotIcon
import com.akib.mycompanion.ui.components.DotSymbol
import com.akib.mycompanion.ui.theme.CompanionRed
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Played in My Companion · stored on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (history.isNotEmpty()) TextButton(onClick = actions::onClearHistory) { Text("Clear history") }
        }
    }
    if (history.isEmpty()) {
        item { EmptyState("No history yet", "Videos appear here after you listen for five seconds.") }
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
            item(key = "history-group-$group") { SectionLabel(group, Modifier.padding(start = 4.dp, top = 4.dp)) }
            previousGroup = group
        }
        item(key = "history-${entry.video.id}") { HistoryRow(entry, recent = group != "Earlier", playlists, actions) }
    }
}

/** A played video: artwork with a red line for how far you got, when, and Replay / Resume. */
@Composable
private fun HistoryRow(
    entry: PlaybackHistoryEntry,
    recent: Boolean,
    playlists: List<Playlist>,
    actions: PlayerActions
) {
    val video = entry.video
    val canResume = entry.positionMs > 0 && entry.durationMs > 0L && entry.positionMs < entry.durationMs - 1_000
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { actions.onReplayHistory(entry, false) }
            .padding(start = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(12.dp))) {
                Thumbnail(video.thumbnailUrl, Modifier.matchParentSize(), shape = RoundedCornerShape(12.dp))
                if (entry.durationMs > 0) {
                    val progress = (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .35f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(CompanionRed),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val played = Instant.ofEpochMilli(entry.lastPlayedAt).atZone(ZoneId.systemDefault()).let {
                    if (recent) {
                        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(it)
                    } else {
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(it)
                    }
                }
                Text(
                    "${historyPosition(entry.positionMs)} / ${historyPosition(entry.durationMs)} · $played",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            HistoryMenu(entry, playlists, actions)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryPill("Replay") { actions.onReplayHistory(entry, false) }
            if (canResume) HistoryPill("Resume", accent = true) { actions.onReplayHistory(entry, true) }
        }
    }
}

@Composable
private fun HistoryPill(label: String, accent: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(36.dp),
    ) {
        DotIcon(
            DotSymbol.PLAY,
            Modifier.size(12.dp),
            color = if (accent) CompanionRed else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HistoryMenu(entry: PlaybackHistoryEntry, playlists: List<Playlist>, actions: PlayerActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.MoreVert, "History actions for ${entry.video.title}")
        }
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

private fun historyPosition(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
