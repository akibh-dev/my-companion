package com.akib.mycompanion.presentation.player

import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akib.mycompanion.R
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.RepeatMode
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.presentation.common.OutlinedStateTextField
import com.akib.mycompanion.ui.components.DotIcon
import com.akib.mycompanion.ui.components.DotSymbol
import com.akib.mycompanion.ui.components.rememberChromeListState
import com.akib.mycompanion.ui.theme.CompanionRed
import java.util.Locale

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

    fun onReplayHistory(entry: PlaybackHistoryEntry, resume: Boolean) = Unit
    fun onQueueHistory(entry: PlaybackHistoryEntry) = Unit
    fun onPlaylistHistory(entry: PlaybackHistoryEntry, playlistId: Long) = Unit
    fun onRemoveHistory(entry: PlaybackHistoryEntry) = Unit
    fun onClearHistory() = Unit
}

/**
 * Player tab, in the app's dotted monochrome style: a link bar, a black "now playing" card with a
 * dotted seek bar, then Up next / Playlists / History. [linkText] is the link box's Compose text state.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    link: VideoActionsUiState,
    linkText: TextFieldState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = rememberChromeListState(),
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "A soundtrack for your day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { LinkBox(link, linkText, actions) }

        state.playback.current?.let {
            item { NowPlaying(state.playback, actions) }
        }

        item { SectionTabs(state.section, actions) }

        when {
            state.section == PlayerSection.QUEUE -> queueItems(state.playback, actions)
            state.section == PlayerSection.HISTORY -> historyItems(state.history, state.playlists, actions)
            state.openPlaylist != null -> playlistDetail(state.openPlaylist, actions)
            else -> playlistItems(state.playlists, actions)
        }
    }
}

@Composable
private fun LinkBox(link: VideoActionsUiState, linkText: TextFieldState, actions: PlayerActions) {
    when (val status = link.status) {
        is LinkStatus.Ready -> Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        ) {
            VideoActionsCard(
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
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedStateTextField(
                state = linkText,
                label = "YouTube link",
                placeholder = "https://youtu.be/…",
                isError = status is LinkStatus.Error,
                supportingText = (status as? LinkStatus.Error)?.message,
                trailingIcon = {
                    IconButton(onClick = actions::onPaste) {
                        Icon(painterResource(R.drawable.ic_content_paste), contentDescription = "Paste link")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                onImeAction = actions::onSubmitLink,
                onUserEdit = actions::onLinkEdited,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Or share a video from the YouTube app to My Companion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, end = 12.dp),
                )
                Button(
                    onClick = actions::onSubmitLink,
                    enabled = linkText.text.isNotBlank() && status != LinkStatus.Loading,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    if (status == LinkStatus.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
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

/** The black hero card: artwork, title, a dotted seek bar and dotted transport controls. */
@Composable
private fun NowPlaying(playback: PlaybackState, actions: PlayerActions) {
    val video = playback.current ?: return
    val colors = MaterialTheme.colorScheme
    val content = colors.onPrimary
    val muted = content.copy(alpha = .62f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.primary)
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(active = playback.isPlaying)
            Text(
                if (playback.isPlaying) "NOW PLAYING" else "PAUSED",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = muted,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
            if (playback.queue.size > 1) {
                Text(
                    "${playback.currentIndex + 1} / ${playback.queue.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                )
            }
        }
        Thumbnail(
            video.thumbnailUrl,
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleLarge,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.author.isNotBlank()) {
                Text(video.author, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        playback.error?.let { Text(it, color = CompanionRed, style = MaterialTheme.typography.bodySmall) }

        DotSeekBar(playback, actions::onSeek, color = content)

        CompositionLocalProvider(LocalContentColor provides content) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(
                    icon = R.drawable.ic_shuffle,
                    description = if (playback.shuffle) "Shuffle on" else "Shuffle off",
                    active = playback.shuffle,
                    idle = muted,
                    onClick = actions::onToggleShuffle,
                )
                IconButton(onClick = actions::onPrevious, modifier = Modifier.size(52.dp)) {
                    DotIcon(DotSymbol.PREVIOUS, Modifier.size(26.dp).semantics { contentDescription = "Previous" })
                }
                PlayButton(playback, actions::onTogglePlay, background = content, foreground = colors.primary)
                IconButton(onClick = actions::onNext, modifier = Modifier.size(52.dp)) {
                    DotIcon(DotSymbol.NEXT, Modifier.size(26.dp).semantics { contentDescription = "Next" })
                }
                ToggleIcon(
                    icon = if (playback.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
                    description = when (playback.repeat) {
                        RepeatMode.OFF -> "Repeat off"
                        RepeatMode.ALL -> "Repeat all"
                        RepeatMode.ONE -> "Repeat one"
                    },
                    active = playback.repeat != RepeatMode.OFF,
                    idle = muted,
                    onClick = actions::onCycleRepeat,
                )
            }
        }
    }
}

/** A red dot that breathes while music plays and rests while paused. */
@Composable
private fun LiveDot(active: Boolean) {
    val pulse = rememberInfiniteTransition(label = "Now playing pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = .3f,
        animationSpec = infiniteRepeatable(tween(900), AnimationRepeatMode.Reverse),
        label = "Now playing dot",
    )
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(CompanionRed.copy(alpha = if (active) alpha else .45f)),
    )
}

