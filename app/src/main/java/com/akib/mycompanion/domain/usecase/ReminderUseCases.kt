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
        repeat: Repeat = Repeat.NONE,
        contactId: Long? = null,
    ): ScheduleResult {
        if (title.isBlank() && message.isBlank()) return ScheduleResult.EmptyContent
        if (!time.isValid()) return ScheduleResult.InvalidTime

        val reminder = reminders.save(
            Reminder(
                title = title.trim().ifEmpty { DEFAULT_TITLE },
                message = message.trim(),
                triggerAtMillis = calculator.firstTrigger(time),
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
 * The alarm went off: post the notification, then either queue tomorrow's (daily)
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

        when (reminder.repeat) {
            Repeat.DAILY -> {
                val nextTrigger = calculator.nextDaily(reminder.triggerAtMillis)
                scheduler.schedule(reminders.save(reminder.copy(triggerAtMillis = nextTrigger)))
            }
            Repeat.NONE -> reminders.save(reminder.copy(delivered = true))
        }
        return shown
    }
}

/** "Snooze" on a notification: a one-off copy fires again in [minutes]. Daily schedules are left untouched. */
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
                repeat = Repeat.NONE,
                delivered = false,
            ),
        )
        scheduler.schedule(snoozed)
        if (original.repeat == Repeat.NONE) reminders.delete(original.id)
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
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val now = clock.nowMillis()
        reminders.deleteDeliveredBefore(now - CLEANUP_AFTER_MILLIS)
        reminders.getPending().forEach { reminder ->
            if (reminder.triggerAtMillis > now) scheduler.schedule(reminder) else fire(reminder.id)
        }
    }

    companion object {
        const val CLEANUP_AFTER_MILLIS = 24 * 60 * 60 * 1000L
    }
}
