package com.akib.mycompanion.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock

/**
 * Schedules a notification [delayMinutes] from now. A delay of 0 posts it immediately.
 *
 * Uses an inexact-while-idle alarm: no special permission, may fire a little late in Doze.
 * Pending reminders are not restored after a reboot yet (next step: persist + BOOT_COMPLETED,
 * or move to WorkManager).
 */
object ReminderScheduler {
    fun schedule(
        context: Context,
        title: String,
        message: String,
        delayMinutes: Int,
        id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    ) {
        if (delayMinutes <= 0) {
            NotificationHelper.show(context, id, title, message)
            return
        }
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            ReminderReceiver.intent(context, id, title, message),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMinutes * 60_000L,
            pending,
        )
    }
}
