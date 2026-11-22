package dev.akibh.mycompanion.voice.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.core.settings.AiEngine
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.messaging.InstalledApp
import dev.akibh.mycompanion.messaging.InstalledApps
import dev.akibh.mycompanion.ui.components.ChoiceChip
import dev.akibh.mycompanion.ui.components.ChoiceRow
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.PrimaryButton
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SwitchRow
import dev.akibh.mycompanion.voice.domain.AppScope
import dev.akibh.mycompanion.voice.domain.BlockRule
import dev.akibh.mycompanion.voice.domain.BlockWhere
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.ReadingMode
import dev.akibh.mycompanion.voice.domain.VoiceSettings
import dev.akibh.mycompanion.voice.system.VoicePlayback
import dev.akibh.mycompanion.voice.system.VoiceSettingsStore
import dev.akibh.mycompanion.voice.system.VoiceState
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val playback: VoicePlayback,
    private val installedApps: InstalledApps,
) : ViewModel() {
    val app: StateFlow<Settings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Settings()
    )
    val settings: StateFlow<VoiceSettings> =
        voiceSettings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettings())
    val playbackState = playback.state
    private val mutableApps = MutableStateFlow<List<InstalledApp>?>(null)

    /** Null until [loadApps] has listed them. */
    val apps: StateFlow<List<InstalledApp>?> = mutableApps.asStateFlow()
    private var testJob: Job? = null

    fun loadApps() {
        if (mutableApps.value != null) return
        viewModelScope.launch {
            mutableApps.value = withContext(Dispatchers.IO) { installedApps.launchable() }
        }
    }

    fun label(packageName: String): String = installedApps.label(packageName)

    fun toggleMute(packageName: String) = update {
        it.copy(mutedApps = if (packageName in it.mutedApps) it.mutedApps - packageName else it.mutedApps + packageName)
    }

    fun addRule(phrase: String, where: BlockWhere) {
        val rule = BlockRule(phrase.trim(), where)
        if (rule.phrase.isEmpty()) return
        update { it.copy(blockRules = (it.blockRules - rule + rule)) }
    }

    fun removeRule(rule: BlockRule) = update { it.copy(blockRules = it.blockRules - rule) }

    fun onVoiceMode(on: Boolean) {
        viewModelScope.launch {
            if (on && settings.value.readingMode != ReadingMode.ORIGINAL) {
                settingsStore.setUseModel(AiEngine.TRANSLITERATOR, true)
            }
            settingsStore.setEnabled(Feature.VOICE_MODE, on)
            if (!on) playback.stop()
        }
    }

    fun update(change: (VoiceSettings) -> VoiceSettings) {
        viewModelScope.launch { voiceSettings.update(change) }
    }

    fun mode(mode: ReadingMode) {
        viewModelScope.launch {
            if (mode != ReadingMode.ORIGINAL) {
                settingsStore.setUseModel(AiEngine.TRANSLITERATOR, true)
            }
            voiceSettings.update { it.copy(readingMode = mode) }
        }
    }

    fun onTest() {
        if (testJob?.isActive == true) return
        testJob = viewModelScope.launch { playback.sample() }
    }

    fun stop() {
        testJob?.cancel()
        playback.stop()
    }

    override fun onCleared() {
        testJob?.cancel()
        super.onCleared()
    }
}

