package com.akib.mycompanion.autoreply.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.autoreply.domain.Audience
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.GroupPolicy
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.core.context.ContextStatus
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.messaging.ChatApp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AutoReplyUiState(
    val app: Settings = Settings(),
    val settings: AutoReplySettings = AutoReplySettings(),
    val templates: List<ReplyTemplate> = emptyList(),
    val activeRules: Set<String> = emptySet(),
)

@HiltViewModel
class AutoReplyViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val replySettings: AutoReplySettingsStore,
    private val repository: AutoReplyRepository,
    private val clock: Clock,
    status: ContextStatus,
) : ViewModel() {
    val state: StateFlow<AutoReplyUiState> =
        combine(
            settingsStore.settings,
            replySettings.settings,
            repository.templates,
            status.activeRules
        ) { app, s, t, rules ->
            AutoReplyUiState(app, s, t, rules)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoReplyUiState())

    init {
        viewModelScope.launch { repository.addDefaultsIfEmpty() }
    }

    fun onFeature(feature: Feature, on: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(feature, on) }
    }

    /** [minutes] null = until switched off; 0 = not busy. */
    fun onBusy(minutes: Int?) = update {
        when (minutes) {
            0 -> it.copy(busy = false, busyUntil = null)
            null -> it.copy(busy = true, busyUntil = null)
            else -> it.copy(busy = true, busyUntil = clock.nowMillis() + minutes * 60_000L)
        }
    }

    fun update(change: (AutoReplySettings) -> AutoReplySettings) {
        viewModelScope.launch { replySettings.update(change) }
    }

    fun onSaveTemplate(template: ReplyTemplate) {
        viewModelScope.launch {
            val templates = state.value.templates
            val order = templates.indexOfFirst { it.id == template.id }.takeIf { it >= 0 } ?: templates.size
            repository.save(template, order)
        }
    }

    fun onDeleteTemplate(template: ReplyTemplate) {
        viewModelScope.launch { repository.delete(template) }
    }
}

