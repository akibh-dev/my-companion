package com.akib.mycompanion.domain.time

import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class ReminderTimeCalculator @Inject constructor(private val clock: Clock) {

    /**
     * First time a reminder created now should fire. For [Repeat.Weekly] and [Repeat.Monthly] that's
     * the first matching day at that clock time (a Friday-only reminder set on Sunday waits for Friday).
     * "Now" always fires at once.
     */
    fun firstTrigger(time: ReminderTime, repeat: Repeat = Repeat.Never): Long {
        val now = clock.nowMillis()
        val base = when (time) {
            is ReminderTime.InMinutes -> Math.addExact(
                now,
                Math.multiplyExact(time.minutes.coerceAtLeast(0).toLong(), MINUTE)
            )
            is ReminderTime.AtDateTime -> time.dateTime.atZone(clock.zone).toInstant().toEpochMilli()
            is ReminderTime.AtTime -> {
                val today = zoned(now).with(LocalTime.of(time.hour, time.minute))
                val candidate = today.toInstant().toEpochMilli()
                if (candidate > now) candidate else today.plusDays(1).toInstant().toEpochMilli()
            }
        }
        if ((time is ReminderTime.InMinutes && time.minutes <= 0) || !repeat.isValid()) return base
        var day = zoned(base)
        while (!matches(repeat, day)) day = day.plusDays(1)
        return day.toInstant().toEpochMilli()
    }

    /**
     * When a repeating reminder fires next after [previousTrigger], skipping any times missed while
     * the phone was off. Null for a one-off.
     */
    fun next(repeat: Repeat, previousTrigger: Long): Long? =
        runCatching { nextChecked(repeat, previousTrigger) }.getOrNull()

    private fun nextChecked(repeat: Repeat, previousTrigger: Long): Long? {
        if (repeat == Repeat.Never || !repeat.isValid()) return null
        val after = maxOf(clock.nowMillis(), previousTrigger)
        if (repeat is Repeat.Every) {
            val step = repeat.minutes * MINUTE
            val stepsMissed = (Math.subtractExact(after, previousTrigger).coerceAtLeast(0) / step) + 1
            return Math.addExact(previousTrigger, Math.multiplyExact(stepsMissed, step))
        }
        if (repeat is Repeat.CalendarDays) return calendarNext(repeat.anchor, repeat.count, after, months = false)
        if (repeat is Repeat.CalendarMonths) return calendarNext(repeat.anchor, repeat.count, after, months = true)
        val from = zoned(previousTrigger)
        val localNow = zoned(after)
        // Jump directly to the current calendar period, then check at most the next week/month.
        return when (repeat) {
            Repeat.Daily -> {
                var candidate = localNow.toLocalDate().atTime(from.toLocalTime()).atZone(clock.zone)
                if (candidate.toInstant().toEpochMilli() <= after) candidate = candidate.plusDays(1)
                candidate.toInstant().toEpochMilli()
            }
            is Repeat.Weekly -> {
                var candidate = localNow.toLocalDate().atTime(from.toLocalTime()).atZone(clock.zone)
                while (candidate.toInstant().toEpochMilli() <= after || candidate.dayOfWeek !in repeat.days) {
                    candidate = candidate.plusDays(1)
                }
                candidate.toInstant().toEpochMilli()
            }
            is Repeat.Monthly -> {
                var month = YearMonth.from(localNow)
                var candidate = month.atDay(minOf(repeat.dayOfMonth, month.lengthOfMonth()))
                    .atTime(from.toLocalTime()).atZone(clock.zone)
                if (candidate.toInstant().toEpochMilli() <= after) {
                    month = month.plusMonths(1)
                    candidate = month.atDay(minOf(repeat.dayOfMonth, month.lengthOfMonth()))
                        .atTime(from.toLocalTime()).atZone(clock.zone)
                }
                candidate.toInstant().toEpochMilli()
            }
            else -> null
        }
    }

    private fun calendarNext(anchor: LocalDateTime, count: Int, after: Long, months: Boolean): Long {
        val localAfter = zoned(after)
        val elapsed = if (months) {
            ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(localAfter))
        } else {
            ChronoUnit.DAYS.between(anchor.toLocalDate(), localAfter.toLocalDate())
        }
        var index = (elapsed / count).coerceAtLeast(0)
        fun occurrence(): Long {
            val amount = Math.multiplyExact(index, count.toLong())
            val date = if (months) anchor.plusMonths(amount) else anchor.plusDays(amount)
            return date.atZone(clock.zone).toInstant().toEpochMilli()
        }
        var candidate = occurrence()
        while (candidate <= after) {
            index++
            candidate = occurrence()
        }
        return candidate
    }

    /** Used by calendar recurrence to persist the requested local date/time, not a UTC offset. */
    fun localDateTime(millis: Long): LocalDateTime = zoned(millis).toLocalDateTime()

    fun inMinutes(minutes: Int): Long = clock.nowMillis() + minutes * MINUTE

    /** The local date of [millis], e.g. to show "every month on the 24th". */
    fun date(millis: Long): LocalDate = zoned(millis).toLocalDate()

    private fun matches(repeat: Repeat, day: ZonedDateTime): Boolean = when (repeat) {
        is Repeat.Weekly -> day.dayOfWeek in repeat.days
        is Repeat.Monthly -> day.dayOfMonth == minOf(repeat.dayOfMonth, day.toLocalDate().lengthOfMonth())
        else -> true
    }

    private fun zoned(millis: Long): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), clock.zone)

    companion object {
        const val MINUTE = 60_000L
    }
}
