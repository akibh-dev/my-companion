package com.akib.mycompanion.presentation.reminders

import android.content.Context
import android.text.format.DateFormat
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * "Today, 5:30 PM", "Tomorrow, 8:00 AM", "Mon 28 Oct, 9:00 AM" — or "Today, 17:30" when the phone
 * uses 24-hour time ([is24Hour] is asked each time, so changing the setting applies at once).
 */
class ReminderTimeFormatter(
    private val clock: Clock,
    private val locale: Locale,
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

    /** Days in the order this locale's calendar shows them (Sunday first in the US, Monday in the UK…). */
    val week: List<DayOfWeek> = WeekFields.of(locale).firstDayOfWeek.let { first -> (0L..6L).map(first::plus) }

    fun dayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)

    /** "Every day", "Every Sun, Mon, Thu", "Every month on the 24th", "Every 2 h"; null for a one-off. */
    fun repeat(repeat: Repeat): String? = when (repeat) {
        Repeat.Never -> null
        Repeat.Daily -> "Every day"
        is Repeat.Weekly ->
            if (repeat.days.size == 7) {
                "Every day"
            } else {
                "Every " + week.filter {
                    it in repeat.days
                }.joinToString(", ", transform = ::dayShort)
            }
        is Repeat.Monthly -> "Every month on the ${ordinal(repeat.dayOfMonth)}"
        is Repeat.Every -> "Every ${duration(repeat.minutes)}"
    }

    /** 30 → "30 min", 120 → "2 h", 90 → "1 h 30 min". */
    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it h" },
            m.takeIf { it > 0 }?.let { "$it min" }
        ).joinToString(" ")
    }

    private fun ordinal(day: Int): String = day.toString() + when {
        day % 100 in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}
