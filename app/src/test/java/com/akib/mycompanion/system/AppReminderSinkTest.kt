package com.akib.mycompanion.system

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.domain.usecase.FireReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleReminderUseCase
import com.akib.mycompanion.fakes.FakeAlarmScheduler
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakeNotifier
import com.akib.mycompanion.fakes.FakeReminderRepository
import com.akib.mycompanion.fakes.millisAt
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class AppReminderSinkTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock(now = millisAt(2026, 10, 23, 10, 0))
    private val calculator = ReminderTimeCalculator(clock)
    private val reminders = FakeReminderRepository()
    private val scheduler = FakeAlarmScheduler()
    private val fire = FireReminderUseCase(reminders, FakeContactRepository(), FakeNotifier(), scheduler, calculator)
    private val sink = AppReminderSink(app, ScheduleReminderUseCase(reminders, scheduler, calculator, fire))
    private val at = millisAt(2026, 11, 1, 9, 0)

    @After
    fun resetAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `sets a one-off reminder with only the title and a fixed message`() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        assertThat(sink.remind("Passport", "From your vault", at)).isTrue()

        val reminder = reminders.all.single()
        assertThat(reminder.title).isEqualTo("Passport")
        assertThat(reminder.message).isEqualTo("From your vault")
        assertThat(scheduler.scheduled).containsExactly(reminder.id, at)
    }

    @Test
    fun `refuses when notifications are not allowed`() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        assertThat(sink.remind("Passport", "From your vault", at)).isFalse()
        assertThat(reminders.all).isEmpty()
    }

    @Test
    fun `refuses when exact alarms are not allowed`() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertThat(sink.remind("Passport", "From your vault", at)).isFalse()
        assertThat(reminders.all).isEmpty()
    }
}
