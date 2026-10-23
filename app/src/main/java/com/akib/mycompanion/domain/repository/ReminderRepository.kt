package com.akib.mycompanion.domain.repository

import com.akib.mycompanion.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    /** Reminders that have not fired yet, soonest first. */
    fun observeUpcoming(): Flow<List<Reminder>>

    suspend fun get(id: Long): Reminder?

    suspend fun getPending(): List<Reminder>

    /** Inserts when [Reminder.id] is 0, otherwise updates. Returns the saved reminder with its id. */
    suspend fun save(reminder: Reminder): Reminder

    suspend fun delete(id: Long)

    /** Removes delivered one-off reminders that fired before [beforeMillis]. */
    suspend fun deleteDeliveredBefore(beforeMillis: Long)
}
