package dev.akibh.mycompanion.domain.usecase

import dev.akibh.mycompanion.domain.model.Reminder
import dev.akibh.mycompanion.domain.model.ReminderSound
import dev.akibh.mycompanion.domain.model.ReminderTime
import dev.akibh.mycompanion.domain.model.Repeat
import dev.akibh.mycompanion.domain.port.AlarmScheduler
import dev.akibh.mycompanion.domain.port.ReminderNotifier
import dev.akibh.mycompanion.domain.repository.ContactRepository
import dev.akibh.mycompanion.domain.repository.ReminderRepository
import dev.akibh.mycompanion.domain.time.Clock
import dev.akibh.mycompanion.domain.time.ReminderTimeCalculator
import java.time.LocalTime
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

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
        sound: ReminderSound = ReminderSound.Default,
        editingId: Long? = null,
        link: String? = null,
    ): ScheduleResult {
        if (title.isBlank() && message.isBlank()) return ScheduleResult.EmptyContent
        if (!time.isValid() || !repeat.isValid()) return ScheduleResult.InvalidTime

        val existing = editingId?.let { reminders.get(it) ?: return ScheduleResult.InvalidTime }
        val trigger = runCatching {
            calculator.firstTrigger(
                time,
                repeat
            )
        }.getOrElse { return ScheduleResult.InvalidTime }
        val reminder = reminders.save(
            Reminder(
                id = existing?.id ?: 0,
                title = title.trim().ifEmpty { DEFAULT_TITLE },
                message = message.trim(),
                triggerAtMillis = trigger,
                repeat = repeat,
                contactId = contactId,
                sound = sound,
                paused = existing?.paused == true,
                link = link ?: existing?.link,
                localTime = if (repeat.keepsClockTime) wantedClockTime(time, trigger) else null,
            ),
        )

        if (existing != null) {
            scheduler.cancel(existing.id)
            fire.cancelNotification(existing.id)
            // Editing a paused reminder saves the changes but keeps it paused.
            if (existing.paused) return ScheduleResult.Scheduled(reminder)
        }
        if (time is ReminderTime.InMinutes && time.minutes == 0) {
            val shown = fire(reminder.id)
            if (shown) return ScheduleResult.Shown(reminder)
            // The screen says notifications are off and keeps the form; a new one-off isn't kept as missed.
            if (existing == null && !repeat.isRepeating) reminders.delete(reminder.id)
            return ScheduleResult.NotificationsBlocked
        }
        scheduler.schedule(reminder)
        return ScheduleResult.Scheduled(reminder)
    }

    /**
     * A one-off reminder at an exact moment.
     * A moment that has already passed is [ScheduleResult.InvalidTime].
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

    /** The clock time the user asked for, even when a daylight-saving gap moved the first trigger. */
    private fun wantedClockTime(time: ReminderTime, trigger: Long): LocalTime = when (time) {
        is ReminderTime.AtTime -> LocalTime.of(time.hour, time.minute)
        is ReminderTime.AtDateTime -> time.dateTime.toLocalTime()
        is ReminderTime.InMinutes -> calculator.clockTime(trigger)
    }

    private fun ReminderTime.isValid(): Boolean = when (this) {
        is ReminderTime.InMinutes -> minutes in 0..MAX_MINUTES
        is ReminderTime.AtTime -> hour in 0..23 && minute in 0..59
        is ReminderTime.AtDateTime -> runCatching {
            calculator.firstTrigger(this) > calculator.inMinutes(0)
        }.getOrDefault(false)
    }

    companion object {
        const val DEFAULT_TITLE = "Reminder"
        const val MAX_MINUTES = 7 * 24 * 60
    }
}

/**
 * The alarm went off: post the notification, then either queue the next time (repeating)
 * or mark it delivered (one-off). Returns whether the notification was posted.
 *
 * When it can't be posted (notifications off) the reminder is flagged [Reminder.missed]: a one-off
 * stays undelivered, so the next restore (boot, clock change) tries again, and Home shows the count.
 */
class FireReminderUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val contacts: ContactRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: AlarmScheduler,
    private val calculator: ReminderTimeCalculator,
) {
    fun cancelNotification(reminderId: Long) = notifier.cancel(reminderId.toInt())

    suspend operator fun invoke(reminderId: Long): Boolean {
        val reminder = reminders.get(reminderId) ?: return false
        if (reminder.delivered || reminder.paused || reminder.triggerAtMillis > calculator.inMinutes(0)) return false
        val contact = reminder.contactId?.let { contacts.get(it) }
        val shown = notifier.show(reminder, contact)

        // Reminders saved before the wanted time was stored take it from this trigger, once.
        val clockTime = reminder.localTime
            ?: calculator.clockTime(reminder.triggerAtMillis).takeIf { reminder.repeat.keepsClockTime }
        val nextTrigger = calculator.next(reminder.repeat, reminder.triggerAtMillis, clockTime)
        when {
            nextTrigger != null -> scheduler.schedule(
                reminders.save(reminder.copy(triggerAtMillis = nextTrigger, localTime = clockTime, missed = !shown)),
            )
            // No next time could be worked out (out of range): keep the repeat in the list, don't
            // mark it delivered, which would delete it with the old one-offs.
            reminder.repeat.isRepeating -> reminders.save(reminder.copy(missed = !shown))
            else -> reminders.save(reminder.copy(delivered = shown, missed = !shown))
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
                // The copy is a fresh one-off: snoozing a paused repeat's notification must still ring.
                paused = false,
                localTime = null,
                missed = false,
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
 * Alarms are wiped on reboot. Re-arm pending reminders, skip missed repeat occurrences,
 * deliver overdue one-offs, and clean up old delivered reminders. Calendar anchors are also
 * reinterpreted in the current timezone after clock/timezone changes.
 *
 * One reminder that fails (a bad row, an alarm the system refuses) must not stop the rest being
 * re-armed, so each is tried on its own; the failures are returned for the caller to log.
 */
class RestoreRemindersUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val fire: FireReminderUseCase,
    private val cleanUp: CleanUpRemindersUseCase,
    private val clock: Clock,
    private val calculator: ReminderTimeCalculator = ReminderTimeCalculator(clock),
) {
    /** A reminder that couldn't be restored; [reminderId] is null for the clean-up before them. */
    data class Failure(val reminderId: Long?, val error: Exception)

    suspend operator fun invoke(): List<Failure> {
        val failures = mutableListOf<Failure>()
        attempt(null, failures) { cleanUp() }
        val now = clock.nowMillis()
        reminders.getPending().filterNot { it.paused }.forEach { reminder ->
            attempt(reminder.id, failures) { restore(reminder, now) }
        }
        return failures
    }

    private suspend fun attempt(reminderId: Long?, failures: MutableList<Failure>, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += Failure(reminderId, e)
        }
    }

    private suspend fun restore(reminder: Reminder, now: Long) {
        val rebuild = reminder.rebuildsFromLocalTime()
        when {
            rebuild || (reminder.repeat.isRepeating && reminder.triggerAtMillis <= now) -> {
                val previous = if (rebuild) now - 1 else reminder.triggerAtMillis
                calculator.next(reminder.repeat, previous, reminder.localTime)?.let { next ->
                    scheduler.schedule(reminders.save(reminder.copy(triggerAtMillis = next)))
                }
            }
            reminder.triggerAtMillis > now -> scheduler.schedule(reminder)
            else -> fire(reminder.id)
        }
    }
}

/**
 * Calendar repeats and clock-time repeats with a stored [Reminder.localTime] are rebuilt from the
 * wanted local time in the current zone, so a timezone or clock change moves them to the right moment.
 */
private fun Reminder.rebuildsFromLocalTime(): Boolean =
    repeat is Repeat.CalendarDays || repeat is Repeat.CalendarMonths ||
        (repeat.keepsClockTime && localTime != null)

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

/**
 * Pause or resume one reminder. Pausing drops its alarm but keeps it in the list. Resuming arms it
 * again: a repeat whose time passed while paused moves to its next time, and a one-off that's
 * overdue fires now (the same rules as after a reboot).
 */
class SetReminderPausedUseCase @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val fire: FireReminderUseCase,
    private val clock: Clock,
    private val calculator: ReminderTimeCalculator = ReminderTimeCalculator(clock),
) {
    suspend operator fun invoke(reminderId: Long, paused: Boolean): Reminder? {
        val reminder = reminders.get(reminderId) ?: return null
        if (reminder.paused == paused) return reminder
        if (paused) {
            scheduler.cancel(reminderId)
            notifier.cancel(reminderId.toInt())
            return reminders.save(reminder.copy(paused = true))
        }
        val now = clock.nowMillis()
        val resumed = reminders.save(reminder.copy(paused = false))
        when {
            resumed.triggerAtMillis > now -> scheduler.schedule(resumed)
            resumed.repeat.isRepeating -> {
                val previous = if (resumed.rebuildsFromLocalTime()) now - 1 else resumed.triggerAtMillis
                calculator.next(resumed.repeat, previous, resumed.localTime)?.let { next ->
                    return reminders.save(resumed.copy(triggerAtMillis = next)).also(scheduler::schedule)
                }
            }
            else -> fire(resumed.id)
        }
        return reminders.get(reminderId)
    }
}
