package com.akib.mycompanion.presentation.reminders

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.ReminderSound
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
import java.time.LocalDateTime
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

    data class OnDateTime(val dateTime: LocalDateTime) : WhenChoice

    fun toReminderTime(): ReminderTime = when (this) {
        Now -> ReminderTime.InMinutes(0)
        is InMinutes -> ReminderTime.InMinutes(minutes)
        is At -> ReminderTime.AtTime(hour, minute)
        is OnDateTime -> ReminderTime.AtDateTime(dateTime)
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
    CUSTOM("Custom repeat"),
}

enum class RepeatUnit(val title: String, val maximum: Int) {
    MINUTES("minutes", Repeat.MAX_EVERY_MINUTES),
    HOURS("hours", Repeat.MAX_EVERY_MINUTES / 60),
    DAYS("days", Repeat.MAX_CALENDAR_DAYS),
    MONTHS("months", Repeat.MAX_CALENDAR_MONTHS),
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
    val customNumber: String = "12",
    val customUnit: RepeatUnit = RepeatUnit.MINUTES,
    val sound: ReminderSound = ReminderSound.Default,
    val editingId: Long? = null,
    val calendarAnchor: LocalDateTime? = null,
    val calendarAnchorCount: Int? = null,
    val monthlyDay: Int? = null,
) {
    /** "On days" needs at least one day ticked. */
    val customCount: Int? get() = customNumber.toIntOrNull()?.takeIf { it in 1..customUnit.maximum }
    val repeatComplete: Boolean get() = when (repeat) {
        RepeatChoice.DAYS -> days.isNotEmpty()
        RepeatChoice.CUSTOM -> customCount != null
        RepeatChoice.EVERY -> Repeat.Every(everyMinutes).isValid()
        else -> true
    }

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
    val nextOccurrences: List<String> = emptyList(),
    val validationMessage: String? = null,
)

