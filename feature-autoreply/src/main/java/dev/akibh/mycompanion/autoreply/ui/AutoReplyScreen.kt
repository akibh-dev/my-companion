package dev.akibh.mycompanion.autoreply.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.autoreply.data.AutoReplyRepository
import dev.akibh.mycompanion.autoreply.data.AutoReplySettingsStore
import dev.akibh.mycompanion.autoreply.domain.Audience
import dev.akibh.mycompanion.autoreply.domain.AutoReplySettings
import dev.akibh.mycompanion.autoreply.domain.GroupPolicy
import dev.akibh.mycompanion.autoreply.domain.ReplyContext
import dev.akibh.mycompanion.autoreply.domain.ReplyTemplate
import dev.akibh.mycompanion.core.context.ContextStatus
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.ui.components.ChoiceChip
import dev.akibh.mycompanion.ui.components.ChoiceRow
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionCard
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SwitchRow
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
    /** False until the stores have answered, so "No replies yet" doesn't flash on open. */
    val loaded: Boolean = false,
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
            AutoReplyUiState(app, s, t, rules, loaded = true)
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

    // Which duration was tapped last, so a timed busy shows its chip as chosen.
    var pickedMinutes by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(PageMargin),
        verticalArrangement = Arrangement.spacedBy(SectionGap)
    ) {
        item {
            SectionCard {
                SwitchRow("Auto-reply to chats", Feature.AUTO_REPLY in state.app.enabled, {
                    viewModel.onFeature(Feature.AUTO_REPLY, it)
                })
                SwitchRow("Text back after a missed call", Feature.MISSED_CALL_SMS in state.app.enabled, {
                    viewModel.onFeature(Feature.MISSED_CALL_SMS, it)
                })
                if (state.app.paused) Note("Paused from Home")
            }
        }
        item {
            val busyText = when {
                !s.busy -> "Not busy"
                s.busyUntil == null -> "Busy until I switch it off"
                else -> "Busy until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.busyUntil))
            }
            Section("Busy") {
                Text(busyText, style = MaterialTheme.typography.titleSmall)
                ChoiceRow {
                    BUSY_CHOICES.forEach { (minutes, label) ->
                        ChoiceChip(
                            label = label,
                            selected = when {
                                !s.busy -> minutes == 0
                                s.busyUntil == null -> minutes == null
                                else -> minutes != null && minutes != 0 && minutes == pickedMinutes
                            },
                            onClick = {
                                pickedMinutes = minutes
                                viewModel.onBusy(minutes)
                            },
                        )
                    }
                }
            }
        }
        item {
            Section(
                "Replies",
                note = "The first reply whose \"when\" is true is sent. Use {name}, {app} and {until} in the text." +
                    state.activeRules.takeIf { it.isNotEmpty() }
                        ?.let { " Active rules now: ${it.joinToString()}." }
                        .orEmpty(),
            ) {
                if (state.loaded && state.templates.isEmpty()) {
                    EmptyState(
                        symbol = DotSymbol.REPLY,
                        title = "No replies yet",
                        body = "Add one to say what gets sent while you're busy.",
                    )
                }
                state.templates.forEach { template ->
                    ReplyRow(template, onClick = { editing = template })
                }
                SecondaryButton(
                    onClick = { editing = ReplyTemplate(name = "", text = "", context = ReplyContext.Busy) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add reply") }
            }
        }
        item {
            Section("Who gets replies") {
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
        }
        item {
            Section("Groups") {
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
        }
        item {
            Section("How often") {
                Text(
                    "At most one reply per chat every ${s.cooldownMinutes} min",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = s.cooldownMinutes.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(cooldownMinutes = (v / 5).toInt() * 5) } },
                    valueRange = 5f..240f,
                )
            }
        }
        item {
            Section("Label") {
                OutlinedTextField(
                    s.label,
                    { l -> viewModel.update { it.copy(label = l) } },
                    label = { Text("Label in front of every reply") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Section("Apps") {
                ChipRow(ChatApp.entries, { it.title }, { it in s.apps }, multiple = true) { app ->
                    viewModel.update { it.copy(apps = if (app in it.apps) it.apps - app else it.apps + app) }
                }
            }
        }
        item {
            Section("Missed-call text") {
                OutlinedTextField(
                    s.missedCallText,
                    { t -> viewModel.update { it.copy(missedCallText = t) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow("Only to saved contacts", s.missedCallContactsOnly, { on ->
                    viewModel.update {
                        it.copy(missedCallContactsOnly = on)
                    }
                })
            }
        }
        item {
            ChoiceRow {
                SecondaryButton(onClick = onOpenLog) { Text("Reply log") }
                SecondaryButton(onClick = onOpenSelfTest) { Text("Chat app self-test") }
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
                onDelete?.let {
                    TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** [minutes] for [AutoReplyViewModel.onBusy]: 0 = not busy, null = until switched off. */
private val BUSY_CHOICES: List<Pair<Int?, String>> = listOf(
    0 to "Off",
    30 to "30 min",
    60 to "1 h",
    120 to "2 h",
    null to "Until off",
)

/** One reply in the Replies card: tap to edit; the chevron says so. */
@Composable
private fun ReplyRow(template: ReplyTemplate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Edit", onClick = onClick)
            // Inset from the ripple's rounded corners, which would otherwise clip the first letters.
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${describe(template.context)} · ${template.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun <T> ChipRow(
    items: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    multiple: Boolean = false,
    onClick: (T) -> Unit,
) {
    ChoiceRow {
        items.forEach { item ->
            ChoiceChip(label = label(item), selected = selected(item), onClick = { onClick(item) }, multiple = multiple)
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