/** Voice settings (plan §11): apps, muting and block rules, senders, privacy, speed, and a test. */
@Composable
fun VoiceRoute(modifier: Modifier = Modifier, viewModel: VoiceViewModel = hiltViewModel()) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var senders by remember(s.senders) { mutableStateOf(s.senders.joinToString(", ")) }
    var rulePhrase by remember { mutableStateOf("") }
    var ruleWhere by remember { mutableStateOf(BlockWhere.ANYWHERE) }
    var pickingApps by remember { mutableStateOf(false) }
    if (pickingApps) {
        val apps by viewModel.apps.collectAsStateWithLifecycle()
        AppPicker(apps, s.mutedApps, viewModel::toggleMute) { pickingApps = false }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(PageMargin),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item {
            Section(
                label = null,
                note = "Also a Quick Settings tile. Silent during calls. " +
                    "One Bengali voice reads the whole message, including English words.",
            ) {
                SwitchRow(
                    title = "Read notifications aloud",
                    checked = Feature.VOICE_MODE in app.enabled,
                    onCheckedChange = viewModel::onVoiceMode,
                )
                if (app.paused) {
                    Text(
                        "Paused from Home",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            Section("Where") {
                Choices(Privacy.entries, { it.title }, { it == s.privacy }) { p ->
                    viewModel.update { it.copy(privacy = p) }
                }
            }
        }
        item {
            Section("Apps", note = "Ongoing, progress, player and call notifications are never read.") {
                Choices(AppScope.entries, { it.title }, { it == s.appScope }) { scope ->
                    viewModel.update { it.copy(appScope = scope) }
                }
                SwitchRow(
                    title = "Also read silent notifications",
                    checked = s.readSilent,
                    onCheckedChange = { on -> viewModel.update { it.copy(readSilent = on) } },
                )
            }
        }
        item {
            Section("Muted apps") {
                if (s.mutedApps.isEmpty()) {
                    Note("No muted apps. Every app in scope is read.")
                } else {
                    Removable(
                        s.mutedApps.map { it to viewModel.label(it) }.sortedBy { it.second.lowercase() }
                    ) { (pkg, _) ->
                        viewModel.toggleMute(pkg)
                    }
                }
                SecondaryButton(onClick = {
                    viewModel.loadApps()
                    pickingApps = true
                }) { Text("Mute an app") }
            }
        }
        item {
            Section(
                "Block rules",
                note = "Skip notifications whose title or text contains these words (any case).",
            ) {
                if (s.blockRules.isNotEmpty()) {
                    Removable(s.blockRules.map { it to "${it.where.title}: ${it.phrase}" }) { (rule, _) ->
                        viewModel.removeRule(rule)
                    }
                }
                OutlinedTextField(
                    value = rulePhrase,
                    onValueChange = { rulePhrase = it },
                    label = { Text("Words to block") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
                Choices(BlockWhere.entries, { it.title }, { it == ruleWhere }) { ruleWhere = it }
                SecondaryButton(
                    enabled = rulePhrase.isNotBlank(),
                    onClick = {
                        viewModel.addRule(rulePhrase, ruleWhere)
                        rulePhrase = ""
                    },
                ) { Text("Add rule") }
            }
        }
        item {
            val typed = senders.split(',').map(String::trim).filter(String::isNotEmpty)
            Section("People") {
                OutlinedTextField(
                    value = senders,
                    onValueChange = { text -> senders = text },
                    label = { Text("Only these people") },
                    placeholder = { Text("Everyone") },
                    supportingText = { Text("Names separated by commas. Leave empty for everyone.") },
                    shape = MaterialTheme.shapes.large,
                    trailingIcon = if (typed != s.senders) {
                        { TextButton(onClick = { viewModel.update { it.copy(senders = typed) } }) { Text("Save") } }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Section(
                "Reading mode",
                note = "Auto converts common Banglish words and preserves other English words. " +
                    "Banglish text converts every Latin word.",
            ) {
                Choices(ReadingMode.entries, { it.title }, { it == s.readingMode }, viewModel::mode)
            }
        }
        item {
            Section("Speech") {
                Text(
                    "Speed: ${"%.1f".format(Locale.getDefault(), s.speechRate)}×",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(value = s.speechRate, onValueChange = { rate ->
                    viewModel.update {
                        it.copy(speechRate = rate)
                    }
                }, valueRange = 0.5f..2f)
                Text("Cut messages after ${s.maxChars} characters", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = s.maxChars.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(maxChars = (v / 20).roundToInt() * 20) } },
                    valueRange = 60f..600f,
                )
            }
        }
        item {
            val busy = state == VoiceState.Preparing || state == VoiceState.Speaking
            Section("Test") {
                PrimaryButton(onClick = if (busy) viewModel::stop else viewModel::onTest) {
                    Text(if (busy) "Stop reading" else "Read a test message")
                }
                Note(
                    when (val current = state) {
                        VoiceState.Idle -> "Ready"
                        VoiceState.Preparing -> "Preparing message…"
                        VoiceState.Speaking -> "Reading…"
                        is VoiceState.Blocked -> current.reason
                        is VoiceState.Failed -> current.reason
                    }
                )
            }
        }
        item {
            Section("System", note = "Voice mode needs notification access to hear other apps.") {
                SecondaryButton(
                    onClick = {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Notification access settings") }
            }
        }
    }
}

@Composable
private fun <T> Choices(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    ChoiceRow {
        items.forEach { item ->
            ChoiceChip(label = label(item), selected = selected(item), onClick = { onClick(item) })
        }
    }
}

/** Saved entries; tapping one removes it. */
@Composable
private fun <T> Removable(items: List<Pair<T, String>>, onRemove: (Pair<T, String>) -> Unit) {
    ChoiceRow {
        items.forEach { item ->
            SecondaryButton(onClick = { onRemove(item) }) {
                Text(item.second)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AppPicker(
    apps: List<InstalledApp>?,
    muted: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Mute apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (apps == null) {
                    Text("Loading apps…", modifier = Modifier.padding(top = 12.dp))
                } else {
                    val shown = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.packageName }) { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = app.packageName in muted,
                                        role = Role.Checkbox,
                                        onValueChange = { onToggle(app.packageName) },
                                    ),
                            ) {
                                Checkbox(checked = app.packageName in muted, onCheckedChange = null)
                                Text(app.label)
                            }
                        }
                    }
                }
            }
        },
    )
}
