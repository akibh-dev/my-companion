package dev.akibh.mycompanion.presentation.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.core.health.HeartbeatLog
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.log.LogLevel
import dev.akibh.mycompanion.core.log.LogLine
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SectionLabel
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogUiState(
    val lines: List<LogLine> = emptyList(),
    val gaps: List<String> = emptyList(),
    val groups: List<LogGroup> = emptyList(),
)

/** One hour of the log ("Today · 13:00"), newest line first. */
data class LogGroup(val label: String, val lines: List<LogLine>)

/**
 * Groups [lines] (newest first, times like `2026-11-22 13:47:48.058`) by day and hour, newest first.
 * Lines whose time can't be read go into a group of their own, keeping their order.
 */
fun groupLog(lines: List<LogLine>, today: LocalDate = LocalDate.now()): List<LogGroup> {
    val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val groups = mutableListOf<LogGroup>()
    var key: String? = null
    var current = mutableListOf<LogLine>()
    var label = ""
    for (line in lines) {
        val date = line.time.substringBefore(' ')
        val hour = line.time.substringAfter(' ', "").take(2)
        val lineKey = "$date $hour"
        if (lineKey != key) {
            if (current.isNotEmpty()) groups += LogGroup(label, current)
            current = mutableListOf()
            key = lineKey
            val day = runCatching { LocalDate.parse(date) }.getOrNull()
            val dayLabel = when (day) {
                null -> date
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> day.format(dayFormat)
            }
            label = if (hour.length == 2 && hour.all(Char::isDigit)) "$dayLabel · $hour:00" else dayLabel
        }
        current += line
    }
    if (current.isNotEmpty()) groups += LogGroup(label, current)
    return groups
}

@HiltViewModel
class LogViewModel @Inject constructor(private val log: AppLog, private val heartbeats: HeartbeatLog) : ViewModel() {
    private val mutableState = MutableStateFlow(LogUiState())
    val state: StateFlow<LogUiState> = mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val lines = log.read().asReversed()
            mutableState.value = LogUiState(
                lines = lines,
                gaps = heartbeats.gaps().map { gap ->
                    "${format.format(Date(gap.fromMillis))} → ${format.format(Date(gap.toMillis))}"
                },
                groups = withContext(Dispatchers.Default) { groupLog(lines) },
            )
        }
    }

    fun clear() {
        viewModelScope.launch {
            log.clear()
            refresh()
        }
    }
}

/** The on-device log, newest first, and gaps in the heartbeat (times the app wasn't running). */
@Composable
fun LogRoute(modifier: Modifier = Modifier, viewModel: LogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LogScreen(state, onRefresh = viewModel::refresh, onClear = viewModel::clear, modifier = modifier)
}

/** Each entry is a row (time, then what happened), grouped by hour under small labels. Lazy, so long logs stay cheap. */
@Composable
fun LogScreen(state: LogUiState, onRefresh: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = PageMargin, top = 8.dp, end = PageMargin, bottom = SectionGap),
    ) {
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(onClick = onRefresh) { Text("Refresh") }
                SecondaryButton(onClick = onClear, destructive = true) { Text("Clear") }
            }
        }
        item(key = "gaps") {
            Section(
                "Heartbeat gaps",
                modifier = Modifier.padding(top = SectionGap),
                note = "Times the app wasn't running in the background.",
            ) {
                if (state.gaps.isEmpty()) {
                    Text("None: the app has kept running", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.gaps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (state.groups.isEmpty()) {
            item(key = "empty") {
                EmptyState(DotSymbol.CONTENTS, "The log is empty", "What the app does shows up here as it happens.")
            }
        }
        state.groups.forEachIndexed { index, group ->
            item(key = "label-$index-${group.label}") {
                SectionLabel(group.label, Modifier.padding(start = 8.dp, top = SectionGap, bottom = 8.dp))
            }
            items(group.lines) { line -> LogRow(line) }
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val colors = MaterialTheme.colorScheme
    val messageColor = when (line.level) {
        LogLevel.INFO -> colors.onSurface
        LogLevel.WARN -> colors.tertiary
        LogLevel.ERROR -> colors.error
    }
    val clock = line.time.substringAfter(' ').substringBefore('.')
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            clock,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(56.dp).padding(top = 2.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Note(
                when (line.level) {
                    LogLevel.INFO -> line.tag
                    LogLevel.WARN -> "${line.tag} · warning"
                    LogLevel.ERROR -> "${line.tag} · error"
                },
            )
            Text(line.message, style = MaterialTheme.typography.bodyMedium, color = messageColor)
        }
    }
}
