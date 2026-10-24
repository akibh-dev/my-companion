package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.domain.usecase.CancelReminderUseCase
import com.akib.mycompanion.domain.usecase.DismissReminderUseCase
import com.akib.mycompanion.domain.usecase.FireReminderUseCase
import com.akib.mycompanion.domain.usecase.RestoreRemindersUseCase
import com.akib.mycompanion.domain.usecase.ScheduleReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleResult
import com.akib.mycompanion.domain.usecase.SnoozeReminderUseCase
import com.akib.mycompanion.fakes.FakeAlarmScheduler
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakeNotifier
import com.akib.mycompanion.fakes.FakeReminderRepository
import com.akib.mycompanion.fakes.millisAt
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReminderUseCasesTest {

    private val clock = FakeClock(now = millisAt(2026, 10, 23, 10, 0))
    private val calculator = ReminderTimeCalculator(clock)
    private val mum = Contact(id = 7, name = "Mum", phone = "+8801711123456")
    private val contacts = FakeContactRepository(listOf(mum))
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeAlarmScheduler()
    private val notifier = FakeNotifier()

    private val fire = FireReminderUseCase(reminders, contacts, notifier, scheduler, calculator)
    private val schedule = ScheduleReminderUseCase(reminders, scheduler, calculator, fire)
    private val snooze = SnoozeReminderUseCase(reminders, notifier, scheduler, calculator)
    private val dismiss = DismissReminderUseCase(reminders, notifier)
    private val cancel = CancelReminderUseCase(reminders, scheduler, notifier)
    private val restore = RestoreRemindersUseCase(reminders, scheduler, fire, clock)

    // ---- Scheduling ----

    @Test
    fun `scheduling in 15 minutes saves the reminder and arms an alarm`() = runTest {
        val result = schedule("  Drink water ", "Stay hydrated", ReminderTime.InMinutes(15))

        val reminder = (result as ScheduleResult.Scheduled).reminder
        assertThat(reminder.title).isEqualTo("Drink water")
        assertThat(reminder.triggerAtMillis).isEqualTo(millisAt(2026, 10, 23, 10, 15))
        assertThat(reminders.all).containsExactly(reminder)
        assertThat(scheduler.scheduled).containsExactly(reminder.id, reminder.triggerAtMillis)
        assertThat(notifier.shown).isEmpty()
    }

    @Test
    fun `a blank title falls back to the default when there is a message`() = runTest {
        val result = schedule("", "Call the bank", ReminderTime.AtTime(17, 0))

        assertThat((result as ScheduleResult.Scheduled).reminder.title)
            .isEqualTo(ScheduleReminderUseCase.DEFAULT_TITLE)
    }

    @Test
    fun `empty title and message are rejected`() = runTest {
        assertThat(schedule(" ", " ", ReminderTime.InMinutes(5))).isEqualTo(ScheduleResult.EmptyContent)
        assertThat(reminders.all).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `invalid times are rejected`() = runTest {
        assertThat(schedule("x", "", ReminderTime.AtTime(25, 0))).isEqualTo(ScheduleResult.InvalidTime)
        assertThat(schedule("x", "", ReminderTime.AtTime(8, 60))).isEqualTo(ScheduleResult.InvalidTime)
        assertThat(schedule("x", "", ReminderTime.InMinutes(-1))).isEqualTo(ScheduleResult.InvalidTime)
        assertThat(reminders.all).isEmpty()
    }

    @Test
    fun `now shows the notification immediately without an alarm`() = runTest {
        val result = schedule("Hello", "", ReminderTime.InMinutes(0))

        assertThat(result).isInstanceOf(ScheduleResult.Shown::class.java)
        assertThat(notifier.shown.map { it.first.title }).containsExactly("Hello")
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(reminders.all.single().delivered).isTrue()
    }

    @Test
    fun `now with notifications blocked is reported`() = runTest {
        notifier.enabled = false

        assertThat(schedule("Hello", "", ReminderTime.InMinutes(0))).isEqualTo(ScheduleResult.NotificationsBlocked)
    }

    // ---- Firing ----

    @Test
    fun `a one-off reminder is shown with its contact and marked delivered`() = runTest {
        val saved = reminders.save(reminder(contactId = mum.id))

        assertThat(fire(saved.id)).isTrue()

        assertThat(notifier.shown).containsExactly(saved to mum)
        assertThat(reminders.get(saved.id)!!.delivered).isTrue()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `a daily reminder is shown and re-armed for tomorrow`() = runTest {
        val eightToday = millisAt(2026, 10, 23, 8, 0)
        val saved = reminders.save(reminder(trigger = eightToday, repeat = Repeat.DAILY))

        fire(saved.id)

        val tomorrow = millisAt(2026, 10, 24, 8, 0)
        assertThat(notifier.shown).hasSize(1)
        assertThat(reminders.get(saved.id)).isEqualTo(saved.copy(triggerAtMillis = tomorrow))
        assertThat(scheduler.scheduled).containsExactly(saved.id, tomorrow)
    }

    @Test
    fun `firing an unknown reminder does nothing`() = runTest {
        assertThat(fire(404)).isFalse()
        assertThat(notifier.shown).isEmpty()
    }

    @Test
    fun `a removed contact just means no call button`() = runTest {
        val saved = reminders.save(reminder(contactId = 999))

        fire(saved.id)

        assertThat(notifier.shown.single().second).isNull()
    }

    // ---- Notification actions ----

    @Test
    fun `snoozing a one-off reminder replaces it with one in 10 minutes`() = runTest {
        val saved = reminders.save(reminder(delivered = true))

        val snoozed = snooze(saved.id)!!

        assertThat(snoozed.triggerAtMillis).isEqualTo(millisAt(2026, 10, 23, 10, 10))
        assertThat(snoozed.delivered).isFalse()
        assertThat(reminders.all).containsExactly(snoozed)
        assertThat(scheduler.scheduled).containsExactly(snoozed.id, snoozed.triggerAtMillis)
        assertThat(notifier.cancelled).containsExactly(saved.notificationId)
    }

    @Test
    fun `snoozing a daily reminder leaves the daily schedule alone`() = runTest {
        val daily = reminders.save(reminder(trigger = millisAt(2026, 10, 24, 8, 0), repeat = Repeat.DAILY))

        val snoozed = snooze(daily.id)!!

        assertThat(snoozed.repeat).isEqualTo(Repeat.NONE)
        assertThat(reminders.all).containsExactly(daily, snoozed)
    }

    @Test
    fun `done removes a delivered reminder and its notification`() = runTest {
        val saved = reminders.save(reminder(delivered = true))

        dismiss(saved.id)

        assertThat(reminders.all).isEmpty()
        assertThat(notifier.cancelled).containsExactly(saved.notificationId)
    }

    @Test
    fun `done keeps a daily reminder for tomorrow`() = runTest {
        val daily = reminders.save(reminder(repeat = Repeat.DAILY))

        dismiss(daily.id)

        assertThat(reminders.all).containsExactly(daily)
    }

    @Test
    fun `cancelling removes the alarm, notification and reminder`() = runTest {
        val saved = reminders.save(reminder())
        scheduler.schedule(saved)

        cancel(saved.id)

        assertThat(reminders.all).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
        assertThat(scheduler.cancelled).containsExactly(saved.id)
        assertThat(notifier.cancelled).containsExactly(saved.notificationId)
    }

    // ---- After reboot ----

    @Test
    fun `restore re-arms future reminders, fires missed ones and cleans up old ones`() = runTest {
        val future = reminders.save(reminder(title = "future", trigger = millisAt(2026, 10, 23, 18, 0)))
        val missed = reminders.save(reminder(title = "missed", trigger = millisAt(2026, 10, 23, 9, 0)))
        val oldDone = reminders.save(reminder(title = "old", trigger = millisAt(2026, 10, 20, 9, 0), delivered = true))
        val recentDone = reminders.save(
            reminder(title = "recent", trigger = millisAt(2026, 10, 23, 9, 30), delivered = true),
        )

        restore()

        assertThat(scheduler.scheduled).containsExactly(future.id, future.triggerAtMillis)
        assertThat(notifier.shown.map { it.first.id }).containsExactly(missed.id)
        assertThat(reminders.get(missed.id)!!.delivered).isTrue()
        assertThat(reminders.get(oldDone.id)).isNull()
        assertThat(reminders.get(recentDone.id)).isNotNull()
    }

    private fun reminder(
        title: String = "Call Mum",
        trigger: Long = millisAt(2026, 10, 23, 12, 0),
        repeat: Repeat = Repeat.NONE,
        contactId: Long? = null,
        delivered: Boolean = false,
    ) = Reminder(
        title = title,
        message = "",
        triggerAtMillis = trigger,
        repeat = repeat,
        contactId = contactId,
        delivered = delivered,
    )
}