/** Auto-reply settings (plan §11: audience and settings, templates). */
@Composable
fun AutoReplyRoute(
    onOpenLog: () -> Unit,
    onOpenSelfTest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutoReplyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    var editing by remember { mutableStateOf<ReplyTemplate?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SwitchRow("Auto-reply to chats", Feature.AUTO_REPLY in state.app.enabled) {
                        viewModel.onFeature(Feature.AUTO_REPLY, it)
                    }
                    SwitchRow("Text back after a missed call", Feature.MISSED_CALL_SMS in state.app.enabled) {
                        viewModel.onFeature(Feature.MISSED_CALL_SMS, it)
                    }
                    if (state.app.paused) Text("Paused from Home", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Section("I'm busy")
            val busyText = when {
                !s.busy -> "Not busy"
                s.busyUntil == null -> "Busy until I switch it off"
                else -> "Busy until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.busyUntil))
            }
            Text(busyText, style = MaterialTheme.typography.bodyMedium)
            ChipRow(
                listOf(
                    0 to "Off",
                    30 to "30 min",
                    60 to "1 h",
                    120 to "2 h",
                    null to "Until off"
                ),
                { (_, l) -> l },
                {
                    false
                }
            ) { (m, _) ->
                viewModel.onBusy(m)
            }
        }
        item {
            Section("Replies")
            Text(
                "The first reply whose \"when\" is true is sent. Use {name}, {app} and {until} in the text." +
                    state.activeRules.takeIf { it.isNotEmpty() }
                        ?.let { " Active rules now: ${it.joinToString()}." }
                        .orEmpty(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(state.templates, key = { it.id }) { template ->
            ElevatedCard(onClick = { editing = template }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(template.name) },
                    supportingContent = { Text("${describe(template.context)} · ${template.text}") },
                )
            }
        }
        item {
            OutlinedButton(onClick = {
                editing = ReplyTemplate(name = "", text = "", context = ReplyContext.Busy)
            }) { Text("Add reply") }
        }
        item {
            Section("Who gets replies")
            ChipRow(
                Audience.entries,
                { it.title },
                { it == s.audience }
            ) { a -> viewModel.update { it.copy(audience = a) } }
            if (s.audience == Audience.ALLOW_LIST) {
                ListField(
                    "Names, comma separated",
                    s.allowList
                ) { list -> viewModel.update { it.copy(allowList = list) } }
            }
            ListField("Never reply to (names, comma separated)", s.blockList) { list ->
                viewModel.update {
                    it.copy(blockList = list)
                }
            }
        }
        item {
            Section("Groups")
            ChipRow(
                GroupPolicy.entries,
                { it.title },
                { it == s.groups }
            ) { g -> viewModel.update { it.copy(groups = g) } }
            if (s.groups == GroupPolicy.WHEN_MENTIONED) {
                ListField("Your name or nickname, comma separated", s.mentionWords) { list ->
                    viewModel.update {
                        it.copy(mentionWords = list)
                    }
                }
            }
        }
        item {
            Section("At most one reply per chat every ${s.cooldownMinutes} min")
            Slider(
                value = s.cooldownMinutes.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(cooldownMinutes = (v / 5).toInt() * 5) } },
                valueRange = 5f..240f,
            )
        }
        item {
            Section("Label in front of every reply")
            OutlinedTextField(s.label, { l ->
                viewModel.update {
                    it.copy(label = l)
                }
            }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Section("Apps")
            ChipRow(ChatApp.entries, { it.title }, { it in s.apps }) { app ->
                viewModel.update { it.copy(apps = if (app in it.apps) it.apps - app else it.apps + app) }
            }
        }
        item {
            Section("Missed-call text")
            OutlinedTextField(
                s.missedCallText,
                { t -> viewModel.update { it.copy(missedCallText = t) } },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("Only to saved contacts", s.missedCallContactsOnly) { on ->
                viewModel.update {
                    it.copy(missedCallContactsOnly = on)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenLog) { Text("Reply log") }
                OutlinedButton(onClick = onOpenSelfTest) { Text("Chat app self-test") }
            }
        }
    }

    editing?.let { template ->
        TemplateDialog(
            template = template,
            onDismiss = { editing = null },
            onSave = {
                viewModel.onSaveTemplate(it)
                editing = null
            },
            onDelete = if (template.id != 0L) {
                {
                    viewModel.onDeleteTemplate(template)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

fun describe(context: ReplyContext): String = when (context) {
    ReplyContext.Busy -> "When I'm busy"
    is ReplyContext.WhileRule -> "While \"${context.ruleName}\" is active"
    ReplyContext.WhileDnd -> "During Do Not Disturb"
    ReplyContext.Always -> "Always"
}

@Composable
private fun TemplateDialog(
    template: ReplyTemplate,
    onDismiss: () -> Unit,
    onSave: (ReplyTemplate) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(template.name) }
    var text by remember { mutableStateOf(template.text) }
    var context by remember { mutableStateOf(template.context) }
    var ruleName by remember { mutableStateOf((template.context as? ReplyContext.WhileRule)?.ruleName.orEmpty()) }
    val kinds = listOf(ReplyContext.Busy, ReplyContext.WhileRule(""), ReplyContext.WhileDnd, ReplyContext.Always)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template.id == 0L) "New reply" else "Edit reply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("Reply text") }, minLines = 2)
                ChipRow(
                    kinds,
                    { describe(it).substringBefore(" \"") },
                    { it::class == context::class }
                ) { context = it }
                if (context is ReplyContext.WhileRule) {
                    OutlinedTextField(
                        ruleName,
                        { ruleName = it },
                        label = { Text("Context rule name") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            val final = if (context is ReplyContext.WhileRule) ReplyContext.WhileRule(ruleName.trim()) else context
            TextButton(
                onClick = { onSave(template.copy(name = name.trim(), text = text.trim(), context = final)) },
                enabled = name.isNotBlank() && text.isNotBlank() &&
                    (final !is ReplyContext.WhileRule || final.ruleName.isNotBlank()),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun <T> ChipRow(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            FilterChip(selected = selected(item), onClick = {
                onClick(item)
            }, label = { Text(label(item)) })
        }
    }
}

/** A comma-separated list, saved when you leave the field's text as it is for a moment. */
@Composable
internal fun ListField(label: String, values: List<String>, onChange: (List<String>) -> Unit) {
    var text by remember(values) { mutableStateOf(values.joinToString(", ")) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.split(',').map(String::trim).filter(String::isNotEmpty))
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}
