package com.akib.mycompanion

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.data.alarm.AndroidAlarmScheduler
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.MIGRATION_1_2
import com.akib.mycompanion.data.local.MIGRATION_2_3
import com.akib.mycompanion.data.local.MIGRATION_3_4
import com.akib.mycompanion.data.local.ReminderEntity
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.ReminderSound
import com.akib.mycompanion.domain.model.Repeat
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Real alarm → broadcast receiver → migrated Room → notification channel, without waiting 12 minutes. */
@RunWith(AndroidJUnit4::class)
class Plan13DeviceTest {
    @Test
    fun twelveMinuteReminderFiresAndRearmsWithItsChosenSound() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        val scheduler = AndroidAlarmScheduler(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val first = System.currentTimeMillis() + 3_000
        val fixture = Reminder(
            title = "Plan 13 fixture ${UUID.randomUUID()}",
            message = "Custom repeat test",
            triggerAtMillis = first,
            repeat = Repeat.Every(12),
            sound = ReminderSound.Silent,
        )
        val id = db.reminderDao().insert(ReminderEntity.from(fixture))
        try {
            scheduler.schedule(fixture.copy(id = id))
            val deadline = SystemClock.elapsedRealtime() + 25_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (manager.activeNotifications.any { it.id == id.toInt() } &&
                    db.reminderDao().get(id)!!.triggerAtMillis > first
                ) {
                    break
                }
                SystemClock.sleep(100)
            }
            val notification = manager.activeNotifications.single { it.id == id.toInt() }.notification
            assertThat(notification.extras.getString(Notification.EXTRA_TITLE)).isEqualTo(fixture.title)
            assertThat(manager.getNotificationChannel(notification.channelId).sound).isNull()
            val stored = db.reminderDao().get(id)!!.toDomain()
            assertThat(stored.triggerAtMillis).isEqualTo(first + 12 * 60_000)
            assertThat(stored.repeat).isEqualTo(Repeat.Every(12))
            assertThat(stored.sound).isEqualTo(ReminderSound.Silent)
            assertThat(stored.delivered).isFalse()
        } finally {
            scheduler.cancel(id)
            manager.cancel(id.toInt())
            db.reminderDao().delete(id)
            db.close()
        }
    }
}
