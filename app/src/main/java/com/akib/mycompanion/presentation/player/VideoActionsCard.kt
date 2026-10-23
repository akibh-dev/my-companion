package com.akib.mycompanion.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akib.mycompanion.R
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.Video
import java.util.Locale

/** Video found from a link: Play now, Add to queue, or Add to a playlist (existing or new). */
@Composable
fun VideoActionsCard(
    video: Video,
    playlists: List<Playlist>,
    newPlaylistError: String?,
    onPlayNow: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onNewPlaylistNameChange: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosingPlaylist by remember(video.id) { mutableStateOf(false) }
    var newName by remember(video.id) { mutableStateOf("") }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VideoRow(video)

        if (!choosingPlaylist) {
            Button(onClick = onPlayNow, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play now")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAddToQueue, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_queue_music), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add to queue")
                }
                FilledTonalButton(onClick = { choosingPlaylist = true }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add to playlist")
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        } else {
            Text("Add to playlist", style = MaterialTheme.typography.titleSmall)
            playlists.forEach { playlist ->
                OutlinedButton(onClick = { onAddToPlaylist(playlist.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(playlist.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${playlist.itemCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = newName,
                onValueChange = {
                    newName = it
                    onNewPlaylistNameChange()
                },
                label = { Text("New playlist name") },
                singleLine = true,
                isError = newPlaylistError != null,
                supportingText = newPlaylistError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = { choosingPlaylist = false }) { Text("Back") }
                Button(onClick = { onCreatePlaylistAndAdd(newName) }, enabled = newName.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Create & add")
                }
            }
        }
    }
}

@Composable
fun VideoRow(video: Video, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Thumbnail(video.thumbnailUrl, Modifier.size(width = 88.dp, height = 50.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOf(video.author, formatSeconds(video.durationSeconds)).filter { it.isNotBlank() }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** 0 → "", 65 → "1:05", 3725 → "1:02:05". */
fun formatSeconds(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

/** Like [formatSeconds] but "0:00" for zero, for the seek bar. */
fun formatMillis(millis: Long): String = formatSeconds(millis / 1000).ifEmpty { "0:00" }

/** "12 videos · 48 min" / "1 video" / "Empty". */
fun playlistSummary(playlist: Playlist): String {
    if (playlist.itemCount == 0) return "Empty"
    val count = if (playlist.itemCount == 1) "1 video" else "${playlist.itemCount} videos"
    val minutes = playlist.totalSeconds / 60
    val length = when {
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        minutes > 0 -> "$minutes min"
        else -> ""
    }
    return listOf(count, length).filter { it.isNotEmpty() }.joinToString(" · ")
}
