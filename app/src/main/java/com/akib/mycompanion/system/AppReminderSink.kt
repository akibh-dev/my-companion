package com.akib.mycompanion.system

import android.app.AlarmManager
import android.content.Context
import com.akib.mycompanion.core.sinks.ReminderSink
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import com.akib.mycompanion.domain.usecase.ScheduleReminderUseCase
import com.akib.mycompanion.domain.usecase.ScheduleResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The app's reminders, offered to the feature modules (FR-2.16: "Remind me…" on a vault item).
 * Uses the same reminders as the Reminders tab, so they show up there and survive a reboot.
 */
class AppReminderSink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleReminder: ScheduleReminderUseCase,
) : ReminderSink {

    override suspend fun remind(title: String, message: String, atMillis: Long): Boolean {
        if (!AndroidReminderNotifier.canPost(context)) return false
        // USE_EXACT_ALARM is granted at install, but the user (or a policy) can still take it away.
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        if (!alarms.canScheduleExactAlarms()) return false
        return scheduleReminder.at(title, message, atMillis) is ScheduleResult.Scheduled
    }
}
