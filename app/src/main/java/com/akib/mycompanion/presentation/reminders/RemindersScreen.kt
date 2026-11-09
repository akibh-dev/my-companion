package com.akib.mycompanion.presentation.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.presentation.common.OutlinedStateTextField
import com.akib.mycompanion.ui.components.DotIcon
import com.akib.mycompanion.ui.components.DotSymbol
import com.akib.mycompanion.ui.components.rememberChromeListState
import java.time.DayOfWeek

/**
 * Reminders screen: compose a notification on top, upcoming ones below. The typed [title] and [message]
 * are Compose text state (owned by the ViewModel); everything else comes in through [state].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    state: RemindersUiState,
    title: TextFieldState,
    message: TextFieldState,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onChoiceChange: (WhenChoice) -> Unit,
    onPickTime: () -> Unit,
    onRepeatChange: (RepeatChoice) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onEveryChange: (Int) -> Unit,
    onContactChange: (Long?) -> Unit,
    onSubmit: () -> Unit,
    onCancelReminder: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onPickDateTime: () -> Unit = {},
    onCustomNumberChange: (String) -> Unit = {},
    onCustomUnitChange: (RepeatUnit) -> Unit = {},
    soundContent: @Composable () -> Unit = {},
    onEditReminder: (Long) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    preciseTimingAllowed: Boolean = true,
    submissionCount: Int = 0,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var handledSubmission by rememberSaveable { mutableIntStateOf(submissionCount) }
    LaunchedEffect(submissionCount) {
        if (submissionCount != handledSubmission) {
            creating = false
            handledSubmission = submissionCount
        }
    }
    LazyColumn(
        state = rememberChromeListState(),
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "For the things worth coming back to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!notificationsAllowed) {
            item { NotificationsOffBanner(onAllowNotifications) }
        }
        if (!preciseTimingAllowed) {
            item { Text("Precise timing is unavailable. Reminders may arrive later than the selected time.") }
        }
        item {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Create a reminder")
            }
        }
        item {
            Text(
                "Upcoming",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.upcoming.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DotIcon(DotSymbol.BELL, Modifier.size(48.dp))
                    Text("Nothing scheduled", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Set a little nudge for later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.upcoming, key = { it.id }) { reminder ->
                UpcomingRow(
                    reminder,
                    onCancel = { onCancelReminder(reminder.id) },
                    onEdit = { onEditReminder(reminder.id) }
                )
            }
        }
    }
    if (creating || state.form.editingId != null) {
        val dismiss = {
            creating = false
            if (state.form.editingId != null) onCancelEdit()
        }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxHeight(.94f),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.form.editingId != null) "Edit reminder" else "New reminder",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = dismiss) { Icon(Icons.Filled.Close, "Close reminder editor") }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            ) {
                ComposerCard(
                    state = state, title = title, message = message,
                    onChoiceChange = onChoiceChange, onPickTime = onPickTime,
                    onRepeatChange = onRepeatChange, onDayToggle = onDayToggle,
                    onEveryChange = onEveryChange, onContactChange = onContactChange,
                    onSubmit = onSubmit, onPickDateTime = onPickDateTime,
                    onCustomNumberChange = onCustomNumberChange, onCustomUnitChange = onCustomUnitChange,
                    soundContent = soundContent, onCancelEdit = onCancelEdit,
                )
            }
        }
    }
}

@Composable
private fun NotificationsOffBanner(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(
                "Notifications are off, so reminders can't show.",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            TextButton(onClick = onAllow) { Text("Allow") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerCard(
    state: RemindersUiState,
    title: TextFieldState,
    message: TextFieldState,
    onChoiceChange: (WhenChoice) -> Unit,
    onPickTime: () -> Unit,
    onRepeatChange: (RepeatChoice) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onEveryChange: (Int) -> Unit,
    onContactChange: (Long?) -> Unit,
    onSubmit: () -> Unit,
    onPickDateTime: () -> Unit,
    onCustomNumberChange: (String) -> Unit,
    onCustomUnitChange: (RepeatUnit) -> Unit,
    soundContent: @Composable () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val form = state.form
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedStateTextField(
                state = title,
                label = "Title",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedStateTextField(
                state = message,
                label = "Message",
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.editingId != null) {
                Text("Editing reminder", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onCancelEdit) { Text("Cancel editing") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("When", style = MaterialTheme.typography.labelLarge)
            ChipRow {
                state.presets.forEach { chip ->
                    FilterChip(
                        selected = chip.selected,
                        onClick = { onChoiceChange(chip.choice) },
                        label = { Text(chip.label) },
                    )
                }
                FilterChip(
                    selected = form.choice is WhenChoice.At,
                    onClick = onPickTime,
                    label = { Text(state.pickedTimeLabel) },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                )
            }

            OutlinedButton(onClick = onPickDateTime) {
                Text(if (form.choice is WhenChoice.OnDateTime) state.pickedTimeLabel else "Pick first date and time")
            }
            RepeatPicker(state, onRepeatChange, onDayToggle, onEveryChange, onCustomNumberChange, onCustomUnitChange)
            state.validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.nextOccurrences.isNotEmpty()) {
                Text("Next notifications", style = MaterialTheme.typography.labelLarge)
                state.nextOccurrences.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            soundContent()

            if (state.contacts.isNotEmpty()) {
                CallButtonPicker(state, onContactChange)
            }

            // Reading the text here recomposes only when it changes; nothing is sent back anywhere.
            val canSubmit = form.canSubmit(title.text, message.text) && state.validationMessage == null
            Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        form.editingId != null -> "Save changes"
                        form.choice == WhenChoice.Now -> "Send now"
                        else -> "Set reminder"
                    }
                )
            }
        }
    }
}

/** Never / every day / on chosen days / every month / every few hours, plus the details for the choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatPicker(
    state: RemindersUiState,
    onRepeatChange: (RepeatChoice) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onEveryChange: (Int) -> Unit,
    onCustomNumberChange: (String) -> Unit,
    onCustomUnitChange: (RepeatUnit) -> Unit,
) {
    val form = state.form
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("Repeat", style = MaterialTheme.typography.labelLarge)
    ChipRow {
        RepeatChoice.entries.forEach { choice ->
            FilterChip(
                selected = form.repeat == choice,
                onClick = { onRepeatChange(choice) },
                label = { Text(choice.label) },
                leadingIcon = if (choice == RepeatChoice.NEVER) {
                    null
                } else {
                    { Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp)) }
                },
            )
        }
    }
    when (form.repeat) {
        RepeatChoice.DAYS -> ChipRow {
            state.dayChips.forEach { chip ->
                FilterChip(selected = chip.selected, onClick = { onDayToggle(chip.day) }, label = { Text(chip.label) })
            }
        }
        RepeatChoice.EVERY -> ChipRow {
            state.everyChips.forEach { chip ->
                FilterChip(
                    selected = chip.selected,
                    onClick = { onEveryChange(chip.minutes) },
                    label = { Text(chip.label) }
                )
            }
        }
        RepeatChoice.CUSTOM -> {
            OutlinedTextField(
                value = form.customNumber,
                onValueChange = onCustomNumberChange,
                label = { Text("Repeat every") },
                singleLine = true,
                isError = form.customCount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            ChipRow {
                RepeatUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = unit == form.customUnit,
                        onClick = { onCustomUnitChange(unit) },
                        label = { Text(unit.title) }
                    )
                }
            }
            if (form.customUnit == RepeatUnit.MINUTES && (form.customCount ?: 15) < 15) {
                Text(
                    "Short intervals may be delayed while the phone sleeps.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        else -> Unit
    }
    val summary = when {
        form.repeat == RepeatChoice.DAYS && form.days.isEmpty() -> "Pick at least one day"
        else -> state.repeatSummary
    }
    if (summary != null && form.repeat != RepeatChoice.NEVER) {
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Optionally attach a "Call <person>" button to the notification. */
@Composable
private fun CallButtonPicker(state: RemindersUiState, onContactChange: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = state.contacts.firstOrNull { it.id == state.form.contactId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(selected?.let { "Call button: ${it.name}" } ?: "Add a call button (optional)")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("No call button") },
                onClick = {
                    open = false
                    onContactChange(null)
                },
            )
            state.contacts.forEach { contact ->
                DropdownMenuItem(
                    text = { Text(contact.name) },
                    onClick = {
                        open = false
                        onContactChange(contact.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun UpcomingRow(reminder: UpcomingReminder, onCancel: () -> Unit, onEdit: () -> Unit) {
    val details = buildList {
        add(reminder.whenText)
        reminder.repeatText?.let(::add)
        reminder.contactName?.let { add("Call $it") }
    }.joinToString(" · ")
    ElevatedCard(Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            headlineContent = { Text(reminder.title) },
            supportingContent = {
                Column {
                    if (reminder.message.isNotBlank()) Text(reminder.message, maxLines = 2)
                    Text(details, color = MaterialTheme.colorScheme.primary)
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel ${reminder.title}")
                    }
                }
            },
        )
    }
}
