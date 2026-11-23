package dev.akibh.mycompanion.system

import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.domain.usecase.DismissReminderUseCase
import dev.akibh.mycompanion.domain.usecase.FireReminderUseCase
import dev.akibh.mycompanion.domain.usecase.RestoreRemindersUseCase
import dev.akibh.mycompanion.domain.usecase.SnoozeReminderUseCase
import javax.inject.Inject

/** What the system receivers can ask for. An interface so receiver tests can swap it out. */
interface ReminderEvents {
    suspend fun fire(reminderId: Long)

    suspend fun snooze(reminderId: Long)

    suspend fun done(reminderId: Long)

    suspend fun restoreAll()
}

class DefaultReminderEvents @Inject constructor(
    private val fireReminder: FireReminderUseCase,
    private val snoozeReminder: SnoozeReminderUseCase,
    private val dismissReminder: DismissReminderUseCase,
    private val restoreReminders: RestoreRemindersUseCase,
    private val log: AppLog,
) : ReminderEvents {
    override suspend fun fire(reminderId: Long) {
        fireReminder(reminderId)
    }

    override suspend fun snooze(reminderId: Long) {
        snoozeReminder(reminderId)
    }

    override suspend fun done(reminderId: Long) = dismissReminder(reminderId)

    override suspend fun restoreAll() {
        restoreReminders().forEach { failure ->
            val what = failure.reminderId?.let { "reminder $it" } ?: "clean-up"
            log.e(TAG, "couldn't restore $what", failure.error)
        }
    }

    private companion object {
        const val TAG = "Reminders"
    }
}
