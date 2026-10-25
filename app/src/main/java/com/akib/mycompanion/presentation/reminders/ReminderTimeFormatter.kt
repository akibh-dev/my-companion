package com.akib.mycompanion.presentation.reminders

import android.content.Context
import android.text.format.DateFormat
import com.akib.mycompanion.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/**
 * "Today, 5:30 PM", "Tomorrow, 8:00 AM", "Mon 28 Oct, 9:00 AM" — or "Today, 17:30" when the phone
 * uses 24-hour time ([is24Hour] is asked each time, so changing the setting applies at once).
 */
class ReminderTimeFormatter(
    private val clock: Clock,
    locale: Locale,
    private val is24Hour: () -> Boolean = { false },
) {

    @Inject constructor(clock: Clock, @ApplicationContext context: Context) :
        this(clock, Locale.getDefault(), { DateFormat.is24HourFormat(context) })

    private val time12 = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val time: DateTimeFormatter get() = if (is24Hour()) time24 else time12
    private val date = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun format(epochMillis: Long): String {
        val zone = clock.zone
        val target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone).toLocalDate()
        val day = when (ChronoUnit.DAYS.between(today, target.toLocalDate())) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> target.format(date)
        }
        return "$day, ${target.format(time)}"
    }

    /** "8:05 AM" for a picked hour/minute. */
    fun clockTime(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(time)
}
