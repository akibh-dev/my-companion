package com.akib.mycompanion.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.R
import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.RepeatMode
import com.akib.mycompanion.presentation.common.OutlinedStateTextField

/** Everything the Player screen can ask for; the route forwards these to the ViewModels. */
interface PlayerActions {
    /** The user typed in the link box (the text itself is already in the link TextFieldState). */
    fun onLinkEdited()

    fun onPaste()

    fun onSubmitLink()

    fun onPlayNow()

    fun onAddToQueue()

    fun onAddToPlaylist(playlistId: Long)

    fun onCreatePlaylistAndAdd(name: String)

    fun onNewPlaylistNameChange()

    fun onDismissLink()

    fun onTogglePlay()

    fun onNext()

    fun onPrevious()

    fun onSeek(positionMs: Long)

    fun onCycleRepeat()

    fun onToggleShuffle()

    fun onSkipTo(index: Int)

    fun onRemoveFromQueue(index: Int)

    fun onSaveQueue()

    fun onSectionChange(section: PlayerSection)

    fun onNewPlaylist()

    fun onOpenPlaylist(playlistId: Long)

    fun onClosePlaylist()

    fun onPlayPlaylist(playlistId: Long, startIndex: Int, shuffle: Boolean)

    fun onRemoveFromPlaylist(item: PlaylistItem)

    fun onRenamePlaylist(playlist: Playlist)

    fun onDeletePlaylist(playlist: Playlist)
}

/** Player tab: link box ([linkText] is its Compose text state), now playing, then Up next / Playlists. */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    link: VideoActionsUiState,
    linkText: TextFieldState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LinkBox(link, linkText, actions) }

        state.playback.current?.let {
            item { NowPlaying(state.playback, actions) }
        }

        item {
            TabRow(selectedTabIndex = state.section.ordinal) {
                Tab(
                    selected = state.section == PlayerSection.QUEUE,
                    onClick = { actions.onSectionChange(PlayerSection.QUEUE) },
                    text = { Text("Up next") },
                )
                Tab(
                    selected = state.section == PlayerSection.PLAYLISTS,
                    onClick = { actions.onSectionChange(PlayerSection.PLAYLISTS) },
                    text = { Text("Playlists") },
                )
            }
        }

        when {
            state.section == PlayerSection.QUEUE -> queueItems(state.playback, actions)
            state.openPlaylist != null -> playlistDetail(state.openPlaylist, actions)
            else -> playlistItems(state.playlists, actions)
        }
    }
}

