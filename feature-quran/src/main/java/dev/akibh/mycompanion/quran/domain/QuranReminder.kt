package dev.akibh.mycompanion.quran.domain

import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/**
 * The daily "read Quran" reminder. It is an ordinary reminder of the Reminders feature (so it shows
 * in that list, with snooze and sounds); the app implements this so the Quran module needn't know it.
 */
interface QuranReminder {
    /** The reminder's time, or null when there is none. */
    val time: Flow<LocalTime?>

    /** Sets the daily reminder to [time], or removes it when null. */
    suspend fun set(time: LocalTime?)

    companion object {
        /** Where the reminder's notification opens: the reader, at the last place read. */
        const val ROUTE = "quran/read/0"
    }
}
