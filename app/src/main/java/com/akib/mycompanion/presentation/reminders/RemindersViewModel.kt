package com.akib.mycompanion.presentation.reminders

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.domain.usecase.CancelReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "When" chips. */
sealed interface WhenChoice {
    data object Now : WhenChoice

    data class InMinutes(val minutes: Int) : WhenChoice

    data class At(val hour: Int, val minute: Int) : WhenChoice

    fun toReminderTime(): ReminderTime = when (this) {
        Now -> ReminderTime.InMinutes(0)
        is InMinutes -> ReminderTime.InMinutes(minutes)
        is At -> ReminderTime.AtTime(hour, minute)
    }

    companion object {
        // A getter, not a stored list: with Kotlin 2.2's JVM default methods, creating `Now` first
        // initialises this interface, and a stored list would capture `Now` while it's still null.
        val presets: List<WhenChoice> get() = listOf(Now, InMinutes(5), InMinutes(15), InMinutes(30), InMinutes(60))
    }
}

/** The "Repeat" chips. Days and interval are picked under the chip. */
enum class RepeatChoice(val label: String) {
    NEVER("Never"),
    DAILY("Every day"),
    DAYS("On days"),
    MONTHLY("Every month"),
    EVERY("Every few hours"),
}

/**
 * Everything on the form except the typed text: title and message live in [RemindersViewModel.title]
 * and [RemindersViewModel.message] (Compose text state, see device finding #5).
 */
data class ReminderForm(
    val choice: WhenChoice = WhenChoice.Now,
    val repeat: RepeatChoice = RepeatChoice.NEVER,
    /** For [RepeatChoice.DAYS]. */
    val days: Set<DayOfWeek> = emptySet(),
    /** For [RepeatChoice.EVERY]. */
    val everyMinutes: Int = 60,
    val contactId: Long? = null,
) {
    /** "On days" needs at least one day ticked. */
    val repeatComplete: Boolean get() = repeat != RepeatChoice.DAYS || days.isNotEmpty()

    /** A reminder needs a title or a message, and a complete repeat choice. */
    fun canSubmit(title: CharSequence, message: CharSequence): Boolean =
        (title.isNotBlank() || message.isNotBlank()) && repeatComplete
}

data class UpcomingReminder(
    val id: Long,
    val title: String,
    val message: String,
    val whenText: String,
    /** "Every day", "Every Sun, Thu"…; null for a one-off. */
    val repeatText: String?,
    val contactName: String?,
)

/** One "When" chip, ready to draw. */
data class ChoiceChip(val choice: WhenChoice, val label: String, val selected: Boolean)

/** A weekday toggle under "On days". */
data class DayChip(val day: DayOfWeek, val label: String, val selected: Boolean)

/** An interval under "Every few hours". */
data class EveryChip(val minutes: Int, val label: String, val selected: Boolean)

data class RemindersUiState(
    val form: ReminderForm = ReminderForm(),
    val presets: List<ChoiceChip> = emptyList(),
    /** Label of the "Pick time" chip: the picked time once there is one. */
    val pickedTimeLabel: String = "Pick time",
    val dayChips: List<DayChip> = emptyList(),
    val everyChips: List<EveryChip> = emptyList(),
    /** What the chosen repeat means in words ("Every month on the 24th"); null for a one-off. */
    val repeatSummary: String? = null,
    val upcoming: List<UpcomingReminder> = emptyList(),
    val contacts: List<Contact> = emptyList(),
)

