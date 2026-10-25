package com.akib.mycompanion

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.data.alarm.AndroidAlarmScheduler
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.MIGRATION_1_2
import com.akib.mycompanion.data.local.ReminderEntity
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.messaging.ChatAdapter
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.NotificationExtractor
import com.akib.mycompanion.messaging.ReplySender
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceComponentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun systemAlarmDeliversReminderThroughReceiverAndNotificationManager() = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2).build()
        val scheduler = AndroidAlarmScheduler(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = "Device alarm ${UUID.randomUUID()}"
        val fixture = Reminder(title = title, message = "Alarm delivery fixture", triggerAtMillis = 0)
        val id = db.reminderDao().insert(ReminderEntity.from(fixture))
        try {
            scheduler.schedule(fixture.copy(id = id, triggerAtMillis = System.currentTimeMillis() + 3_000))
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (
                manager.activeNotifications.none { it.id == id.toInt() } &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(100)
            }
            val posted = manager.activeNotifications.single { it.id == id.toInt() }.notification
            assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(title)
            assertThat(posted.actions.map { it.title.toString() }).containsExactly("Snooze 10 min", "Done").inOrder()
            assertThat(db.reminderDao().get(id)?.delivered).isTrue()
        } finally {
            scheduler.cancel(id)
            manager.cancel(id.toInt())
            db.reminderDao().delete(id)
            db.close()
        }
    }

    @Test
    fun allChatAdaptersReadRealMessagingStyleAndRemoteInputReachesReceiver() {
        val actionName = "${context.packageName}.DEVICE_REPLY_${UUID.randomUUID()}"
        val received = CountDownLatch(1)
        var reply: String? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply")?.toString()
                received.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(actionName), ContextCompat.RECEIVER_NOT_EXPORTED)
        val pending = PendingIntent.getBroadcast(
            context,
            1,
            Intent(actionName).setPackage(context.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dir = File(context.cacheDir, "reply-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input = RemoteInput.Builder("reply").setLabel("Reply").build()
            val action = Notification.Action.Builder(null, "Reply", pending)
                .addRemoteInput(input).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).build()
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me fixture").build())
                .addMessage("Synthetic message", 1, Person.Builder().setName("Sender fixture").build())
            val notification = NotificationCompat.Builder(context, "device-fixture")
                .setSmallIcon(R.drawable.ic_notification).setStyle(style).build()
            notification.actions = arrayOf(action)
            for (app in ChatApp.entries) {
                val sbn = StatusBarNotification(
                    app.packageName, app.packageName, 1, null, 10000, 0, 0, notification,
                    Process.myUserHandle(), 1,
                )
                val message = requireNotNull(ChatAdapter.ALL.getValue(app).parse(NotificationExtractor.parts(sbn)))
                assertThat(message.sender).isEqualTo("Sender fixture")
                assertThat(message.text).isEqualTo("Synthetic message")
                assertThat(message.canReply).isTrue()
            }
            val sender = ReplySender(context, AppLog(dir, mirrorToLogcat = false))
            sender.remember("fixture-key", requireNotNull(NotificationExtractor.replyAction(notification)))
            assertThat(sender.reply("fixture-key", "Synthetic reply")).isTrue()
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(reply).isEqualTo("Synthetic reply")
            sender.forget("fixture-key")
            assertThat(sender.reply("fixture-key", "Must not send")).isFalse()
        } finally {
            context.unregisterReceiver(receiver)
            pending.cancel()
            dir.deleteRecursively()
        }
    }
}