sealed interface RemindersEvent {
    data class Message(val text: String) : RemindersEvent
}

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminders: ReminderRepository,
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
            val picked = form.choice.takeIf { it is WhenChoice.At || it is WhenChoice.OnDateTime }
            val repeat = form.toRepeat()
            val previews = preview(form, repeat)
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
                repeatSummary = repeat?.let(formatter::repeat),
                nextOccurrences = previews.map(formatter::format),
                validationMessage = when {
                    form.repeat == RepeatChoice.CUSTOM && form.customCount == null ->
                        "Enter a whole number from 1 to ${form.customUnit.maximum}"
                    previews.isEmpty() && form.choice is WhenChoice.OnDateTime -> "Choose a future date and time"
                    else -> null
                },
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

    fun onChoiceChange(choice: WhenChoice) = form.update {
        it.copy(choice = choice, calendarAnchor = null, monthlyDay = null)
    }

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

    fun onCustomNumberChange(number: String) = form.update { it.copy(customNumber = number) }

    fun onCustomUnitChange(unit: RepeatUnit) = form.update {
        if (it.customUnit == unit) it else it.copy(customUnit = unit, calendarAnchor = null, calendarAnchorCount = null)
    }

    fun onSoundChange(sound: ReminderSound) = form.update { it.copy(sound = sound) }

    fun onContactChange(contactId: Long?) = form.update { it.copy(contactId = contactId) }

    /** The domain repeat for the form; null when the form can't make one yet (no days ticked). */
    private fun ReminderForm.toRepeat(): Repeat? = when (repeat) {
        RepeatChoice.NEVER -> Repeat.Never
        RepeatChoice.DAILY -> Repeat.Daily
        RepeatChoice.DAYS -> days.takeIf { it.isNotEmpty() }?.let(Repeat::Weekly)
        RepeatChoice.MONTHLY -> Repeat.Monthly(monthlyDay ?: firstDate(this).dayOfMonth)
        RepeatChoice.EVERY -> Repeat.Every(everyMinutes).takeIf(Repeat::isValid)
        RepeatChoice.CUSTOM -> customCount?.let { count ->
            val unchangedAnchor = calendarAnchor?.takeIf { calendarAnchorCount == null || calendarAnchorCount == count }
            val anchor = unchangedAnchor ?: calculator.localDateTime(calculator.firstTrigger(choice.toReminderTime()))
            when (customUnit) {
                RepeatUnit.MINUTES -> Repeat.Every(count)
                RepeatUnit.HOURS -> Repeat.Every(Math.multiplyExact(count, 60))
                RepeatUnit.DAYS -> Repeat.CalendarDays(count, anchor)
                RepeatUnit.MONTHS -> Repeat.CalendarMonths(count, anchor)
            }
        }
    }

    private fun preview(form: ReminderForm, repeat: Repeat?): List<Long> = runCatching {
        if (repeat == null) return@runCatching emptyList()
        val first = calculator.firstTrigger(form.choice.toReminderTime(), repeat)
        if (form.choice is WhenChoice.OnDateTime && first <= calculator.inMinutes(0)) return@runCatching emptyList()
        buildList {
            add(first)
            if (repeat.isRepeating) kotlin.repeat(2) { calculator.next(repeat, last())?.let(::add) }
        }
    }.getOrDefault(emptyList())

    /** The day a reminder made from this form would first fire (today for "Now"). */
    private fun firstDate(form: ReminderForm) = calculator.date(calculator.firstTrigger(form.choice.toReminderTime()))

    private fun choiceLabel(choice: WhenChoice): String = when (choice) {
        WhenChoice.Now -> "Now"
        is WhenChoice.InMinutes -> if (choice.minutes % 60 == 0) "${choice.minutes / 60} h" else "${choice.minutes} min"
        is WhenChoice.At -> formatter.clockTime(choice.hour, choice.minute)
        is WhenChoice.OnDateTime -> formatter.format(calculator.firstTrigger(choice.toReminderTime()))
    }

    private val mutableSavedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = mutableSavedCount

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
                sound = current.sound,
                editingId = current.editingId,
            )
            val message = when (result) {
                is ScheduleResult.Scheduled -> {
                    resetForm()
                    mutableSavedCount.value++
                    val time = formatter.format(result.reminder.triggerAtMillis)
                    "Reminder set for $time" + then(result.reminder.repeat)
                }
                is ScheduleResult.Shown -> {
                    resetForm()
                    mutableSavedCount.value++
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

    fun onEdit(reminderId: Long) {
        viewModelScope.launch {
            val reminder = reminders.get(reminderId) ?: return@launch
            title.setTextAndPlaceCursorAtEnd(reminder.title)
            message.setTextAndPlaceCursorAtEnd(reminder.message)
            form.value = reminder.toForm()
        }
    }

    fun onCancelEdit() = resetForm()

    private fun Reminder.toForm(): ReminderForm {
        val kind = when (repeat) {
            Repeat.Never -> RepeatChoice.NEVER
            Repeat.Daily -> RepeatChoice.DAILY
            is Repeat.Weekly -> RepeatChoice.DAYS
            is Repeat.Monthly -> RepeatChoice.MONTHLY
            is Repeat.Every, is Repeat.CalendarDays, is Repeat.CalendarMonths -> RepeatChoice.CUSTOM
        }
        val unit = when (repeat) {
            is Repeat.CalendarDays -> RepeatUnit.DAYS
            is Repeat.CalendarMonths -> RepeatUnit.MONTHS
            else -> RepeatUnit.MINUTES
        }
        val count = when (val value = repeat) {
            is Repeat.Every -> value.minutes
            is Repeat.CalendarDays -> value.count
            is Repeat.CalendarMonths -> value.count
            else -> 12
        }
        return ReminderForm(
            choice = WhenChoice.OnDateTime(calculator.localDateTime(triggerAtMillis)),
            repeat = kind,
            days = (repeat as? Repeat.Weekly)?.days.orEmpty(),
            customNumber = count.toString(), customUnit = unit,
            contactId = contactId, sound = sound, editingId = id,
            calendarAnchor = when (val value = repeat) {
                is Repeat.CalendarDays -> value.anchor
                is Repeat.CalendarMonths -> value.anchor
                else -> null
            },
            calendarAnchorCount = when (val value = repeat) {
                is Repeat.CalendarDays -> value.count
                is Repeat.CalendarMonths -> value.count
                else -> null
            },
            monthlyDay = (repeat as? Repeat.Monthly)?.dayOfMonth,
        )
    }

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
