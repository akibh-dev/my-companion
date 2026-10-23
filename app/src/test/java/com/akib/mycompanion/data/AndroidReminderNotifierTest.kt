package com.akib.mycompanion.data

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.presentation.call.CallActivity
import com.akib.mycompanion.system.ReminderReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AndroidReminderNotifierTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidReminderNotifier(app)
    private val reminder = Reminder(id = 12, title = "Call Mum", message = "It's Sunday", triggerAtMillis = 0)
    private val mum = Contact(id = 3, name = "Mum", phone = "+8801711123456")

    @Before
    fun grantNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `posts a high-priority reminder with snooze and done`() {
        assertThat(notifier.show(reminder, contact = null)).isTrue()

        val notification = shadowOf(manager).getNotification(12)!!
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Call Mum")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("It's Sunday")
        assertThat(notification.channelId).isEqualTo(AndroidReminderNotifier.CHANNEL_ID)
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Snooze 10 min", "Done").inOrder()
        assertThat(manager.getNotificationChannel(AndroidReminderNotifier.CHANNEL_ID).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun `snooze and done talk to the reminder receiver`() {
        notifier.show(reminder, contact = null)
        val (snooze, done) = shadowOf(manager).getNotification(12)!!.actions

        val snoozeIntent = shadowOf(snooze.actionIntent).savedIntent
        assertThat(snoozeIntent.action).isEqualTo(ReminderReceiver.ACTION_SNOOZE)
        assertThat(snoozeIntent.getLongExtra(ReminderReceiver.EXTRA_REMINDER_ID, -1)).isEqualTo(12L)
        assertThat(shadowOf(done.actionIntent).savedIntent.action).isEqualTo(ReminderReceiver.ACTION_DONE)
    }

    @Test
    fun `a linked contact adds a call button`() {
        notifier.show(reminder, mum)

        val call = shadowOf(manager).getNotification(12)!!.actions.last()
        assertThat(call.title.toString()).isEqualTo("Call Mum")
        val pending = shadowOf(call.actionIntent)
        assertThat(pending.isActivityIntent).isTrue()
        assertThat(pending.savedIntent.component!!.className).isEqualTo(CallActivity::class.java.name)
        assertThat(pending.savedIntent.getLongExtra(CallActivity.EXTRA_CONTACT_ID, -1)).isEqualTo(3L)
        assertThat(pending.savedIntent.getIntExtra(CallActivity.EXTRA_NOTIFICATION_ID, -1)).isEqualTo(12)
    }

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(notifier.show(reminder, null)).isFalse()
        assertThat(shadowOf(manager).size()).isEqualTo(0)
    }

    @Test
    fun `nothing is posted when notifications are switched off`() {
        shadowOf(manager).setNotificationsEnabled(false)

        assertThat(notifier.show(reminder, null)).isFalse()
        assertThat(shadowOf(manager).size()).isEqualTo(0)
    }

    @Test
    fun `cancel removes the notification`() {
        notifier.show(reminder, null)

        notifier.cancel(12)

        assertThat(shadowOf(manager).size()).isEqualTo(0)
    }

    @Test
    fun `pending intent request codes never collide between reminders`() {
        val codes = (1..50).flatMap { id -> (0..3).map { action -> AndroidReminderNotifier.requestCode(id, action) } }
        assertThat(codes).containsNoDuplicates()
    }
}
