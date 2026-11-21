package dev.akibh.mycompanion.library.domain

import java.time.LocalDate

/** Daily minutes to read, and (optionally) books to finish this year; 0 turns a goal off. */
data class ReadingGoal(val minutesPerDay: Int = 20, val booksPerYear: Int = 0)

data class ReadingStatus(
    val todaySeconds: Long,
    val goal: ReadingGoal,
    /** Days in a row with at least a minute read, up to today (or yesterday: today isn't over yet). */
    val streak: Int,
    /** Seconds read on each of the last [ReadingStats.CHART_DAYS] days, oldest first, today last. */
    val recent: List<Long>,
    val totalSeconds: Long,
    val finishedThisYear: Int,
) {
    val goalSeconds: Long get() = goal.minutesPerDay * 60L
    val goalMet: Boolean get() = goalSeconds > 0 && todaySeconds >= goalSeconds
}

object ReadingStats {
    const val CHART_DAYS = 30

    /** Less than this in a day doesn't keep a streak alive (a book opened by mistake). */
    const val STREAK_SECONDS = 60L

    /** [days]: seconds read per epoch day; [finished]: the days books were finished. */
    fun status(days: Map<Long, Long>, finished: List<LocalDate>, goal: ReadingGoal, today: LocalDate): ReadingStatus {
        val todayDay = today.toEpochDay()
        val counts = { day: Long -> (days[day] ?: 0L) >= STREAK_SECONDS }
        var day = if (counts(todayDay)) todayDay else todayDay - 1
        var streak = 0
        while (counts(day)) {
            streak++
            day--
        }
        return ReadingStatus(
            todaySeconds = days[todayDay] ?: 0L,
            goal = goal,
            streak = streak,
            recent = (CHART_DAYS - 1 downTo 0).map { days[todayDay - it] ?: 0L },
            totalSeconds = days.values.sum(),
            finishedThisYear = finished.count { it.year == today.year },
        )
    }
}

/**
 * Counts reading time, but only while the reader is in front and something happened (a page turn,
 * a scroll, a tap) in the last [idleMillis]: a book left open on the table doesn't count.
 * Times are milliseconds from any monotonic clock.
 */
class ReadingTimer(private val idleMillis: Long = IDLE_MILLIS) {
    private var lastActivity = Long.MIN_VALUE / 2
    private var lastTake: Long? = null

    /** The reader came to the front: counts as activity. */
    fun resume(now: Long) {
        lastTake = now
        lastActivity = now
    }

    /** The reader left the front; returns the time still to credit. */
    fun pause(now: Long): Long = take(now).also { lastTake = null }

    /** The reader did something; returns the time to credit up to now. */
    fun activity(now: Long): Long = take(now).also { lastActivity = now }

    /** Milliseconds read since the previous call; zero while paused or idle. */
    fun take(now: Long): Long {
        val from = lastTake ?: return 0L
        lastTake = now
        val end = minOf(now, lastActivity + idleMillis)
        return (end - from).coerceAtLeast(0L)
    }

    companion object {
        const val IDLE_MILLIS = 2 * 60_000L
    }
}