@Composable
private fun PlayButton(playback: PlaybackState, onClick: () -> Unit, background: Color, foreground: Color) {
    val label = if (playback.isPlaying) "Pause" else "Play"
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            playback.isBuffering && !playback.isPlaying ->
                CircularProgressIndicator(Modifier.size(28.dp), color = foreground, strokeWidth = 3.dp)
            playback.isPlaying -> DotIcon(DotSymbol.PAUSE, Modifier.size(36.dp), color = foreground)
            // The play triangle sits a touch right of centre, so it looks centred.
            else -> DotIcon(DotSymbol.PLAY, Modifier.size(38.dp).padding(start = 4.dp), color = foreground)
        }
    }
}

/**
 * A row of dots instead of a slider: dots fill in as the track plays, with a red playhead. Tap or drag
 * to seek. Screen readers get it as an adjustable progress bar.
 */
@Composable
private fun DotSeekBar(playback: PlaybackState, onSeek: (Long) -> Unit, color: Color) {
    val duration = playback.durationMs.coerceAtLeast(0)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playing = if (duration > 0) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: playing
    val seek by rememberUpdatedState(onSeek)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .then(
                    if (duration > 0) {
                        Modifier
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    seek((offset.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                                }
                            }
                            .pointerInput(duration) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                                    onDragEnd = {
                                        dragFraction?.let { seek((it * duration).toLong()) }
                                        dragFraction = null
                                    },
                                    onDragCancel = { dragFraction = null },
                                ) { change, _ ->
                                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    contentDescription = "Seek"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    if (duration > 0) {
                        setProgress { value ->
                            seek((value.coerceIn(0f, 1f) * duration).toLong())
                            true
                        }
                    }
                },
        ) {
            val gap = 7.dp.toPx()
            val dot = 1.6.dp.toPx()
            val y = size.height / 2
            val count = (size.width / gap).toInt()
            val step = if (count > 0) size.width / count else size.width
            val head = fraction * size.width
            for (i in 0..count) {
                val x = (i * step).coerceIn(dot, size.width - dot)
                drawCircle(if (x <= head) color else color.copy(alpha = .22f), dot, Offset(x, y))
            }
            drawCircle(CompanionRed, 6.dp.toPx(), Offset(head.coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), y))
        }
        Row(Modifier.fillMaxWidth()) {
            val muted = color.copy(alpha = .62f)
            Text(
                formatMillis((fraction * duration).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = muted
            )
            Spacer(Modifier.weight(1f))
            Text(formatMillis(duration), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

@Composable
private fun ToggleIcon(icon: Int, description: String, active: Boolean, idle: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = description, tint = if (active) CompanionRed else idle)
    }
}

@Composable
private fun SectionTabs(current: PlayerSection, actions: PlayerActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectableGroup()
            .padding(4.dp),
    ) {
        PlayerSection.entries.forEach { section ->
            val selected = current == section
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected, role = Role.Tab, onClick = { actions.onSectionChange(section) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (section) {
                        PlayerSection.QUEUE -> "Up next"
                        PlayerSection.PLAYLISTS -> "Playlists"
                        PlayerSection.HISTORY -> "History"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = with(MaterialTheme.colorScheme) { if (selected) onPrimary else onSurfaceVariant },
                )
            }
        }
    }
}

