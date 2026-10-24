package com.akib.mycompanion.system

import com.akib.mycompanion.domain.usecase.DismissReminderUseCase
import com.akib.mycompanion.domain.usecase.FireReminderUseCase
import com.akib.mycompanion.domain.usecase.RestoreRemindersUseCase
import com.akib.mycompanion.domain.usecase.SnoozeReminderUseCase
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
) : ReminderEvents {
    override suspend fun fire(reminderId: Long) {
        fireReminder(reminderId)
    }

    override suspend fun snooze(reminderId: Long) {
        snoozeReminder(reminderId)
    }

    override suspend fun done(reminderId: Long) = dismissReminder(reminderId)

    override suspend fun restoreAll() = restoreReminders()
}
