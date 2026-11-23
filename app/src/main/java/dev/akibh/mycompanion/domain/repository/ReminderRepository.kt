package dev.akibh.mycompanion.domain.repository

import dev.akibh.mycompanion.domain.model.Reminder
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

    /** How many reminders came due while their notification couldn't be shown. */
    fun observeMissedCount(): Flow<Int>
}
