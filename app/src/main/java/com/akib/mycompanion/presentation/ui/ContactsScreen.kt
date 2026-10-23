package com.akib.mycompanion.presentation.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.domain.model.Contact

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onCall: (Contact) -> Unit,
    onAdd: (name: String, phone: String) -> Unit,
    onDelete: (Contact) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        if (contacts.isEmpty()) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No contacts yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap + to add someone you call often.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(contact, onCall = { onCall(contact) }, onDelete = { onDelete(contact) })
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Add contact") }
    }

    if (showAdd) {
        AddContactDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, phone ->
                onAdd(name, phone)
                showAdd = false
            },
        )
    }
}

@Composable
private fun ContactCard(contact: Contact, onCall: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(contact.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(contact.phone) },
            trailingContent = {
                ContactActions(onDelete = onDelete, onCall = onCall)
            },
        )
    }
}

@Composable
private fun ContactActions(onDelete: () -> Unit, onCall: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove contact")
        }
        IconButton(
            onClick = onCall,
            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call on WhatsApp",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun AddContactDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && phone.count { it.isDigit() } >= 7

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Number with country code") },
                    placeholder = { Text("+8801XXXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, phone) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
