package com.akib.mycompanion.data.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.ContextCompat
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.port.AlarmScheduler
import com.akib.mycompanion.system.ReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Exact, Doze-friendly alarms via AlarmManager.
 *
 * Exact alarms need USE_EXACT_ALARM (API 33+, granted at install) or SCHEDULE_EXACT_ALARM (31–32,
 * user-granted). If exact alarms aren't allowed we fall back to an inexact alarm, which Android may
 * delay by a few minutes.
 */
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    // canScheduleExact() + the SecurityException catch cover the exact-alarm permission.
    @SuppressLint("ScheduleExactAlarm")
    override fun schedule(reminder: Reminder) {
        val manager = alarmManager ?: return
        val pending = pendingIntent(reminder.id)
        if (canScheduleExact(manager)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
                return
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call: fall through to inexact.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
    }

    override fun cancel(reminderId: Long) {
        alarmManager?.cancel(pendingIntent(reminderId))
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean = manager.canScheduleExactAlarms()

    private fun pendingIntent(reminderId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminderId.toInt(),
        ReminderReceiver.fireIntent(context, reminderId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
