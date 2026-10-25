package com.akib.mycompanion.context.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.InstalledApp
import com.akib.mycompanion.context.device.hasUsageAccess
import com.akib.mycompanion.context.device.launchableApps
import com.akib.mycompanion.context.domain.DndMode
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.domain.VolumeStream
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RuleDraft(
    val id: Long = 0,
    val name: String = "",
    val enabled: Boolean = true,
    val order: Int = 0,
    val triggers: List<Trigger> = emptyList(),
    val actions: List<RuleAction> = emptyList(),
) {
    val problem: String?
        get() = when {
            name.isBlank() -> "Give the rule a name"
            triggers.isEmpty() -> "Add at least one \"when\""
            actions.isEmpty() -> "Add at least one change"
            actions.map { it.key }.toSet().size < actions.size -> "Two changes set the same thing"
            else -> null
        }
}

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ContextRepository,
) : ViewModel() {
    private val ruleId: Long? = savedState.get<String>("id")?.toLongOrNull()
    private val mutableDraft = MutableStateFlow(RuleDraft())
    val draft: StateFlow<RuleDraft> = mutableDraft.asStateFlow()
    val places: StateFlow<List<Place>> = repository.places.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                repository.rule(ruleId)?.let {
                    mutableDraft.value = RuleDraft(it.id, it.name, it.enabled, it.order, it.triggers, it.actions)
                }
            }
        }
    }

    val isNew: Boolean get() = ruleId == null

    fun onName(name: String) = mutableDraft.update { it.copy(name = name) }

    fun onTrigger(index: Int?, trigger: Trigger) = mutableDraft.update { d ->
        d.copy(
            triggers = d.triggers.replaceOrAdd(index, trigger)
        )
    }

    fun onRemoveTrigger(index: Int) = mutableDraft.update { d ->
        d.copy(
            triggers = d.triggers.filterIndexed { i, _ -> i != index }
        )
    }

    fun onAction(index: Int?, action: RuleAction) = mutableDraft.update { d ->
        d.copy(
            actions = d.actions.replaceOrAdd(index, action)
        )
    }

    fun onRemoveAction(index: Int) = mutableDraft.update { d ->
        d.copy(
            actions = d.actions.filterIndexed { i, _ -> i != index }
        )
    }

    fun save(onDone: () -> Unit) {
        val d = mutableDraft.value
        if (d.problem != null) return
        viewModelScope.launch {
            repository.save(Rule(d.id, d.name.trim(), d.enabled, d.order, d.triggers, d.actions))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            ruleId?.let { repository.deleteRule(it) }
            onDone()
        }
    }
}

