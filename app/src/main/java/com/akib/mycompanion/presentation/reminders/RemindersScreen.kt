package com.akib.mycompanion.presentation.reminders

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.presentation.common.OutlinedStateTextField
import java.time.DayOfWeek

/**
 * Reminders screen: compose a notification on top, upcoming ones below. The typed [title] and [message]
 * are Compose text state (owned by the ViewModel); everything else comes in through [state].
 */
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
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!notificationsAllowed) {
            item { NotificationsOffBanner(onAllowNotifications) }
        }
        item {
            ComposerCard(
                state = state,
                title = title,
                message = message,
                onChoiceChange = onChoiceChange,
                onPickTime = onPickTime,
                onRepeatChange = onRepeatChange,
                onDayToggle = onDayToggle,
                onEveryChange = onEveryChange,
                onContactChange = onContactChange,
                onSubmit = onSubmit,
            )
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
                Text(
                    "Nothing scheduled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.upcoming, key = { it.id }) { reminder ->
                UpcomingRow(reminder, onCancel = { onCancelReminder(reminder.id) })
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

            Text("When", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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

            RepeatPicker(state, onRepeatChange, onDayToggle, onEveryChange)

            if (state.contacts.isNotEmpty()) {
                CallButtonPicker(state, onContactChange)
            }

            // Reading the text here recomposes only when it changes; nothing is sent back anywhere.
            val canSubmit = form.canSubmit(title.text, message.text)
            Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Notifications, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (form.choice == WhenChoice.Now) "Send now" else "Set reminder")
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
) {
    val form = state.form
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

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
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
private fun UpcomingRow(reminder: UpcomingReminder, onCancel: () -> Unit) {
    val details = buildList {
        add(reminder.whenText)
        reminder.repeatText?.let(::add)
        reminder.contactName?.let { add("Call $it") }
    }.joinToString(" · ")
    ElevatedCard(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(reminder.title) },
            supportingContent = {
                Column {
                    if (reminder.message.isNotBlank()) Text(reminder.message, maxLines = 2)
                    Text(details, color = MaterialTheme.colorScheme.primary)
                }
            },
            trailingContent = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel ${reminder.title}")
                }
            },
        )
    }
}
