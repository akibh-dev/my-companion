package com.akib.mycompanion.presentation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val delays = listOf(0 to "Now", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifyScreen(
    onSend: (title: String, message: String, delayMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var delay by remember { mutableIntStateOf(0) }

    Column(
        modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Custom notification", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Remind me in", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            delays.forEach { (minutes, label) ->
                FilterChip(
                    selected = delay == minutes,
                    onClick = { delay = minutes },
                    label = { Text(label) },
                )
            }
        }

        Button(
            onClick = {
                onSend(title.ifBlank { "My Companion" }, message, delay)
                title = ""
                message = ""
            },
            enabled = title.isNotBlank() || message.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Text(if (delay == 0) "  Send now" else "  Schedule")
        }
    }
}
