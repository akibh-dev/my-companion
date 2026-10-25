package com.akib.mycompanion.domain.time

import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
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
            is ReminderTime.InMinutes -> now + time.minutes.coerceAtLeast(0) * MINUTE
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
    fun next(repeat: Repeat, previousTrigger: Long): Long? {
        val now = clock.nowMillis()
        if (repeat is Repeat.Every) {
            val step = repeat.minutes * MINUTE
            val stepsMissed = ((now - previousTrigger).coerceAtLeast(0) / step) + 1
            return previousTrigger + stepsMissed * step
        }
        if (repeat == Repeat.Never || !repeat.isValid()) return null
        // Calendar days, not 24 h steps, so the local clock time survives daylight-saving changes.
        var next = step(repeat, zoned(previousTrigger))
        while (next.toInstant().toEpochMilli() <= now) next = step(repeat, next)
        return next.toInstant().toEpochMilli()
    }

    fun inMinutes(minutes: Int): Long = clock.nowMillis() + minutes * MINUTE

    /** The local date of [millis], e.g. to show "every month on the 24th". */
    fun date(millis: Long): LocalDate = zoned(millis).toLocalDate()

    private fun step(repeat: Repeat, from: ZonedDateTime): ZonedDateTime = when (repeat) {
        is Repeat.Monthly -> {
            val nextMonth = from.withDayOfMonth(1).plusMonths(1)
            nextMonth.withDayOfMonth(minOf(repeat.dayOfMonth, nextMonth.toLocalDate().lengthOfMonth()))
        }
        else -> {
            var day = from.plusDays(1)
            while (!matches(repeat, day)) day = day.plusDays(1)
            day
        }
    }

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