/** Create or edit a rule (plan §11, "rule editor"). */
@Composable
fun RuleEditorRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RuleEditorViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val placesById = places.associateBy { it.id }
    var editingTrigger by remember { mutableStateOf<Pair<Int?, Trigger>?>(null) }
    var editingAction by remember { mutableStateOf<Pair<Int?, RuleAction>?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::onName,
                label = { Text("Rule name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Text("When (all of these)", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(draft.triggers) { index, trigger ->
            Line(
                Describe.trigger(trigger, placesById),
                { editingTrigger = index to trigger }
            ) { viewModel.onRemoveTrigger(index) }
        }
        item {
            AddMenu(
                "Add a \"when\"",
                TRIGGER_KINDS.map { (label, make) -> label to { editingTrigger = null to make(places) } }
            )
        }
        item { Text("Then change", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(draft.actions) { index, action ->
            Line(Describe.action(action), { editingAction = index to action }) { viewModel.onRemoveAction(index) }
        }
        item {
            AddMenu("Add a change", ACTION_KINDS.map { (label, make) -> label to { editingAction = null to make() } })
        }
        item {
            Text(
                "When the rule stops applying, changed settings go back to how they were, " +
                    "unless you changed them yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            draft.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.save(onDone) }, enabled = draft.problem == null) { Text("Save") }
                if (!viewModel.isNew) OutlinedButton(onClick = { viewModel.delete(onDone) }) { Text("Delete") }
            }
        }
    }

    editingTrigger?.let { (index, trigger) ->
        TriggerDialog(trigger, places, onDismiss = { editingTrigger = null }) {
            viewModel.onTrigger(index, it)
            editingTrigger = null
        }
    }
    editingAction?.let { (index, action) ->
        ActionDialog(action, onDismiss = { editingAction = null }) {
            viewModel.onAction(index, it)
            editingAction = null
        }
    }
}

/** Replaces the item at [index], or adds it at the end when [index] is null. */
private fun <T> List<T>.replaceOrAdd(index: Int?, item: T): List<T> =
    if (index == null) this + item else toMutableList().also { it[index] = item }

private val TRIGGER_KINDS: List<Pair<String, (List<Place>) -> Trigger>> = listOf(
    "Time of day" to { _ -> Trigger.TimeWindow(Describe.WORK_WEEK_BD, LocalTime.of(9, 0), LocalTime.of(17, 0)) },
    "At a place" to { places -> Trigger.AtPlace(places.firstOrNull()?.id ?: -1) },
    "Charging" to { _ -> Trigger.Charging(true) },
    "Bluetooth device" to { _ -> Trigger.BluetoothDevice("") },
    "Wi-Fi network" to { _ -> Trigger.WifiNetwork("") },
    "App in use" to { _ -> Trigger.AppInForeground("", "") },
)

private val ACTION_KINDS: List<Pair<String, () -> RuleAction>> = listOf(
    "Sound mode" to { RuleAction.Sound(SoundMode.VIBRATE) },
    "Do Not Disturb" to { RuleAction.DoNotDisturb(DndMode.PRIORITY) },
    "Volume" to { RuleAction.Volume(VolumeStream.MEDIA, 30) },
    "Wi-Fi" to { RuleAction.Wifi(true) },
    "Bluetooth" to { RuleAction.Bluetooth(true) },
    "Brightness" to { RuleAction.Brightness(50) },
)

@Composable
private fun Line(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(text) },
            trailingContent = {
                IconButton(
                    onClick = onRemove
                ) { Icon(Icons.Filled.Close, contentDescription = "Remove $text") }
            },
        )
    }
}

@Composable
private fun AddMenu(label: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(label) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        options.forEach { (text, pick) ->
            DropdownMenuItem(text = { Text(text) }, onClick = {
                open = false
                pick()
            })
        }
    }
}

@Composable
private fun TriggerDialog(initial: Trigger, places: List<Place>, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    var trigger by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    val valid = when (val t = trigger) {
        is Trigger.TimeWindow -> t.days.isNotEmpty() && t.start != t.end
        is Trigger.BluetoothDevice -> t.name.isNotBlank()
        is Trigger.WifiNetwork -> t.ssid.isNotBlank()
        is Trigger.AtPlace -> places.any { it.id == t.placeId }
        is Trigger.Charging -> true
        is Trigger.AppInForeground -> t.packageName.isNotBlank()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(trigger) }, enabled = valid) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("When") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val t = trigger) {
                    is Trigger.TimeWindow -> {
                        Chips(DayOfWeek.entries, { Describe.short(it) }, { it in t.days }) { day ->
                            trigger = t.copy(days = if (day in t.days) t.days - day else t.days + day)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { pickTime(context, t.start) { trigger = t.copy(start = it) } }
                            ) { Text("From ${t.start}") }
                            OutlinedButton(
                                onClick = { pickTime(context, t.end) { trigger = t.copy(end = it) } }
                            ) { Text("To ${t.end}") }
                        }
                        if (t.end < t.start) Text("Runs past midnight", style = MaterialTheme.typography.bodySmall)
                    }
                    is Trigger.Charging -> Chips(
                        listOf(true, false),
                        { if (it) "Charging" else "Not charging" },
                        { it == t.charging }
                    ) {
                        trigger = Trigger.Charging(it)
                    }
                    is Trigger.BluetoothDevice -> OutlinedTextField(
                        value = t.name,
                        onValueChange = { trigger = Trigger.BluetoothDevice(it) },
                        label = { Text("Device name, as shown in Bluetooth settings") },
                        singleLine = true,
                    )
                    is Trigger.WifiNetwork -> OutlinedTextField(
                        value = t.ssid,
                        onValueChange = { trigger = Trigger.WifiNetwork(it) },
                        label = { Text("Wi-Fi network name") },
                        singleLine = true,
                    )
                    is Trigger.AtPlace -> {
                        if (places.isEmpty()) {
                            Text("Save a place first (Rules → Saved places).")
                        } else {
                            Chips(places, { it.name }, { it.id == t.placeId }) { trigger = t.copy(placeId = it.id) }
                            Chips(
                                listOf(true, false),
                                { if (it) "Inside" else "Away" },
                                { it == t.inside }
                            ) { trigger = t.copy(inside = it) }
                        }
                    }
                    is Trigger.AppInForeground -> AppPicker(t.packageName) {
                        trigger = Trigger.AppInForeground(it.packageName, it.label)
                    }
                }
            }
        },
    )
}

