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
import com.akib.mycompanion.presentation.reminders.WhenChoice
import com.google.common.truth.Truth.assertThat
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
    fun `picking a clock time shows it and allows repeating`() = runTest {
        viewModel.uiState.test {
            viewModel.onChoiceChange(WhenChoice.At(8, 5))
            viewModel.onRepeatDailyChange(true)

            val state = expectMostRecentItem()
            assertThat(state.pickedTimeLabel).isEqualTo("8:05 AM")
            assertThat(state.form.canRepeat).isTrue()
            assertThat(state.form.repeatDaily).isTrue()
            assertThat(state.presets.none { it.selected }).isTrue()

            viewModel.onChoiceChange(WhenChoice.InMinutes(5))
            assertThat(expectMostRecentItem().form.repeatDaily).isFalse()
        }
    }

    @Test
    fun `repeat cannot be switched on for a relative time`() = runTest {
        viewModel.uiState.test {
            viewModel.onChoiceChange(WhenChoice.InMinutes(15))
            viewModel.onRepeatDailyChange(true)
            assertThat(expectMostRecentItem().form.repeatDaily).isFalse()
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
            assertThat(item.daily).isFalse()
        }
    }

    @Test
    fun `a daily reminder says so`() = runTest {
        viewModel.events.test {
            viewModel.onTitleChange("Medicine")
            viewModel.onChoiceChange(WhenChoice.At(8, 0))
            viewModel.onRepeatDailyChange(true)
            viewModel.onSubmit()

            assertThat(awaitItem())
                .isEqualTo(RemindersEvent.Message("Reminder set for Tomorrow, 8:00 AM, then every day"))
        }
        viewModel.uiState.test {
            assertThat(expectMostRecentItem().upcoming.single().daily).isTrue()
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
