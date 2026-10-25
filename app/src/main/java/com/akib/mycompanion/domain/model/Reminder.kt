package com.akib.mycompanion.domain.model

import java.time.DayOfWeek

/**
 * A custom notification the user asked for.
 *
 * @property triggerAtMillis wall-clock time (epoch millis) when it should fire next.
 * @property contactId when set, the notification gets a "Call on WhatsApp" button for that person.
 * @property delivered true once a one-off reminder has fired (kept briefly so Snooze still works).
 */
data class Reminder(
    val id: Long = 0,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val repeat: Repeat = Repeat.Never,
    val contactId: Long? = null,
    val delivered: Boolean = false,
) {
    /** Notification ids are ints; reminder ids stay far below Int.MAX_VALUE on a personal device. */
    val notificationId: Int get() = id.toInt()
}

/**
 * How a reminder comes back after it fires. Daily, weekly and monthly keep the local clock time
 * (also across daylight-saving changes); [Every] counts real time.
 * Stored as text ([encode] / [decode]), so new kinds need no database migration.
 */
sealed interface Repeat {
    /** One-off. */
    data object Never : Repeat

    data object Daily : Repeat

    /** On these days, e.g. Sunday to Thursday for a Bangladeshi work week. */
    data class Weekly(val days: Set<DayOfWeek>) : Repeat

    /** On this day of every month; months without it use their last day (31 → 30 April, 28 February). */
    data class Monthly(val dayOfMonth: Int) : Repeat

    /** Every [minutes] (e.g. "drink water every 2 hours"). */
    data class Every(val minutes: Int) : Repeat

    val isRepeating: Boolean get() = this != Never

    fun isValid(): Boolean = when (this) {
        Never, Daily -> true
        is Weekly -> days.isNotEmpty()
        is Monthly -> dayOfMonth in 1..31
        is Every -> minutes in MIN_EVERY_MINUTES..MAX_EVERY_MINUTES
    }

    fun encode(): String = when (this) {
        Never -> "NONE"
        Daily -> "DAILY"
        is Weekly -> "WEEKLY:" + days.sorted().joinToString(",") { it.name.take(3) }
        is Monthly -> "MONTHLY:$dayOfMonth"
        is Every -> "EVERY:$minutes"
    }

    companion object {
        const val MIN_EVERY_MINUTES = 15
        const val MAX_EVERY_MINUTES = 7 * 24 * 60

        /** Unknown or damaged text reads as [Never], so one bad row can't break the list. */
        fun decode(text: String): Repeat {
            val kind = text.substringBefore(':')
            val value = text.substringAfter(':', missingDelimiterValue = "")
            val decoded = when (kind) {
                "DAILY" -> Daily
                "WEEKLY" -> Weekly(
                    value.split(
                        ','
                    ).mapNotNull { day -> DayOfWeek.entries.firstOrNull { it.name.take(3) == day } }.toSet(),
                )
                "MONTHLY" -> value.toIntOrNull()?.let(::Monthly)
                "EVERY" -> value.toIntOrNull()?.let(::Every)
                else -> Never
            }
            return decoded?.takeIf { it.isValid() } ?: Never
        }
    }
}

/** When the user wants to be reminded. */
sealed interface ReminderTime {
    /** [minutes] from now; 0 means immediately. */
    data class InMinutes(val minutes: Int) : ReminderTime

    /** Next occurrence of this local clock time (today if still ahead, otherwise tomorrow). */
    data class AtTime(val hour: Int, val minute: Int) : ReminderTime
}
