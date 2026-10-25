package com.akib.mycompanion.autoreply.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.LogEntry
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.MessageSamples
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MessagingLogsViewModel @Inject constructor(
    repository: AutoReplyRepository,
    samples: MessageSamples
) : ViewModel() {
    val log: StateFlow<List<LogEntry>> = repository.log.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val samples: StateFlow<List<MessageSamples.Sample>> = samples.samples
    val connected: StateFlow<Boolean> = samples.connected
}

/** Every auto-reply and missed-call text, and why one wasn't sent (FR-1.8). */
@Composable
fun ReplyLogRoute(modifier: Modifier = Modifier, viewModel: MessagingLogsViewModel = hiltViewModel()) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (log.isEmpty()) item { Text("No replies yet.") }
        items(log) { entry ->
            ListItem(
                overlineContent = { Text("${format.format(Date(entry.time))} · ${entry.app}") },
                headlineContent = { Text(entry.person) },
                supportingContent = {
                    Text(
                        if (entry.replied) entry.text else "Not sent: ${entry.reason}",
                        color = MaterialTheme.colorScheme.run { if (entry.replied) onSurface else error },
                    )
                },
            )
        }
    }
}

/**
 * Per chat app: are its notifications understood, and do they offer a reply button? Built from the
 * structure of recent notifications only, never their text (spike S4, plan §6).
 */
@Composable
fun MessagingSelfTestRoute(modifier: Modifier = Modifier, viewModel: MessagingLogsViewModel = hiltViewModel()) {
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                if (connected) {
                    "Notification access is on. Send yourself a test message from each app."
                } else {
                    "Notification access isn't connected yet."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(ChatApp.entries) { app ->
            val mine = samples.filter { it.app == app }
            val status = when {
                mine.isEmpty() -> "No notification seen yet"
                mine.none { !it.skipped } -> "Seen, but none understood as a message"
                mine.any { !it.skipped && it.hasReplyAction } -> "Works: messages understood, reply button found"
                else -> "Messages understood, but no reply button: voice mode only"
            }
            ListItem(
                headlineContent = { Text(app.title) },
                supportingContent = {
                    Text(
                        status + mine.takeIf { it.isNotEmpty() }
                            ?.let { " (${it.size} seen, MessagingStyle: ${it.any { s -> s.usesMessagingStyle }})" }
                            .orEmpty(),
                    )
                },
            )
        }
    }
}