@Composable
private fun LinkBox(link: VideoActionsUiState, linkText: TextFieldState, actions: PlayerActions) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val status = link.status) {
                is LinkStatus.Ready -> VideoActionsCard(
                    video = status.video,
                    playlists = link.playlists,
                    newPlaylistError = link.newPlaylistError,
                    onPlayNow = actions::onPlayNow,
                    onAddToQueue = actions::onAddToQueue,
                    onAddToPlaylist = actions::onAddToPlaylist,
                    onCreatePlaylistAndAdd = actions::onCreatePlaylistAndAdd,
                    onNewPlaylistNameChange = actions::onNewPlaylistNameChange,
                    onDismiss = actions::onDismissLink,
                )
                else -> {
                    OutlinedStateTextField(
                        state = linkText,
                        label = "YouTube link",
                        placeholder = "https://youtu.be/…",
                        isError = status is LinkStatus.Error,
                        supportingText = (status as? LinkStatus.Error)?.message
                            ?: "Or share a video from the YouTube app to My Companion",
                        trailingIcon = {
                            IconButton(onClick = actions::onPaste) {
                                Icon(painterResource(R.drawable.ic_content_paste), contentDescription = "Paste link")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        onImeAction = actions::onSubmitLink,
                        onUserEdit = actions::onLinkEdited,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = actions::onSubmitLink,
                        enabled = linkText.text.isNotBlank() && status != LinkStatus.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (status == LinkStatus.Loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Getting video…")
                        } else {
                            Text("Open link")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(playback: PlaybackState, actions: PlayerActions) {
    val video = playback.current ?: return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Thumbnail(
                video.thumbnailUrl,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Text(
                video.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.author.isNotBlank()) {
                Text(
                    video.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            playback.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            SeekBar(playback, actions)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(
                    icon = R.drawable.ic_shuffle,
                    description = if (playback.shuffle) "Shuffle on" else "Shuffle off",
                    active = playback.shuffle,
                    onClick = actions::onToggleShuffle,
                )
                IconButton(onClick = actions::onPrevious) {
                    Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous")
                }
                FilledIconButton(onClick = actions::onTogglePlay, modifier = Modifier.size(64.dp)) {
                    when {
                        playback.isBuffering && !playback.isPlaying ->
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        playback.isPlaying ->
                            Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause")
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(onClick = actions::onNext) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next")
                }
                ToggleIcon(
                    icon = if (playback.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                    description = when (playback.repeat) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    active = playback.repeat != RepeatMode.OFF,
                    onClick = actions::onCycleRepeat,
                )
            }
        }
    }
}

@Composable
private fun SeekBar(playback: PlaybackState, actions: PlayerActions) {
    val duration = playback.durationMs.coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else playback.positionMs.toFloat()

    Slider(
        value = shown.coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            actions.onSeek(dragValue.toLong())
            dragging = false
        },
        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
        enabled = duration > 0,
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatMillis(shown.toLong()), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(formatMillis(duration), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ToggleIcon(icon: Int, description: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = color)) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

private fun LazyListScope.queueItems(playback: PlaybackState, actions: PlayerActions) {
    if (playback.queue.isEmpty()) {
        item {
            EmptyText("Nothing in the queue. Share a video from YouTube, or paste a link above.")
        }
        return
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (playback.queue.size == 1) "1 video" else "${playback.queue.size} videos",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions::onSaveQueue) {
                Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save as playlist")
            }
        }
    }
    itemsIndexed(playback.queue, key = { index, video -> "$index-${video.id}" }) { index, video ->
        val isCurrent = index == playback.currentIndex
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { actions.onSkipTo(index) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoRow(video, Modifier.weight(1f), highlighted = isCurrent)
            IconButton(onClick = { actions.onRemoveFromQueue(index) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${video.title} from queue")
            }
        }
    }
}

private fun LazyListScope.playlistItems(playlists: List<Playlist>, actions: PlayerActions) {
    item {
        FilledTonalButton(onClick = actions::onNewPlaylist, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New playlist")
        }
    }
    if (playlists.isEmpty()) {
        item { EmptyText("No playlists yet. Create one, or add a video to a new playlist when you share it.") }
        return
    }
    playlists.forEach { playlist ->
        item(key = "playlist-${playlist.id}") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.onOpenPlaylist(playlist.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_queue_music), contentDescription = null)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        playlistSummary(playlist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { actions.onPlayPlaylist(playlist.id, 0, false) },
                    enabled = playlist.itemCount > 0,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${playlist.name}")
                }
            }
        }
    }
}

private fun LazyListScope.playlistDetail(open: OpenPlaylist, actions: PlayerActions) {
    val playlist = open.playlist
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::onClosePlaylist) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
            }
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                Text(playlistSummary(playlist), style = MaterialTheme.typography.bodySmall)
            }
            PlaylistMenu(playlist, actions)
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.onPlayPlaylist(playlist.id, 0, false) },
                enabled = open.items.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play all")
            }
            FilledTonalButton(
                onClick = { actions.onPlayPlaylist(playlist.id, 0, true) },
                enabled = open.items.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Shuffle")
            }
        }
    }
    if (open.items.isEmpty()) {
        item { EmptyText("Empty. Share a YouTube video to My Companion and choose this playlist.") }
        return
    }
    itemsIndexed(open.items, key = { _, item -> "item-${item.id}" }) { index, item ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { actions.onPlayPlaylist(playlist.id, index, false) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoRow(item.video, Modifier.weight(1f))
            IconButton(onClick = { actions.onRemoveFromPlaylist(item) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${item.video.title} from playlist")
            }
        }
    }
}

@Composable
private fun PlaylistMenu(playlist: Playlist, actions: PlayerActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    open = false
                    actions.onRenamePlaylist(playlist)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete playlist") },
                onClick = {
                    open = false
                    actions.onDeletePlaylist(playlist)
                },
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}