sealed interface RemindersEvent {
    data class Message(val text: String) : RemindersEvent
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    reminders: ReminderRepository,
    contacts: ContactRepository,
    private val scheduleReminder: ScheduleReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
    private val formatter: ReminderTimeFormatter,
    private val calculator: ReminderTimeCalculator,
) : ViewModel() {

    /**
     * The typed title and message. Owned by Compose text state, which the keyboard writes to directly;
     * a StateFlow round trip here made fast typing drop or reorder letters (device finding #5).
     */
    val title = TextFieldState()
    val message = TextFieldState()

    private val form = MutableStateFlow(ReminderForm())
    private val eventChannel = Channel<RemindersEvent>(Channel.BUFFERED)
    val events: Flow<RemindersEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<RemindersUiState> =
        combine(form, reminders.observeUpcoming(), contacts.observeAll()) { form, upcoming, people ->
            val names = people.associate { it.id to it.name }
            val picked = form.choice as? WhenChoice.At
            RemindersUiState(
                form = form,
                presets = WhenChoice.presets.map { ChoiceChip(it, choiceLabel(it), selected = it == form.choice) },
                pickedTimeLabel = picked?.let(::choiceLabel) ?: "Pick time",
                dayChips = formatter.week.map { DayChip(it, formatter.dayShort(it), selected = it in form.days) },
                everyChips = EVERY_OPTIONS.map {
                    EveryChip(
                        it,
                        formatter.duration(it),
                        selected = it == form.everyMinutes
                    )
                },
                repeatSummary = form.toRepeat()?.let(formatter::repeat),
                contacts = people,
                upcoming = upcoming.map { reminder ->
                    UpcomingReminder(
                        id = reminder.id,
                        title = reminder.title,
                        message = reminder.message,
                        whenText = formatter.format(reminder.triggerAtMillis),
                        repeatText = formatter.repeat(reminder.repeat),
                        contactName = reminder.contactId?.let(names::get),
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RemindersUiState())

    fun onChoiceChange(choice: WhenChoice) = form.update { it.copy(choice = choice) }

    fun onRepeatChange(repeat: RepeatChoice) = form.update { current ->
        // "On days" starts with the day it would first fire, so it's never empty by surprise.
        val days = if (repeat == RepeatChoice.DAYS && current.days.isEmpty()) {
            setOf(
                firstDate(current).dayOfWeek
            )
        } else {
            current.days
        }
        current.copy(repeat = repeat, days = days)
    }

    fun onDayToggle(day: DayOfWeek) = form.update {
        it.copy(
            days = if (day in it.days) it.days - day else it.days + day
        )
    }

    fun onEveryChange(minutes: Int) = form.update { it.copy(everyMinutes = minutes) }

    fun onContactChange(contactId: Long?) = form.update { it.copy(contactId = contactId) }

    /** The domain repeat for the form; null when the form can't make one yet (no days ticked). */
    private fun ReminderForm.toRepeat(): Repeat? = when (repeat) {
        RepeatChoice.NEVER -> Repeat.Never
        RepeatChoice.DAILY -> Repeat.Daily
        RepeatChoice.DAYS -> days.takeIf { it.isNotEmpty() }?.let(Repeat::Weekly)
        RepeatChoice.MONTHLY -> Repeat.Monthly(firstDate(this).dayOfMonth)
        RepeatChoice.EVERY -> Repeat.Every(everyMinutes)
    }

    /** The day a reminder made from this form would first fire (today for "Now"). */
    private fun firstDate(form: ReminderForm) = calculator.date(calculator.firstTrigger(form.choice.toReminderTime()))

    private fun choiceLabel(choice: WhenChoice): String = when (choice) {
        WhenChoice.Now -> "Now"
        is WhenChoice.InMinutes -> if (choice.minutes % 60 == 0) "${choice.minutes / 60} h" else "${choice.minutes} min"
        is WhenChoice.At -> formatter.clockTime(choice.hour, choice.minute)
    }

    fun onSubmit() {
        val current = form.value
        val repeat = current.toRepeat() ?: return
        val titleText = title.text.toString()
        val messageText = message.text.toString()
        viewModelScope.launch {
            val result = scheduleReminder(
                title = titleText,
                message = messageText,
                time = current.choice.toReminderTime(),
                repeat = repeat,
                contactId = current.contactId,
            )
            val message = when (result) {
                is ScheduleResult.Scheduled -> {
                    resetForm()
                    val time = formatter.format(result.reminder.triggerAtMillis)
                    "Reminder set for $time" + then(result.reminder.repeat)
                }
                is ScheduleResult.Shown -> {
                    resetForm()
                    "Notification sent" + then(result.reminder.repeat)
                }
                ScheduleResult.NotificationsBlocked -> "Notifications are turned off for My Companion"
                ScheduleResult.EmptyContent -> "Add a title or a message"
                ScheduleResult.InvalidTime -> "Pick a valid time"
            }
            eventChannel.send(RemindersEvent.Message(message))
        }
    }

    private fun resetForm() {
        title.clearText()
        message.clearText()
        form.value = ReminderForm()
    }

    /** ", then every day" / ", then every Sun, Thu"; empty for a one-off. */
    private fun then(repeat: Repeat): String =
        formatter.repeat(repeat)?.let { ", then " + it.replaceFirstChar(Char::lowercaseChar) }.orEmpty()

    fun onCancel(reminderId: Long) {
        viewModelScope.launch {
            cancelReminder(reminderId)
            eventChannel.send(RemindersEvent.Message("Reminder removed"))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val EVERY_OPTIONS = listOf(30, 60, 120, 180, 240, 360, 480, 720)
    }
}
