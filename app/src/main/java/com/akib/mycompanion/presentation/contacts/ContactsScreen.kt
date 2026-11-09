package com.akib.mycompanion.presentation.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.R
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.ui.components.rememberChromeListState

/** Stateless People screen: a list of big, obvious call buttons. */
@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onAddClick: () -> Unit,
    onCall: (Contact) -> Unit,
    onEdit: (Contact) -> Unit,
    onDelete: (Contact) -> Unit,
    onPinShortcut: (Contact) -> Unit,
    modifier: Modifier = Modifier,
    onVideoCall: (Contact) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = state.contacts.filter { (it.name + " " + it.phone).contains(query.trim(), ignoreCase = true) }
    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.contacts.isEmpty() -> EmptyContacts(onAddClick, Modifier.align(Alignment.Center))
            else -> LazyColumn(
                state = rememberChromeListState(),
                contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 144.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Voice or video, one tap away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Find a person") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Find a person" },
                    )
                }
                if (filtered.isEmpty()) {
                    item { Text("No matching people", modifier = Modifier.padding(vertical = 24.dp)) }
                }
                items(filtered, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        canPinShortcut = state.canPinShortcuts,
                        onCall = { onCall(contact) },
                        onVideoCall = { onVideoCall(contact) },
                        onEdit = { onEdit(contact) },
                        onDelete = { onDelete(contact) },
                        onPinShortcut = { onPinShortcut(contact) },
                    )
                }
            }
        }

        if (!state.loading && state.contacts.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                // The FAB hides its text from TalkBack (clearAndSetSemantics), so the icon carries the name.
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add person") },
                text = { Text("Add person") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            )
        }
    }
}

@Composable
private fun EmptyContacts(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("No one here yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Add the people you call most. Then one tap starts a WhatsApp call.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Add person")
        }
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    canPinShortcut: Boolean,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPinShortcut: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.name)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ContactMenu(contact.name, canPinShortcut, onEdit, onDelete, onPinShortcut)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onCall, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call ${contact.name} on WhatsApp",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Voice call", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(onClick = onVideoCall, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_video_call),
                        contentDescription = "Video call ${contact.name} on WhatsApp",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Video call", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ContactMenu(
    name: String,
    canPinShortcut: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPinShortcut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options for $name")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            if (canPinShortcut) {
                DropdownMenuItem(
                    text = { Text("Add to home screen") },
                    leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    onClick = {
                        open = false
                        onPinShortcut()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** "Akib Hasan" → "AH", "akib" → "A", "" → "?". */
fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val letters = if (words.size == 1) words.first().take(1) else words.first().take(1) + words.last().take(1)
    return letters.uppercase()
}
