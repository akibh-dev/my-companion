package com.akib.mycompanion.data

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.alarm.AndroidAlarmScheduler
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.system.ReminderReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class AndroidAlarmSchedulerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val scheduler = AndroidAlarmScheduler(app)
    private val reminder = Reminder(id = 5, title = "Stretch", message = "", triggerAtMillis = 1_900_000_000_000)

    @Test
    fun `schedules an exact wake-up alarm that fires the reminder`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule(reminder)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertThat(alarm.triggerAtMs).isEqualTo(reminder.triggerAtMillis)
        assertThat(alarm.type).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_EXACT)
        assertThat(alarm.isAllowWhileIdle).isTrue()

        val intent = shadowOf(alarm.operation).savedIntent
        assertThat(intent.component!!.className).isEqualTo(ReminderReceiver::class.java.name)
        assertThat(intent.action).isEqualTo(ReminderReceiver.ACTION_FIRE)
        assertThat(intent.getLongExtra(ReminderReceiver.EXTRA_REMINDER_ID, -1)).isEqualTo(5L)
    }

    @Test
    fun `falls back to an inexact alarm when exact alarms are not allowed`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(reminder)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertThat(alarm.windowLengthMs).isEqualTo(ShadowAlarmManager.WINDOW_HEURISTIC)
        assertThat(alarm.isAllowWhileIdle).isTrue()
    }

    @Test
    fun `rescheduling the same reminder replaces its alarm`() {
        scheduler.schedule(reminder)
        scheduler.schedule(reminder.copy(triggerAtMillis = reminder.triggerAtMillis + 60_000))

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertThat(alarms).hasSize(1)
        assertThat(alarms.single().triggerAtMs).isEqualTo(reminder.triggerAtMillis + 60_000)
    }

    @Test
    fun `different reminders get separate alarms and cancel independently`() {
        scheduler.schedule(reminder)
        scheduler.schedule(reminder.copy(id = 6))

        scheduler.cancel(5)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertThat(alarms).hasSize(1)
        assertThat(shadowOf(alarms.single().operation).savedIntent.getLongExtra(ReminderReceiver.EXTRA_REMINDER_ID, -1))
            .isEqualTo(6L)
    }
}
