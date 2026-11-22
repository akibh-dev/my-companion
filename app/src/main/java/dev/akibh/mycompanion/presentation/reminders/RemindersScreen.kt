package dev.akibh.mycompanion.presentation.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.presentation.common.OutlinedStateTextField
import dev.akibh.mycompanion.ui.components.AddButton
import dev.akibh.mycompanion.ui.components.AddButtonClearance
import dev.akibh.mycompanion.ui.components.ChoiceChip
import dev.akibh.mycompanion.ui.components.ChoiceRow
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.PrimaryButton
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SectionLabel
import dev.akibh.mycompanion.ui.components.rememberChromeListState
import dev.akibh.mycompanion.ui.theme.CompanionRed
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
    onTogglePause: (Long) -> Unit = {},
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
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberChromeListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PageMargin,
                top = 12.dp,
                end = PageMargin,
                // Room for the add button, which itself floats above the nav bar.
                bottom = ADD_BUTTON_LIFT + AddButtonClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                item { Note("Precise timing is unavailable. Reminders may arrive later than the selected time.") }
            }
            item {
                SectionLabel("Upcoming", Modifier.padding(start = 8.dp, top = SectionGap - 12.dp))
            }
            if (state.upcoming.isEmpty()) {
                item {
                    EmptyState(
                        DotSymbol.BELL,
                        "Nothing scheduled",
                        "Set a little nudge for later.",
                    )
                }
            } else {
                // The soonest reminder that will actually ring is the one highlighted.
                val nextId = state.upcoming.firstOrNull { !it.paused }?.id
                items(state.upcoming, key = { it.id }) { reminder ->
                    UpcomingRow(
                        reminder,
                        next = reminder.id == nextId,
                        onTogglePause = { onTogglePause(reminder.id) },
                        onEdit = { onEditReminder(reminder.id) }
                    )
                }
            }
        }
        AddButton(
            "Add reminder",
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = ADD_BUTTON_LIFT),
        )
    }
    if (creating || state.form.editingId != null) {
        val dismiss = {
            creating = false
            if (state.form.editingId != null) onCancelEdit()
        }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            // Even fully open, the sheet stops under the status bar with a gap, so its rounded top still shows.
            modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            // The sheet is as tall as its content (up to the screen). The fields scroll; the header and the
            // main button stay put, so "Set reminder" is always one tap away.
            Column(Modifier.fillMaxWidth()) {
                // No close button: swipe the sheet down, tap outside it, or go back.
                Text(
                    if (state.form.editingId != null) "Edit reminder" else "New reminder",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp).semantics { heading() },
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ComposerSections(
                        state = state, title = title, message = message,
                        onChoiceChange = onChoiceChange, onPickTime = onPickTime,
                        onRepeatChange = onRepeatChange, onDayToggle = onDayToggle,
                        onEveryChange = onEveryChange, onContactChange = onContactChange,
                        onPickDateTime = onPickDateTime,
                        onCustomNumberChange = onCustomNumberChange, onCustomUnitChange = onCustomUnitChange,
                        soundContent = soundContent,
                    )
                    state.form.editingId?.let { id ->
                        val paused = state.upcoming.firstOrNull { it.id == id }?.paused == true
                        ManageSection(
                            paused = paused,
                            onTogglePause = { onTogglePause(id) },
                            onDelete = {
                                onCancelReminder(id)
                                dismiss()
                            },
                        )
                    }
                }
                SubmitBar(state, title, message, onSubmit)
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

@Composable
private fun ComposerSections(
    state: RemindersUiState,
    title: TextFieldState,
    message: TextFieldState,
    onChoiceChange: (WhenChoice) -> Unit,
    onPickTime: () -> Unit,
    onRepeatChange: (RepeatChoice) -> Unit,
    onDayToggle: (DayOfWeek) -> Unit,
    onEveryChange: (Int) -> Unit,
    onContactChange: (Long?) -> Unit,
    onPickDateTime: () -> Unit,
    onCustomNumberChange: (String) -> Unit,
    onCustomUnitChange: (RepeatUnit) -> Unit,
    soundContent: @Composable () -> Unit,
) {
    val form = state.form
    Section("What") {
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
    }
    Section("When") {
        ChoiceRow(Modifier.fillMaxWidth()) {
            state.presets.forEach { chip ->
                ChoiceChip(
                    label = chip.label,
                    selected = chip.selected,
                    onClick = { onChoiceChange(chip.choice) },
                )
            }
            ChoiceChip(
                label = if (form.choice is WhenChoice.At) state.pickedTimeLabel else "Pick time",
                selected = form.choice is WhenChoice.At,
                onClick = onPickTime,
            )
            ChoiceChip(
                label = if (form.choice is WhenChoice.OnDateTime) state.pickedTimeLabel else "Pick date",
                selected = form.choice is WhenChoice.OnDateTime,
                onClick = onPickDateTime,
            )
        }
        if (state.nextOccurrences.isNotEmpty()) {
            Text(
                "Next: " + state.nextOccurrences.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Section("Repeat") {
        RepeatPicker(state, onRepeatChange, onDayToggle, onEveryChange, onCustomNumberChange, onCustomUnitChange)
    }
    Section(if (state.contacts.isNotEmpty()) "Sound & call" else "Sound") {
        soundContent()
        if (state.contacts.isNotEmpty()) CallButtonPicker(state, onContactChange)
    }
    state.validationMessage?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** The main action, pinned under the scrolling form. */
@Composable
private fun SubmitBar(state: RemindersUiState, title: TextFieldState, message: TextFieldState, onSubmit: () -> Unit) {
    val form = state.form
    // Reading the text here recomposes only when it changes; nothing is sent back anywhere.
    val canSubmit = form.canSubmit(title.text, message.text) && state.validationMessage == null
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PrimaryButton(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
                .heightIn(min = 56.dp),
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = if (canSubmit) CompanionRed else LocalContentColor.current
            )
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    form.editingId != null -> "Save changes"
                    form.choice == WhenChoice.Now -> "Send now"
                    else -> "Set reminder"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Never / every day / on chosen days / every month / every few hours, plus the details for the choice. */
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
    ChoiceRow(Modifier.fillMaxWidth()) {
        RepeatChoice.entries.forEach { choice ->
            ChoiceChip(
                label = choice.label,
                selected = form.repeat == choice,
                onClick = { onRepeatChange(choice) },
            )
        }
    }
    when (form.repeat) {
        RepeatChoice.DAYS -> ChoiceRow(Modifier.fillMaxWidth()) {
            state.dayChips.forEach { chip ->
                ChoiceChip(
                    label = chip.label,
                    selected = chip.selected,
                    onClick = { onDayToggle(chip.day) },
                    multiple = true,
                )
            }
        }
        RepeatChoice.EVERY -> ChoiceRow(Modifier.fillMaxWidth()) {
            state.everyChips.forEach { chip ->
                ChoiceChip(
                    label = chip.label,
                    selected = chip.selected,
                    onClick = { onEveryChange(chip.minutes) },
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
            ChoiceRow(Modifier.fillMaxWidth()) {
                RepeatUnit.entries.forEach { unit ->
                    ChoiceChip(
                        label = unit.title,
                        selected = unit == form.customUnit,
                        onClick = { onCustomUnitChange(unit) },
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

/** Optionally attach a "Call <person>" button to the notification. */
@Composable
private fun CallButtonPicker(state: RemindersUiState, onContactChange: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = state.contacts.firstOrNull { it.id == state.form.contactId }
    Box {
        SecondaryButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
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

/**
 * One scheduled reminder: the time large on the left, what it's about on the right, and the repeat and
 * call button as small labels. Tapping the card edits it; the soonest one is filled, like an active tile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpcomingRow(reminder: UpcomingReminder, next: Boolean, onTogglePause: () -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = if (next) colors.primary else colors.surfaceContainer
    val content = if (next) colors.onPrimary else colors.onSurface
    val muted = content.copy(alpha = .68f)
    // "Today, 14:58" → "Today" over "14:58". Dates like "Mon 28 Oct" have no comma of their own.
    val day = reminder.whenText.substringBeforeLast(", ", "")
    val time = reminder.whenText.substringAfterLast(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .clickable(onClickLabel = "Edit ${reminder.title}", onClick = onEdit)
            // Paused reminders fade back; they're still there, just not ringing.
            .graphicsLayer { alpha = if (reminder.paused) .45f else 1f }
            .padding(start = 20.dp, top = 18.dp, bottom = 18.dp, end = 14.dp),
    ) {
        Column(Modifier.width(78.dp)) {
            Text(
                day.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = if (next) CompanionRed else muted,
            )
            Text(time, style = MaterialTheme.typography.headlineSmall, color = content, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                reminder.title,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (reminder.message.isNotBlank()) {
                Text(
                    reminder.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (reminder.paused || reminder.repeatText != null || reminder.contactName != null) {
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (reminder.paused) InfoPill(painterResource(R.drawable.ic_pause), "Paused", content)
                    reminder.repeatText?.let { InfoPill(painterResource(R.drawable.ic_repeat), it, content) }
                    reminder.contactName?.let {
                        InfoPill(
                            rememberVectorPainter(Icons.Filled.Call),
                            "Call $it",
                            content
                        )
                    }
                }
            }
        }
        // A round dotted play/pause control on a soft borderless disc, centred on the card's height.
        Box(
            Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically)
                .size(48.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = .12f))
                .clickable(onClick = onTogglePause)
                .semantics {
                    contentDescription = if (reminder.paused) "Resume ${reminder.title}" else "Pause ${reminder.title}"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            DotIcon(
                if (reminder.paused) DotSymbol.PLAY else DotSymbol.PAUSE,
                Modifier.size(22.dp),
                color = if (reminder.paused) CompanionRed else content,
            )
        }
    }
}

/** A small status label on a reminder card, e.g. the repeat or the call button: icon and text, no pill. */
@Composable
private fun InfoPill(icon: Painter, text: String, color: Color) {
    Row(
        Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Shown when editing an existing reminder: pause or resume it, or delete it for good. */
@Composable
private fun ManageSection(paused: Boolean, onTogglePause: () -> Unit, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Section("Manage") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(onClick = onTogglePause, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                DotIcon(if (paused) DotSymbol.PLAY else DotSymbol.PAUSE, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (paused) "Resume" else "Pause")
            }
            SecondaryButton(
                onClick = { confirming = true },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                destructive = true,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Delete")
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete this reminder?") },
            text = { Text("It won't ring again. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep") } },
        )
    }
}

/** Lifts the add button above the floating nav bar, as on People. */
private val ADD_BUTTON_LIFT = 120.dp
