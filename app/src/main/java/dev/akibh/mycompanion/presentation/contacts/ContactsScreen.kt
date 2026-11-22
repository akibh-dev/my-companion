package dev.akibh.mycompanion.presentation.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.ui.components.AddButton
import dev.akibh.mycompanion.ui.components.AddButtonClearance
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.DotText
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.rememberChromeListState
import dev.akibh.mycompanion.ui.theme.CompanionRed

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
            state.contacts.isEmpty() -> EmptyState(
                DotSymbol.VOICE,
                "No one here yet",
                "Add the people you call most. Then one tap starts a WhatsApp call.",
                Modifier.align(Alignment.Center),
            )
            else -> LazyColumn(
                state = rememberChromeListState(),
                contentPadding = PaddingValues(
                    start = PageMargin,
                    top = 12.dp,
                    end = PageMargin,
                    bottom = ADD_BUTTON_LIFT + AddButtonClearance,
                ),
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
                    item { EmptyState(DotSymbol.SEARCH, "No matching people", "Try part of a name or number.") }
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

        if (!state.loading) {
            // Lifted above the floating nav bar.
            AddButton(
                "Add person",
                onClick = onAddClick,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = ADD_BUTTON_LIFT),
            )
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
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.padding(start = 20.dp, top = 20.dp, end = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.name)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        contact.phone,
                        style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ContactMenu(contact.name, canPinShortcut, onEdit, onDelete, onPinShortcut)
            }
            // Voice is the everyday action, so it gets the filled button; video sits beside it as a grey pill.
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onCall, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call ${contact.name} on WhatsApp",
                        tint = CompanionRed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Voice call", style = MaterialTheme.typography.labelLarge)
                }
                SecondaryButton(onClick = onVideoCall, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_video_call),
                        contentDescription = "Video call ${contact.name} on WhatsApp",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
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

/** The person's initials in the dotted display face, on a ring that matches the page's outlines. */
@Composable
private fun Avatar(name: String) {
    Box(
        Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        DotText(initials(name), fontSize = 24.sp)
    }
}

/** "Akib Hasan" → "AH", "akib" → "A", "" → "?". */
fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val letters = if (words.size == 1) words.first().take(1) else words.first().take(1) + words.last().take(1)
    return letters.uppercase()
}

/** Lifts the add button above the floating nav bar (with its own 16 dp margin: 136 dp from the bottom). */
private val ADD_BUTTON_LIFT = 120.dp
