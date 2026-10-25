package com.akib.mycompanion.vault.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.SortOrder
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultState
import java.text.DateFormat
import java.util.Date

/** Vault: unlock, then items with search, sort and filters (plan §11, "items list"). */
@Composable
fun VaultRoute(
    onOpenItem: (Long) -> Unit,
    onOpenTrashAndBackup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The screen lock is set in system settings: look again when you come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    when (val vault = state.vault) {
        VaultState.NoScreenLock -> Centered(
            modifier,
            "Set a screen lock first",
            "The vault key is protected by your fingerprint or PIN, so the phone needs a screen lock.",
        ) {
            Button(onClick = { context.startActivity(screenLockSettings()) }) { Text("Set a screen lock") }
        }
        VaultState.NotSetUp, is VaultState.Locked -> {
            val firstTime = vault == VaultState.NotSetUp
            Column(
                modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (firstTime) "Your vault" else "Vault locked", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        firstTime ->
                            "Keep links, notes, images and files encrypted on this phone. " +
                                "Share things to \"Save to vault\" from any app."
                        (vault as VaultState.Locked).inbox > 0 ->
                            "${vault.inbox} shared item(s) are waiting and move in when you unlock."
                        else -> "Unlock with your fingerprint or screen lock."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = {
                    askToUnlock(context, if (firstTime) "Set up the vault" else "Unlock the vault", {
                        viewModel.onUnlocked(firstTime)
                    }, viewModel::onMessage)
                }) { Text(if (firstTime) "Set up" else "Unlock") }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        VaultState.Unlocked -> {
            SecureVaultScreen(viewModel.autoLock)
            UnlockedVault(state, viewModel, onOpenItem, onOpenTrashAndBackup, modifier)
        }
    }
}

@Composable
private fun UnlockedVault(
    state: VaultListState,
    viewModel: VaultViewModel,
    onOpenItem: (Long) -> Unit,
    onOpenTrashAndBackup: () -> Unit,
    modifier: Modifier,
) {
    var adding by remember { mutableStateOf<ItemType?>(null) }
    var menu by remember { mutableStateOf(false) }
    val pick =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> uri?.let(viewModel::onAddFile) }
    val f = state.filter
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = { q -> viewModel.onFilter { it.copy(query = q) } },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chips {
                    SortOrder.entries.forEach { s ->
                        FilterChip(f.sort == s, {
                            viewModel.onFilter { it.copy(sort = s) }
                        }, { Text(s.title) })
                    }
                }
                Chips {
                    FilterChip(f.favouritesOnly, {
                        viewModel.onFilter { it.copy(favouritesOnly = !it.favouritesOnly) }
                    }, { Text("Favourites") })
                    ItemType.entries.forEach { t ->
                        FilterChip(
                            f.type == t,
                            { viewModel.onFilter { it.copy(type = if (it.type == t) null else t) } },
                            { Text(t.title) }
                        )
                    }
                }
                if (state.folders.isNotEmpty() || state.tags.isNotEmpty()) {
                    Chips {
                        state.folders.forEach { folder ->
                            FilterChip(f.folder == folder, {
                                viewModel.onFilter { it.copy(folder = if (it.folder == folder) null else folder) }
                            }, { Text("📁 $folder") })
                        }
                        state.tags.forEach { tag ->
                            FilterChip(
                                f.tag == tag,
                                { viewModel.onFilter { it.copy(tag = if (it.tag == tag) null else tag) } },
                                { Text("#$tag") }
                            )
                        }
                    }
                }
            }
            if (state.items.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.items, key = { it.id }) { item -> ItemRow(item) { onOpenItem(item.id) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenTrashAndBackup) { Text("Trash (${state.trashCount}) & backup") }
                    OutlinedButton(onClick = viewModel::onLock) { Text("Lock now") }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(onClick = {
                menu = true
            }, icon = { Icon(Icons.Filled.Add, contentDescription = "Add") }, text = { Text("Add") })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Note") }, onClick = {
                    menu = false
                    adding = ItemType.TEXT
                })
                DropdownMenuItem(text = { Text("Link") }, onClick = {
                    menu = false
                    adding = ItemType.LINK
                })
                DropdownMenuItem(text = { Text("Image or file") }, onClick = {
                    menu = false
                    pick.launch(arrayOf("*/*"))
                })
            }
        }
    }
    when (adding) {
        ItemType.TEXT -> AddDialog(
            "New note",
            "Title (optional)",
            "Note",
            onDismiss = { adding = null }
        ) { title, text ->
            viewModel.onAddNote(title, text)
            adding = null
        }
        ItemType.LINK -> AddDialog("New link", "Title (optional)", "Link (https://…)", onDismiss = {
            adding = null
        }) { title, url ->
            viewModel.onAddLink(url.trim(), title)
            adding = null
        }
        else -> Unit
    }
}

@Composable
private fun ItemRow(item: VaultItem, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            overlineContent = {
                Text(
                    listOfNotNull(item.type.title, item.folder.ifBlank { null }).joinToString(" · ")
                )
            },
            headlineContent = { Text(item.title.ifBlank { "(untitled)" }, maxLines = 1) },
            supportingContent = {
                val detail = when (item.type) {
                    ItemType.TEXT -> item.text
                    ItemType.LINK -> item.preview?.description?.ifBlank { null } ?: item.url
                    else -> item.fileName
                }
                Text(
                    detail + if (item.tags.isNotEmpty()) "  " + item.tags.joinToString(" ") { "#$it" } else "",
                    maxLines = 2
                )
            },
            trailingContent = {
                Row {
                    if (item.pinned) Icon(Icons.Filled.Star, contentDescription = "Pinned")
                    if (item.favourite) Icon(Icons.Filled.Favorite, contentDescription = "Favourite")
                }
            },
        )
    }
}

@Composable
private fun AddDialog(
    title: String,
    firstLabel: String,
    secondLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(first, { first = it }, label = { Text(firstLabel) }, singleLine = true)
                OutlinedTextField(second, { second = it }, label = { Text(secondLabel) }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(first, second) },
                enabled = second.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun Chips(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@Composable
internal fun Centered(
    modifier: Modifier,
    title: String,
    text: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium)
        action?.invoke()
    }
}

/** Android's "choose a screen lock" screen (PIN, pattern, password). */
internal fun screenLockSettings(): Intent =
    Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun formatDate(millis: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT
).format(Date(millis))
