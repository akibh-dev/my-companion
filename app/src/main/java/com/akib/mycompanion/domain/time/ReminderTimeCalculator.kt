package com.akib.mycompanion.domain.time

import com.akib.mycompanion.domain.model.ReminderTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

class ReminderTimeCalculator @Inject constructor(private val clock: Clock) {

    /** First time a reminder created now should fire. */
    fun firstTrigger(time: ReminderTime): Long {
        val now = clock.nowMillis()
        return when (time) {
            is ReminderTime.InMinutes -> now + time.minutes.coerceAtLeast(0) * MINUTE
            is ReminderTime.AtTime -> {
                val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), clock.zone)
                    .with(LocalTime.of(time.hour, time.minute))
                val candidate = today.toInstant().toEpochMilli()
                if (candidate > now) candidate else today.plusDays(1).toInstant().toEpochMilli()
            }
        }
    }

    /**
     * Next daily occurrence after [previousTrigger] that is still in the future.
     * Uses calendar days (not 24h) so the local clock time survives DST changes,
     * and skips any days missed while the phone was off.
     */
    fun nextDaily(previousTrigger: Long): Long {
        val now = clock.nowMillis()
        var next = ZonedDateTime.ofInstant(Instant.ofEpochMilli(previousTrigger), clock.zone).plusDays(1)
        while (next.toInstant().toEpochMilli() <= now) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    fun inMinutes(minutes: Int): Long = clock.nowMillis() + minutes * MINUTE

    companion object {
        const val MINUTE = 60_000L
    }
}
