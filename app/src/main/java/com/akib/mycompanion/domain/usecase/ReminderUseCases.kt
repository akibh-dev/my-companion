package com.akib.mycompanion.domain.usecase

import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.port.AlarmScheduler
import com.akib.mycompanion.domain.port.ReminderNotifier
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.domain.time.Clock
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import javax.inject.Inject

sealed interface ScheduleResult {
    /** Will fire at [Reminder.triggerAtMillis]. */
    data class Scheduled(val reminder: Reminder) : ScheduleResult

    /** "Now" was chosen and the notification was posted. */
    data class Shown(val reminder: Reminder) : ScheduleResult

    /** "Now" was chosen but notifications are blocked. */
    data object NotificationsBlocked : ScheduleResult

    data object EmptyContent : ScheduleResult

    data object InvalidTime : ScheduleResult
}

class ScheduleReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val calculator: ReminderTimeCalculator,
    private val fire: FireReminderUseCase,
) {
    suspend operator fun invoke(
        title: String,
        message: String,
        time: ReminderTime,
        repeat: Repeat = Repeat.Never,
        contactId: Long? = null,
    ): ScheduleResult {
        if (title.isBlank() && message.isBlank()) return ScheduleResult.EmptyContent
        if (!time.isValid() || !repeat.isValid()) return ScheduleResult.InvalidTime

        val reminder = reminders.save(
            Reminder(
                title = title.trim().ifEmpty { DEFAULT_TITLE },
                message = message.trim(),
                triggerAtMillis = calculator.firstTrigger(time, repeat),
                repeat = repeat,
                contactId = contactId,
            ),
        )

        if (time is ReminderTime.InMinutes && time.minutes == 0) {
            val shown = fire(reminder.id)
            return if (shown) ScheduleResult.Shown(reminder) else ScheduleResult.NotificationsBlocked
        }
        scheduler.schedule(reminder)
        return ScheduleResult.Scheduled(reminder)
    }

    /**
     * A one-off reminder at an exact moment, e.g. picked with date and time pickers by another feature
     * ("Remind me…" on a vault item, FR-2.16). A moment that has already passed is [ScheduleResult.InvalidTime].
     */
    suspend fun at(title: String, message: String, atMillis: Long): ScheduleResult {
        if (title.isBlank() && message.isBlank()) return ScheduleResult.EmptyContent
        if (atMillis <= calculator.inMinutes(0)) return ScheduleResult.InvalidTime

        val reminder = reminders.save(
            Reminder(
                title = title.trim().ifEmpty { DEFAULT_TITLE },
                message = message.trim(),
                triggerAtMillis = atMillis,
            ),
        )
        scheduler.schedule(reminder)
        return ScheduleResult.Scheduled(reminder)
    }

    private fun ReminderTime.isValid(): Boolean = when (this) {
        is ReminderTime.InMinutes -> minutes in 0..MAX_MINUTES
        is ReminderTime.AtTime -> hour in 0..23 && minute in 0..59
    }

    companion object {
        const val DEFAULT_TITLE = "Reminder"
        const val MAX_MINUTES = 7 * 24 * 60
    }
}

/**
 * The alarm went off: post the notification, then either queue the next time (repeating)
 * or mark it delivered (one-off). Returns whether the notification was posted.
 */
class FireReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val contacts: ContactRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: AlarmScheduler,
    private val calculator: ReminderTimeCalculator,
) {
    suspend operator fun invoke(reminderId: Long): Boolean {
        val reminder = reminders.get(reminderId) ?: return false
        val contact = reminder.contactId?.let { contacts.get(it) }
        val shown = notifier.show(reminder, contact)

        when (val nextTrigger = calculator.next(reminder.repeat, reminder.triggerAtMillis)) {
            null -> reminders.save(reminder.copy(delivered = true))
            else -> scheduler.schedule(reminders.save(reminder.copy(triggerAtMillis = nextTrigger)))
        }
        return shown
    }
}

/** "Snooze" on a notification: a one-off copy fires again in [minutes]. Repeating schedules are left untouched. */
class SnoozeReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: AlarmScheduler,
    private val calculator: ReminderTimeCalculator,
) {
    suspend operator fun invoke(reminderId: Long, minutes: Int = DEFAULT_SNOOZE_MINUTES): Reminder? {
        val original = reminders.get(reminderId) ?: return null
        notifier.cancel(original.notificationId)

        val snoozed = reminders.save(
            original.copy(
                id = 0,
                triggerAtMillis = calculator.inMinutes(minutes),
                repeat = Repeat.Never,
                delivered = false,
            ),
        )
        scheduler.schedule(snoozed)
        if (!original.repeat.isRepeating) reminders.delete(original.id)
        return snoozed
    }

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
    }
}

/** "Done" on a notification. */
class DismissReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
) {
    suspend operator fun invoke(reminderId: Long) {
        notifier.cancel(reminderId.toInt())
        val reminder = reminders.get(reminderId) ?: return
        if (reminder.delivered) reminders.delete(reminderId)
    }
}

/** User removed an upcoming reminder. */
class CancelReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
) {
    suspend operator fun invoke(reminderId: Long) {
        scheduler.cancel(reminderId)
        notifier.cancel(reminderId.toInt())
        reminders.delete(reminderId)
    }
}

/**
 * Alarms are wiped on reboot. Re-arm everything still pending, fire anything that came due
 * while the phone was off, and clean up old delivered reminders.
 */
class RestoreRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val fire: FireReminderUseCase,
    private val cleanUp: CleanUpRemindersUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        cleanUp()
        val now = clock.nowMillis()
        reminders.getPending().forEach { reminder ->
            if (reminder.triggerAtMillis > now) scheduler.schedule(reminder) else fire(reminder.id)
        }
    }
}

/**
 * Delivered one-off reminders are kept for a day, so Snooze / Done / Call on a notification still
 * left in the shade find them. After that they're deleted. Runs at boot and at app start.
 */
class CleanUpRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke() = reminders.deleteDeliveredBefore(clock.nowMillis() - KEEP_DELIVERED_MILLIS)

    companion object {
        const val KEEP_DELIVERED_MILLIS = 24 * 60 * 60 * 1000L
    }
}