@Composable
private fun ActionDialog(initial: RuleAction, onDismiss: () -> Unit, onSave: (RuleAction) -> Unit) {
    var action by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(action) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val a = action) {
                    is RuleAction.Sound -> Chips(
                        SoundMode.entries,
                        Describe::sound,
                        { it == a.mode }
                    ) { action = RuleAction.Sound(it) }
                    is RuleAction.DoNotDisturb -> Chips(DndMode.entries, Describe::dnd, {
                        it == a.mode
                    }) { action = RuleAction.DoNotDisturb(it) }
                    is RuleAction.Volume -> {
                        Chips(
                            VolumeStream.entries,
                            Describe::stream,
                            { it == a.stream }
                        ) { action = a.copy(stream = it) }
                        Text("${a.percent}%")
                        Slider(
                            value = a.percent.toFloat(),
                            onValueChange = { action = a.copy(percent = it.toInt()) },
                            valueRange = 0f..100f
                        )
                    }
                    is RuleAction.Wifi -> {
                        Chips(
                            listOf(true, false),
                            { if (it) "On" else "Off" },
                            { it == a.on }
                        ) { action = RuleAction.Wifi(it) }
                        Text(
                            "Android doesn't let apps switch Wi-Fi: you'll get a notification to tap.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is RuleAction.Bluetooth -> {
                        Chips(
                            listOf(true, false),
                            { if (it) "On" else "Off" },
                            { it == a.on }
                        ) { action = RuleAction.Bluetooth(it) }
                        Text(
                            "Android doesn't let apps switch Bluetooth: you'll get a notification to tap.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is RuleAction.Brightness -> {
                        Text("${a.percent}%")
                        Slider(value = a.percent.toFloat(), onValueChange = {
                            action = RuleAction.Brightness(it.toInt())
                        }, valueRange = 0f..100f)
                    }
                }
            }
        },
    )
}

@Composable
private fun <T> Chips(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            FilterChip(selected = selected(item), onClick = {
                onClick(item)
            }, label = { Text(label(item)) })
        }
    }
}

private fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(LocalTime.of(h, m)) },
        initial.hour,
        initial.minute,
        DateFormat.is24HourFormat(context)
    ).show()
}

/** Choose the app for an "App in use" trigger; reminds you that it needs Usage access. */
@Composable
private fun AppPicker(selected: String, onPick: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchableApps(context) } }

    if (!hasUsageAccess(context)) {
        Text(
            "Needs Usage access so the app can see which app is open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }) { Text("Allow usage access") }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Search apps") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val shown = apps.orEmpty().filter { it.label.contains(query.trim(), ignoreCase = true) }
    when {
        apps == null -> Text("Loading apps…")
        shown.isEmpty() -> Text("No app matches")
        else -> LazyColumn(Modifier.heightIn(max = 280.dp)) {
            items(shown, key = { it.packageName }) { app ->
                FilterChip(
                    selected = app.packageName == selected,
                    onClick = { onPick(app) },
                    label = { Text(app.label) },
                )
            }
        }
    }
}
