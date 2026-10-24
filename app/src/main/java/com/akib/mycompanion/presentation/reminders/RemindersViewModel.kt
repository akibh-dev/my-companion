package com.akib.mycompanion.presentation.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.domain.usecase.CancelReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleResult
import dagger.hilt.android.lifecycle.HiltViewModel
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
        val presets: List<WhenChoice> = listOf(Now, InMinutes(5), InMinutes(15), InMinutes(30), InMinutes(60))
    }
}

data class ReminderForm(
    val title: String = "",
    val message: String = "",
    val choice: WhenChoice = WhenChoice.Now,
    val repeatDaily: Boolean = false,
    val contactId: Long? = null,
) {
    val canSubmit: Boolean get() = title.isNotBlank() || message.isNotBlank()

    /** Repeating only makes sense for a clock time. */
    val canRepeat: Boolean get() = choice is WhenChoice.At
}

data class UpcomingReminder(
    val id: Long,
    val title: String,
    val message: String,
    val whenText: String,
    val daily: Boolean,
    val contactName: String?,
)

/** One "When" chip, ready to draw. */
data class ChoiceChip(val choice: WhenChoice, val label: String, val selected: Boolean)

data class RemindersUiState(
    val form: ReminderForm = ReminderForm(),
    val presets: List<ChoiceChip> = emptyList(),
    /** Label of the "Pick time" chip: the picked time once there is one. */
    val pickedTimeLabel: String = "Pick time",
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
) : ViewModel() {

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
                contacts = people,
                upcoming = upcoming.map { reminder ->
                    UpcomingReminder(
                        id = reminder.id,
                        title = reminder.title,
                        message = reminder.message,
                        whenText = formatter.format(reminder.triggerAtMillis),
                        daily = reminder.repeat == Repeat.DAILY,
                        contactName = reminder.contactId?.let(names::get),
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RemindersUiState())

    fun onTitleChange(title: String) = form.update { it.copy(title = title) }

    fun onMessageChange(message: String) = form.update { it.copy(message = message) }

    fun onChoiceChange(choice: WhenChoice) = form.update {
        it.copy(choice = choice, repeatDaily = it.repeatDaily && choice is WhenChoice.At)
    }

    fun onRepeatDailyChange(repeat: Boolean) = form.update { it.copy(repeatDaily = repeat && it.canRepeat) }

    fun onContactChange(contactId: Long?) = form.update { it.copy(contactId = contactId) }

    private fun choiceLabel(choice: WhenChoice): String = when (choice) {
        WhenChoice.Now -> "Now"
        is WhenChoice.InMinutes -> if (choice.minutes % 60 == 0) "${choice.minutes / 60} h" else "${choice.minutes} min"
        is WhenChoice.At -> formatter.clockTime(choice.hour, choice.minute)
    }

    fun onSubmit() {
        val current = form.value
        viewModelScope.launch {
            val result = scheduleReminder(
                title = current.title,
                message = current.message,
                time = current.choice.toReminderTime(),
                repeat = if (current.repeatDaily) Repeat.DAILY else Repeat.NONE,
                contactId = current.contactId,
            )
            val message = when (result) {
                is ScheduleResult.Scheduled -> {
                    form.value = ReminderForm()
                    val daily = if (result.reminder.repeat == Repeat.DAILY) ", then every day" else ""
                    "Reminder set for ${formatter.format(result.reminder.triggerAtMillis)}$daily"
                }
                is ScheduleResult.Shown -> {
                    form.value = ReminderForm()
                    "Notification sent"
                }
                ScheduleResult.NotificationsBlocked -> "Notifications are turned off for My Companion"
                ScheduleResult.EmptyContent -> "Add a title or a message"
                ScheduleResult.InvalidTime -> "Pick a valid time"
            }
            eventChannel.send(RemindersEvent.Message(message))
        }
    }

    fun onCancel(reminderId: Long) {
        viewModelScope.launch {
            cancelReminder(reminderId)
            eventChannel.send(RemindersEvent.Message("Reminder removed"))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
