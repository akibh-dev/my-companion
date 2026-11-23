package dev.akibh.mycompanion.presentation

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.domain.model.Contact
import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.domain.repository.ReminderRepository
import dev.akibh.mycompanion.domain.time.ReminderTimeCalculator
import dev.akibh.mycompanion.domain.usecase.CancelReminderUseCase
import dev.akibh.mycompanion.domain.usecase.FireReminderUseCase
import dev.akibh.mycompanion.domain.usecase.ScheduleReminderUseCase
import dev.akibh.mycompanion.domain.usecase.SetReminderPausedUseCase
import dev.akibh.mycompanion.fakes.FakeAlarmScheduler
import dev.akibh.mycompanion.fakes.FakeClock
import dev.akibh.mycompanion.fakes.FakeContactRepository
import dev.akibh.mycompanion.fakes.FakeNotifier
import dev.akibh.mycompanion.fakes.FakeReminderRepository
import dev.akibh.mycompanion.fakes.MainDispatcherRule
import dev.akibh.mycompanion.fakes.millisAt
import dev.akibh.mycompanion.presentation.reminders.ReminderTimeFormatter
import dev.akibh.mycompanion.presentation.reminders.RemindersEvent
import dev.akibh.mycompanion.presentation.reminders.RemindersViewModel
import dev.akibh.mycompanion.presentation.reminders.RepeatChoice
import dev.akibh.mycompanion.presentation.reminders.RepeatUnit
import dev.akibh.mycompanion.presentation.reminders.WhenChoice
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RemindersViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val clock = FakeClock(now = millisAt(2026, 10, 23, 10, 0))
    private val calculator = ReminderTimeCalculator(clock)
    private val mum = Contact(id = 3, name = "Mum", phone = "+8801711123456")
    private val contacts = FakeContactRepository(listOf(mum))
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeAlarmScheduler()
    private val notifier = FakeNotifier()
    private val fire = FireReminderUseCase(reminders, contacts, notifier, scheduler, calculator)

    // Lazy: viewModelScope needs the test Main dispatcher, which the rule installs after field init.
    private val viewModel by lazy {
        RemindersViewModel(
            reminders = reminders,
            contacts = contacts,
            scheduleReminder = ScheduleReminderUseCase(reminders, scheduler, calculator, fire),
            cancelReminder = CancelReminderUseCase(reminders, scheduler, notifier),
            setPaused = SetReminderPausedUseCase(reminders, scheduler, notifier, fire, clock),
            formatter = ReminderTimeFormatter(clock, Locale.US),
            calculator = calculator,
        )
    }

    @Test
    fun `starts with an empty form set to now`() = runTest {
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.presets.map { it.label })
                .containsExactly("Now", "5 min", "15 min", "30 min", "1 h")
                .inOrder()
            assertThat(state.presets.single { it.selected }.choice).isEqualTo(WhenChoice.Now)
            assertThat(state.pickedTimeLabel).isEqualTo("Pick time")
            assertThat(state.form.canSubmit(viewModel.title.text, viewModel.message.text)).isFalse()
            assertThat(state.contacts).containsExactly(mum)
        }
    }

    @Test
    fun `picking a clock time shows it`() = runTest {
        viewModel.uiState.test {
            viewModel.onChoiceChange(WhenChoice.At(8, 5))

            val state = expectMostRecentItem()
            assertThat(state.pickedTimeLabel).isEqualTo("8:05 AM")
            assertThat(state.presets.none { it.selected }).isTrue()
        }
    }

    @Test
    fun `on days starts with the day it would first fire and needs at least one day`() = runTest {
        viewModel.uiState.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Gym")
            viewModel.onChoiceChange(WhenChoice.At(8, 0)) // tomorrow, Saturday
            viewModel.onRepeatChange(RepeatChoice.DAYS)

            var state = expectMostRecentItem()
            assertThat(state.form.days).containsExactly(DayOfWeek.SATURDAY)
            assertThat(
                state.dayChips.map { it.label }
            ).containsExactly("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").inOrder()
            assertThat(state.repeatSummary).isEqualTo("Every Sat")

            viewModel.onDayToggle(DayOfWeek.SUNDAY)
            assertThat(expectMostRecentItem().repeatSummary).isEqualTo("Every Sun, Sat")

            viewModel.onDayToggle(DayOfWeek.SUNDAY)
            viewModel.onDayToggle(DayOfWeek.SATURDAY)
            state = expectMostRecentItem()
            assertThat(state.form.days).isEmpty()
            assertThat(state.form.canSubmit(viewModel.title.text, viewModel.message.text)).isFalse()
        }
    }

    @Test
    fun `every month uses the day it first fires`() = runTest {
        viewModel.uiState.test {
            viewModel.onChoiceChange(WhenChoice.At(8, 0)) // tomorrow, the 24th
            viewModel.onRepeatChange(RepeatChoice.MONTHLY)

            assertThat(expectMostRecentItem().repeatSummary).isEqualTo("Every month on the 24th")
        }
    }

    @Test
    fun `send now every two hours posts now and keeps going`() = runTest {
        viewModel.events.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Drink water")
            viewModel.onRepeatChange(RepeatChoice.EVERY)
            viewModel.onEveryChange(120)
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(RemindersEvent.Message("Notification sent, then every 2 h"))
        }
        assertThat(scheduler.scheduled.values.single()).isEqualTo(millisAt(2026, 10, 23, 12, 0))
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().upcoming.single().repeatText).isEqualTo("Every 2 h")
        }
    }

    @Test
    fun `setting a reminder confirms the time, resets the form and lists it`() = runTest {
        viewModel.events.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Stretch")
            viewModel.onChoiceChange(WhenChoice.InMinutes(15))
            viewModel.onContactChange(mum.id)
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(RemindersEvent.Message("Reminder set for Today, 10:15 AM"))
        }
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(viewModel.title.text.toString()).isEmpty()
            val item = state.upcoming.single()
            assertThat(item.title).isEqualTo("Stretch")
            assertThat(item.whenText).isEqualTo("Today, 10:15 AM")
            assertThat(item.contactName).isEqualTo("Mum")
            assertThat(item.repeatText).isNull()
        }
    }

    @Test
    fun `a daily reminder says so`() = runTest {
        viewModel.events.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Medicine")
            viewModel.onChoiceChange(WhenChoice.At(8, 0))
            viewModel.onRepeatChange(RepeatChoice.DAILY)
            viewModel.onSubmit()

            assertThat(awaitItem())
                .isEqualTo(RemindersEvent.Message("Reminder set for Tomorrow, 8:00 AM, then every day"))
        }
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().upcoming.single().repeatText).isEqualTo("Every day")
        }
    }

    @Test
    fun `send now posts the notification straight away`() = runTest {
        viewModel.events.test {
            viewModel.message.setTextAndPlaceCursorAtEnd("Hello from the app")
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(RemindersEvent.Message("Notification sent"))
        }
        assertThat(notifier.shown.single().first.message).isEqualTo("Hello from the app")
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().upcoming).isEmpty()
        }
    }

    @Test
    fun `blocked notifications are reported and the form is kept`() = runTest {
        notifier.enabled = false

        viewModel.events.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Hi")
            viewModel.onSubmit()
            assertThat(awaitItem())
                .isEqualTo(RemindersEvent.Message("Notifications are turned off for Buddy"))
        }
        assertThat(viewModel.title.text.toString()).isEqualTo("Hi")
    }

    @Test
    fun `the form can be sent once it has a title or a message`() {
        assertThat(viewModel.uiState.value.form.canSubmit(viewModel.title.text, viewModel.message.text)).isFalse()

        viewModel.message.setTextAndPlaceCursorAtEnd("Water the plants")

        assertThat(viewModel.uiState.value.form.canSubmit(viewModel.title.text, viewModel.message.text)).isTrue()
    }

    @Test
    fun `cancelling removes it from the list`() = runTest {
        viewModel.events.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Stretch")
            viewModel.onChoiceChange(WhenChoice.InMinutes(30))
            viewModel.onSubmit()
            assertThat(awaitItem()).isEqualTo(RemindersEvent.Message("Reminder set for Today, 10:30 AM"))
            val id = reminders.all.single().id

            viewModel.onCancel(id)

            assertThat(awaitItem()).isEqualTo(RemindersEvent.Message("Reminder removed"))
            assertThat(reminders.all).isEmpty()
            assertThat(scheduler.cancelled).containsExactly(id)
        }
    }

    @Test
    fun `custom twelve minute repeat previews three occurrences and schedules it`() = runTest {
        viewModel.uiState.test {
            viewModel.title.setTextAndPlaceCursorAtEnd("Stretch")
            viewModel.onChoiceChange(WhenChoice.InMinutes(12))
            viewModel.onRepeatChange(RepeatChoice.CUSTOM)
            viewModel.onCustomNumberChange("12")
            val state = expectMostRecentItem()
            assertThat(state.repeatSummary).isEqualTo("Every 12 min")
            assertThat(state.nextOccurrences).containsExactly("Today, 10:12 AM", "Today, 10:24 AM", "Today, 10:36 AM")
                .inOrder()
            assertThat(state.form.canSubmit(viewModel.title.text, viewModel.message.text)).isTrue()
        }
        viewModel.events.test {
            viewModel.onSubmit()
            assertThat(awaitItem()).isInstanceOf(RemindersEvent.Message::class.java)
        }
        assertThat(reminders.all.single().repeat).isEqualTo(dev.akibh.mycompanion.domain.model.Repeat.Every(12))
    }

    @Test
    fun `custom calendar repeat uses first date and validates invalid intervals`() = runTest {
        viewModel.uiState.test {
            viewModel.onChoiceChange(WhenChoice.OnDateTime(LocalDateTime.of(2027, 1, 31, 9, 30)))
            viewModel.onRepeatChange(RepeatChoice.CUSTOM)
            viewModel.onCustomUnitChange(RepeatUnit.MONTHS)
            viewModel.onCustomNumberChange("3")
            val state = expectMostRecentItem()
            assertThat(state.repeatSummary).isEqualTo("Every 3 months on the 31st")
            assertThat(state.nextOccurrences).hasSize(3)
            listOf("", "0", "-1", "abc", "99999999999999999999", "121").forEach {
                viewModel.onCustomNumberChange(it)
                val invalid = expectMostRecentItem()
                assertThat(invalid.form.canSubmit("Hi", "")).isFalse()
                assertThat(invalid.validationMessage).isNotNull()
                assertThat(invalid.nextOccurrences).isEmpty()
            }
        }
    }

    @Test
    fun `past chosen datetime is marked invalid`() = runTest {
        viewModel.uiState.test {
            viewModel.onChoiceChange(WhenChoice.OnDateTime(LocalDateTime.of(2026, 10, 22, 8, 0)))
            assertThat(expectMostRecentItem().validationMessage).isEqualTo("Choose a future date and time")
        }
    }

    @Test
    fun `editing a short month occurrence preserves its original monthly anchor and sound`() = runTest {
        val anchor = LocalDateTime.of(2027, 1, 31, 9, 0)
        val repeat = dev.akibh.mycompanion.domain.model.Repeat.CalendarMonths(1, anchor)
        val saved = reminders.save(
            dev.akibh.mycompanion.domain.model.Reminder(
                title = "Monthly",
                message = "Keep day 31",
                triggerAtMillis = millisAt(2027, 2, 28, 9, 0),
                repeat = repeat,
                sound = dev.akibh.mycompanion.domain.model.ReminderSound.Silent,
            )
        )
        viewModel.uiState.test {
            viewModel.onEdit(saved.id)
            val editing = expectMostRecentItem().form
            assertThat(editing.editingId).isEqualTo(saved.id)
            assertThat(editing.calendarAnchor).isEqualTo(anchor)
            assertThat(editing.sound).isEqualTo(dev.akibh.mycompanion.domain.model.ReminderSound.Silent)
        }
        viewModel.events.test {
            viewModel.onSubmit()
            awaitItem()
        }
        assertThat(reminders.all.single().id).isEqualTo(saved.id)
        assertThat(reminders.all.single().repeat).isEqualTo(repeat)
        assertThat(reminders.all.single().sound).isEqualTo(saved.sound)
    }

    @Test
    fun `changing calendar interval reanchors to the chosen first occurrence`() = runTest {
        val saved = reminders.save(
            dev.akibh.mycompanion.domain.model.Reminder(
                title = "Monthly",
                message = "",
                triggerAtMillis = millisAt(2027, 2, 28, 9, 0),
                repeat = dev.akibh.mycompanion.domain.model.Repeat.CalendarMonths(
                    1,
                    LocalDateTime.of(2027, 1, 31, 9, 0)
                ),
            )
        )
        viewModel.uiState.test {
            viewModel.onEdit(saved.id)
            viewModel.onCustomNumberChange("3")
            assertThat(expectMostRecentItem().repeatSummary).isEqualTo("Every 3 months on the 28th")
            // A text correction back to the old count retains the originally requested day 31.
            viewModel.onCustomNumberChange("")
            expectMostRecentItem()
            viewModel.onCustomNumberChange("1")
            assertThat(expectMostRecentItem().repeatSummary).isEqualTo("Every 1 month on the 31st")
            viewModel.onCustomNumberChange("3")
            expectMostRecentItem()
        }
        viewModel.events.test {
            viewModel.onSubmit()
            awaitItem()
        }
        val updated = reminders.all.single()
        assertThat(updated.repeat).isEqualTo(
            dev.akibh.mycompanion.domain.model.Repeat.CalendarMonths(
                3,
                LocalDateTime.of(2027, 2, 28, 9, 0)
            )
        )
        assertThat(calculator.next(updated.repeat, updated.triggerAtMillis)).isEqualTo(millisAt(2027, 5, 28, 9, 0))
    }

    @Test
    fun `a double tap on save makes one reminder`() = runTest {
        val saving = CompletableDeferred<Unit>()
        val slowReminders = object : ReminderRepository by reminders {
            override suspend fun save(reminder: Reminder): Reminder {
                saving.await()
                return reminders.save(reminder)
            }
        }
        val form = RemindersViewModel(
            reminders = reminders,
            contacts = contacts,
            scheduleReminder = ScheduleReminderUseCase(slowReminders, scheduler, calculator, fire),
            cancelReminder = CancelReminderUseCase(reminders, scheduler, notifier),
            setPaused = SetReminderPausedUseCase(reminders, scheduler, notifier, fire, clock),
            formatter = ReminderTimeFormatter(clock, Locale.US),
            calculator = calculator,
        )
        form.title.setTextAndPlaceCursorAtEnd("Stretch")
        form.onChoiceChange(WhenChoice.InMinutes(30))

        form.onSubmit()
        form.onSubmit()
        saving.complete(Unit)

        assertThat(reminders.all.map { it.title }).containsExactly("Stretch")
    }
}