/** Small spaced-out caps, used for list headings on this page. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Shown in caps, but screen readers (and tests) get the words as written, not spelled-out capitals.
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            heading()
        },
    )
}

private fun LazyListScope.queueItems(playback: PlaybackState, actions: PlayerActions) {
    if (playback.queue.isEmpty()) {
        item {
            EmptyState("Nothing in the queue", "Share a video from YouTube, or paste a link above.")
        }
        return
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                if (playback.queue.size == 1) "1 video" else "${playback.queue.size} videos",
                Modifier.weight(1f).padding(start = 4.dp),
            )
            TextButton(onClick = actions::onSaveQueue) {
                Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save as playlist")
            }
        }
    }
    itemsIndexed(playback.queue, key = { index, video -> "$index-${video.id}" }) { index, video ->
        TrackRow(
            index = index,
            video = video,
            current = index == playback.currentIndex,
            onClick = { actions.onSkipTo(index) },
            removeLabel = "Remove ${video.title} from queue",
            onRemove = { actions.onRemoveFromQueue(index) },
        )
    }
}

/** One numbered track: square artwork, title and details. The current one gets a red dotted marker. */
@Composable
private fun TrackRow(
    index: Int,
    video: Video,
    current: Boolean,
    onClick: () -> Unit,
    removeLabel: String,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (current) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            if (current) {
                DotIcon(DotSymbol.PLAY, Modifier.size(20.dp), color = CompanionRed)
            } else {
                Text(
                    "%02d".format(Locale.ROOT, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Thumbnail(video.thumbnailUrl, Modifier.padding(start = 6.dp).size(56.dp), shape = RoundedCornerShape(14.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
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
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = removeLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun LazyListScope.playlistItems(playlists: List<Playlist>, actions: PlayerActions) {
    // Tiles two to a row, like Home's feature tiles. "New playlist" is always the first tile.
    val tiles: List<Playlist?> = listOf(null) + playlists
    tiles.chunked(2).forEachIndexed { row, pair ->
        item(key = "playlist-row-$row-${pair.mapNotNull { it?.id }}") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { playlist ->
                    if (playlist == null) {
                        NewPlaylistTile(actions::onNewPlaylist, Modifier.weight(1f))
                    } else {
                        PlaylistTile(playlist, actions, Modifier.weight(1f))
                    }
                }
                if (pair.size == 1) {
                    // With no playlists yet, the empty slot next to "New playlist" explains how to fill it.
                    Box(Modifier.weight(1f).height(164.dp).padding(8.dp), contentAlignment = Alignment.CenterStart) {
                        if (playlists.isEmpty()) {
                            Text(
                                "No playlists yet. Make one here, or pick “Add to playlist” when you share a video.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(playlist: Playlist, actions: PlayerActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(164.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { actions.onOpenPlaylist(playlist.id) }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            DotIcon(DotSymbol.NOTE, Modifier.size(30.dp))
            Spacer(Modifier.weight(1f))
            val canPlay = playlist.itemCount > 0
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        with(MaterialTheme.colorScheme) { if (canPlay) primary else surfaceContainerHighest },
                    )
                    .clickable(enabled = canPlay) { actions.onPlayPlaylist(playlist.id, 0, false) }
                    .semantics {
                        contentDescription = "Play ${playlist.name}"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                DotIcon(
                    DotSymbol.PLAY,
                    Modifier.size(22.dp).padding(start = 2.dp),
                    color = with(MaterialTheme.colorScheme) { if (canPlay) onPrimary else onSurfaceVariant },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            playlistSummary(playlist),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** A dashed outline tile with a dotted plus: the empty slot you fill with a new playlist. */
@Composable
private fun NewPlaylistTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier
            .height(164.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = size.copy(width = size.width - stroke, height = size.height - stroke),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                )
            }
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotIcon(DotSymbol.PLUS, Modifier.size(30.dp))
        Spacer(Modifier.height(12.dp))
        Text("New playlist", style = MaterialTheme.typography.titleSmall)
    }
}

private fun LazyListScope.playlistDetail(open: OpenPlaylist, actions: PlayerActions) {
    val playlist = open.playlist
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::onClosePlaylist) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    playlistSummary(playlist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlaylistMenu(playlist, actions)
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { actions.onPlayPlaylist(playlist.id, 0, false) },
                enabled = open.items.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                DotIcon(DotSymbol.PLAY, Modifier.size(18.dp), color = LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text("Play all")
            }
            OutlinedButton(
                onClick = { actions.onPlayPlaylist(playlist.id, 0, true) },
                enabled = open.items.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
    if (open.items.isEmpty()) {
        item { EmptyState("This playlist is empty", "Share a YouTube video to My Companion and choose this playlist.") }
        return
    }
    itemsIndexed(open.items, key = { _, item -> "item-${item.id}" }) { index, item ->
        TrackRow(
            index = index,
            video = item.video,
            current = false,
            onClick = { actions.onPlayPlaylist(playlist.id, index, false) },
            removeLabel = "Remove ${item.video.title} from playlist",
            onRemove = { actions.onRemoveFromPlaylist(item) },
        )
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

/** A dotted note over a short title and a hint, for empty lists. */
@Composable
internal fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DotIcon(DotSymbol.NOTE, Modifier.size(44.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
