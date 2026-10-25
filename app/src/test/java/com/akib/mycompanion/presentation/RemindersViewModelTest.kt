package com.akib.mycompanion.presentation

import app.cash.turbine.test
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.domain.usecase.CancelReminderUseCase
import com.akib.mycompanion.domain.usecase.FireReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleReminderUseCase
import com.akib.mycompanion.fakes.FakeAlarmScheduler
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakeNotifier
import com.akib.mycompanion.fakes.FakeReminderRepository
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.fakes.millisAt
import com.akib.mycompanion.presentation.reminders.ReminderTimeFormatter
import com.akib.mycompanion.presentation.reminders.RemindersEvent
import com.akib.mycompanion.presentation.reminders.RemindersViewModel
import com.akib.mycompanion.presentation.reminders.RepeatChoice
import com.akib.mycompanion.presentation.reminders.WhenChoice
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.util.Locale
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
            assertThat(state.form.canSubmit).isFalse()
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
            viewModel.onTitleChange("Gym")
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
            assertThat(state.form.canSubmit).isFalse()
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
            viewModel.onTitleChange("Drink water")
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
            viewModel.onTitleChange("Stretch")
            viewModel.onChoiceChange(WhenChoice.InMinutes(15))
            viewModel.onContactChange(mum.id)
            viewModel.onSubmit()

            assertThat(awaitItem()).isEqualTo(RemindersEvent.Message("Reminder set for Today, 10:15 AM"))
        }
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.form.title).isEmpty()
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
            viewModel.onTitleChange("Medicine")
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
            viewModel.onMessageChange("Hello from the app")
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
            viewModel.onTitleChange("Hi")
            viewModel.onSubmit()
            assertThat(awaitItem())
                .isEqualTo(RemindersEvent.Message("Notifications are turned off for My Companion"))
        }
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().form.title).isEqualTo("Hi")
        }
    }

    @Test
    fun `cancelling removes it from the list`() = runTest {
        viewModel.events.test {
            viewModel.onTitleChange("Stretch")
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
}
