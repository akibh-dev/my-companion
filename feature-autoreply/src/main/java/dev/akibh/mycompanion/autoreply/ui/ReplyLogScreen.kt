package dev.akibh.mycompanion.autoreply.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.autoreply.data.AutoReplyRepository
import dev.akibh.mycompanion.autoreply.data.LogEntry
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.messaging.MessageSamples
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionCard
import dev.akibh.mycompanion.ui.components.SectionGap
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
    // null until the database answers, so the empty state doesn't flash on open.
    val log: StateFlow<List<LogEntry>?> = repository.log.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )
    val samples: StateFlow<List<MessageSamples.Sample>> = samples.samples
    val connected: StateFlow<Boolean> = samples.connected
}

/** Every auto-reply and missed-call text, and why one wasn't sent (FR-1.8). */
@Composable
fun ReplyLogRoute(modifier: Modifier = Modifier, viewModel: MessagingLogsViewModel = hiltViewModel()) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    log?.let { ReplyLogScreen(it, modifier) }
}

@Composable
fun ReplyLogScreen(log: List<LogEntry>, modifier: Modifier = Modifier) {
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(PageMargin),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (log.isEmpty()) {
            item {
                EmptyState(
                    symbol = DotSymbol.REPLY,
                    title = "No replies yet",
                    body = "Auto-replies and missed-call texts show up here, with the reason when one isn't sent.",
                )
            }
        }
        items(log) { entry ->
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Note("${format.format(Date(entry.time))} · ${entry.app}")
                    Text(entry.person, style = MaterialTheme.typography.titleSmall)
                    if (entry.replied) {
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Note("Not sent: ${entry.reason}")
                    }
                }
            }
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
    MessagingSelfTestScreen(samples, connected, modifier)
}

@Composable
fun MessagingSelfTestScreen(
    samples: List<MessageSamples.Sample>,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val access = if (connected) {
        "Notification access is on. Send yourself a test message from each app."
    } else {
        "Notification access isn't connected yet."
    }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(PageMargin),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        if (samples.isEmpty()) {
            EmptyState(symbol = DotSymbol.MESSAGE, title = "No chat notifications seen yet", body = access)
        }
        Section("Chat apps", note = access.takeIf { samples.isNotEmpty() }) {
            ChatApp.entries.forEach { app ->
                val mine = samples.filter { it.app == app }
                val status = when {
                    mine.isEmpty() -> "No notification seen yet"
                    mine.none { !it.skipped } -> "Seen, but none understood as a message"
                    mine.any { !it.skipped && it.hasReplyAction } -> "Works: messages understood, reply button found"
                    else -> "Messages understood, but no reply button: voice mode only"
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(app.title, style = MaterialTheme.typography.titleSmall)
                    Note(
                        status + mine.takeIf { it.isNotEmpty() }
                            ?.let { " (${it.size} seen, MessagingStyle: ${it.any { s -> s.usesMessagingStyle }})" }
                            .orEmpty(),
                    )
                }
            }
        }
    }
}
