package com.akib.mycompanion.presentation.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.core.health.HeartbeatLog
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.log.LogLevel
import com.akib.mycompanion.core.log.LogLine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogUiState(val lines: List<LogLine> = emptyList(), val gaps: List<String> = emptyList())

@HiltViewModel
class LogViewModel @Inject constructor(private val log: AppLog, private val heartbeats: HeartbeatLog) : ViewModel() {
    private val mutableState = MutableStateFlow(LogUiState())
    val state: StateFlow<LogUiState> = mutableState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            mutableState.value = LogUiState(
                lines = log.read().asReversed(),
                gaps = heartbeats.gaps().map { gap ->
                    "${format.format(Date(gap.fromMillis))} → ${format.format(Date(gap.toMillis))}"
                },
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
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::refresh) { Text("Refresh") }
                OutlinedButton(onClick = viewModel::clear) { Text("Clear") }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text("Heartbeat gaps", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (state.gaps.isEmpty()) "None: the app has kept running" else state.gaps.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (state.lines.isEmpty()) item { Text("The log is empty") }
        items(state.lines) { line ->
            Text(
                "${line.time.substringAfter(' ')} ${line.tag}: ${line.message}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = when (line.level) {
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
