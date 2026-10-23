package com.akib.mycompanion.domain.model

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
    val repeat: Repeat = Repeat.NONE,
    val contactId: Long? = null,
    val delivered: Boolean = false,
) {
    /** Notification ids are ints; reminder ids stay far below Int.MAX_VALUE on a personal device. */
    val notificationId: Int get() = id.toInt()
}

enum class Repeat { NONE, DAILY }

/** When the user wants to be reminded. */
sealed interface ReminderTime {
    /** [minutes] from now; 0 means immediately. */
    data class InMinutes(val minutes: Int) : ReminderTime

    /** Next occurrence of this local clock time (today if still ahead, otherwise tomorrow). */
    data class AtTime(val hour: Int, val minute: Int) : ReminderTime
}
