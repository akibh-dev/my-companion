package com.akib.mycompanion.presentation.player

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.presentation.common.ObserveAsEvents
import kotlinx.coroutines.Dispatchers

/** Which name/confirm dialog is open. */
private sealed interface PlayerDialog {
    data object NewPlaylist : PlayerDialog

    data object SaveQueue : PlayerDialog

    data class Rename(val playlist: Playlist) : PlayerDialog

    data class Delete(val playlist: Playlist) : PlayerDialog
}

@Composable
fun PlayerRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    linkViewModel: VideoActionsViewModel = hiltViewModel(),
) {
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    // Main.immediate: the typed link must come back to the TextField in the same frame. With the default
    // (next frame) the field briefly gets an older value back and fast typing drops or reorders letters.
    val link by linkViewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val clipboard = LocalClipboardManager.current
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }

    ObserveAsEvents(playerViewModel.events) { event ->
        when (event) {
            is PlayerEvent.Message -> snackbarHostState.showSnackbar(event.text)
        }
    }
    ObserveAsEvents(linkViewModel.events) { event ->
        when (event) {
            is VideoActionsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            is VideoActionsEvent.Done -> Unit // already on the player
        }
    }

    val actions = remember(playerViewModel, linkViewModel) {
        object : PlayerActions {
            override fun onLinkChange(text: String) = linkViewModel.onLinkChange(text)

            override fun onPaste() {
                val text = clipboard.getText()?.text.orEmpty()
                if (text.isNotBlank()) linkViewModel.onSubmit(text)
            }

            override fun onSubmitLink() = linkViewModel.onSubmit()

            override fun onPlayNow() = linkViewModel.onPlayNow()

            override fun onAddToQueue() = linkViewModel.onAddToQueue()

            override fun onAddToPlaylist(playlistId: Long) = linkViewModel.onAddToPlaylist(playlistId)

            override fun onCreatePlaylistAndAdd(name: String) = linkViewModel.onCreatePlaylistAndAdd(name)

            override fun onNewPlaylistNameChange() = linkViewModel.onNewPlaylistNameChange()

            override fun onDismissLink() = linkViewModel.onDismiss()

            override fun onTogglePlay() = playerViewModel.onTogglePlay()

            override fun onNext() = playerViewModel.onNext()

            override fun onPrevious() = playerViewModel.onPrevious()

            override fun onSeek(positionMs: Long) = playerViewModel.onSeek(positionMs)

            override fun onCycleRepeat() = playerViewModel.onCycleRepeat()

            override fun onToggleShuffle() = playerViewModel.onToggleShuffle()

            override fun onSkipTo(index: Int) = playerViewModel.onSkipTo(index)

            override fun onRemoveFromQueue(index: Int) = playerViewModel.onRemoveFromQueue(index)

            override fun onSaveQueue() {
                dialog = PlayerDialog.SaveQueue
            }

            override fun onSectionChange(section: PlayerSection) = playerViewModel.onSectionChange(section)

            override fun onNewPlaylist() {
                dialog = PlayerDialog.NewPlaylist
            }

            override fun onOpenPlaylist(playlistId: Long) = playerViewModel.onOpenPlaylist(playlistId)

            override fun onClosePlaylist() = playerViewModel.onClosePlaylist()

            override fun onPlayPlaylist(playlistId: Long, startIndex: Int, shuffle: Boolean) =
                playerViewModel.onPlayPlaylist(playlistId, startIndex, shuffle)

            override fun onRemoveFromPlaylist(item: PlaylistItem) = playerViewModel.onRemoveFromPlaylist(item)

            override fun onRenamePlaylist(playlist: Playlist) {
                dialog = PlayerDialog.Rename(playlist)
            }

            override fun onDeletePlaylist(playlist: Playlist) {
                dialog = PlayerDialog.Delete(playlist)
            }
        }
    }

    PlayerScreen(state = state, link = link, actions = actions, modifier = modifier)

    when (val open = dialog) {
        null -> Unit
        PlayerDialog.NewPlaylist -> NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = playerViewModel::onCreatePlaylist,
            onDismiss = { dialog = null },
        )
        PlayerDialog.SaveQueue -> NameDialog(
            title = "Save queue as playlist",
            initial = "",
            confirm = "Save",
            onConfirm = playerViewModel::onSaveQueue,
            onDismiss = { dialog = null },
        )
        is PlayerDialog.Rename -> NameDialog(
            title = "Rename playlist",
            initial = open.playlist.name,
            confirm = "Rename",
            onConfirm = { playerViewModel.onRenamePlaylist(open.playlist.id, it) },
            onDismiss = { dialog = null },
        )
        is PlayerDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${open.playlist.name}?") },
            text = { Text("The playlist and its list of videos will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    playerViewModel.onDeletePlaylist(open.playlist)
                    dialog = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val submit = {
        if (name.isNotBlank()) {
            onConfirm(name)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
