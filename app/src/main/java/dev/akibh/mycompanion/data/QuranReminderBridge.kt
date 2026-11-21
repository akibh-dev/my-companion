package dev.akibh.mycompanion.data

import dev.akibh.mycompanion.domain.model.ReminderTime
import dev.akibh.mycompanion.domain.model.Repeat
import dev.akibh.mycompanion.domain.repository.ReminderRepository
import dev.akibh.mycompanion.domain.time.Clock
import dev.akibh.mycompanion.domain.usecase.CancelReminderUseCase
import dev.akibh.mycompanion.domain.usecase.ScheduleReminderUseCase
import dev.akibh.mycompanion.quran.domain.QuranReminder
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The Quran's daily reminder, kept as a daily reminder that opens the reader. */
class QuranReminderBridge @Inject constructor(
    private val reminders: ReminderRepository,
    private val schedule: ScheduleReminderUseCase,
    private val cancel: CancelReminderUseCase,
    private val clock: Clock,
) : QuranReminder {
    override val time: Flow<LocalTime?> = reminders.observeUpcoming().map { list ->
        list.firstOrNull { it.link == QuranReminder.ROUTE }?.let {
            LocalTime.ofInstant(Instant.ofEpochMilli(it.triggerAtMillis), clock.zone).withSecond(0).withNano(0)
        }
    }

    override suspend fun set(time: LocalTime?) {
        val existing = reminders.observeUpcoming().first().firstOrNull { it.link == QuranReminder.ROUTE }
        if (time == null) {
            existing?.let { cancel(it.id) }
            return
        }
        schedule(
            title = TITLE,
            message = MESSAGE,
            time = ReminderTime.AtTime(time.hour, time.minute),
            repeat = Repeat.Daily,
            editingId = existing?.id,
            link = QuranReminder.ROUTE,
        )
    }

    private companion object {
        const val TITLE = "Read Quran"
        const val MESSAGE = "Tap to continue where you stopped."
    }
}
