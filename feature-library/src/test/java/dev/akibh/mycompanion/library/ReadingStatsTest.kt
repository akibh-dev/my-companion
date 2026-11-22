package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.domain.ReadingGoal
import dev.akibh.mycompanion.library.domain.ReadingStats
import dev.akibh.mycompanion.library.domain.ReadingTimer
import java.time.LocalDate
import org.junit.Test

class ReadingStatsTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val day = today.toEpochDay()

    @Test
    fun `today, the goal and the 30-day chart`() {
        val status = ReadingStats.status(
            mapOf(day to 25 * 60L, day - 1 to 600L, day - 40 to 999L),
            emptyList(),
            ReadingGoal(minutesPerDay = 20),
            today,
        )
        assertThat(status.todaySeconds).isEqualTo(1500)
        assertThat(status.goalMet).isTrue()
        assertThat(status.recent).hasSize(ReadingStats.CHART_DAYS)
        assertThat(status.recent.takeLast(2)).containsExactly(600L, 1500L).inOrder()
        assertThat(status.totalSeconds).isEqualTo(1500 + 600 + 999)
    }

    @Test
    fun `the streak survives until today ends, and a few seconds don't count`() {
        val days = mapOf(day - 1 to 300L, day - 2 to 61L, day - 3 to 30L, day - 4 to 900L)
        assertThat(ReadingStats.status(days, emptyList(), ReadingGoal(), today).streak).isEqualTo(2)
        assertThat(ReadingStats.status(days + (day to 120L), emptyList(), ReadingGoal(), today).streak).isEqualTo(3)
        assertThat(ReadingStats.status(mapOf(day - 2 to 600L), emptyList(), ReadingGoal(), today).streak).isEqualTo(0)
    }

    @Test
    fun `books finished count for this year only`() {
        val finished = listOf(today, today.minusMonths(3), today.minusYears(1))
        assertThat(ReadingStats.status(emptyMap(), finished, ReadingGoal(), today).finishedThisYear).isEqualTo(2)
    }

    @Test
    fun `reading time stops two minutes after the last page turn`() {
        val timer = ReadingTimer(idleMillis = 120_000)
        timer.resume(0)
        assertThat(timer.activity(60_000)).isEqualTo(60_000) // turned a page after a minute
        assertThat(timer.take(150_000)).isEqualTo(90_000) // still within two minutes of it
        assertThat(timer.take(600_000)).isEqualTo(30_000) // idle from 180 s: only up to then
        assertThat(timer.activity(610_000)).isEqualTo(0) // picking it up again starts afresh
        assertThat(timer.take(640_000)).isEqualTo(30_000)
    }

    @Test
    fun `nothing counts while the reader is in the background`() {
        val timer = ReadingTimer()
        timer.resume(0)
        assertThat(timer.pause(10_000)).isEqualTo(10_000)
        assertThat(timer.take(50_000)).isEqualTo(0)
        assertThat(timer.activity(60_000)).isEqualTo(0)
    }
}
