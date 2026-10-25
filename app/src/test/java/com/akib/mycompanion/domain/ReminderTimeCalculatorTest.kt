package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.millisAt
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class ReminderTimeCalculatorTest {

    private val clock = FakeClock(now = millisAt(2026, 10, 23, 10, 0))
    private val calculator = ReminderTimeCalculator(clock)

    @Test
    fun `in minutes adds to now`() {
        assertThat(calculator.firstTrigger(ReminderTime.InMinutes(15))).isEqualTo(millisAt(2026, 10, 23, 10, 15))
    }

    @Test
    fun `negative minutes mean now`() {
        assertThat(calculator.firstTrigger(ReminderTime.InMinutes(-5))).isEqualTo(clock.now)
    }

    @Test
    fun `clock time later today fires today`() {
        assertThat(calculator.firstTrigger(ReminderTime.AtTime(17, 30))).isEqualTo(millisAt(2026, 10, 23, 17, 30))
    }

    @Test
    fun `clock time already passed fires tomorrow`() {
        assertThat(calculator.firstTrigger(ReminderTime.AtTime(8, 0))).isEqualTo(millisAt(2026, 10, 24, 8, 0))
    }

    @Test
    fun `clock time equal to now fires tomorrow`() {
        assertThat(calculator.firstTrigger(ReminderTime.AtTime(10, 0))).isEqualTo(millisAt(2026, 10, 24, 10, 0))
    }

    @Test
    fun `next daily is the following day`() {
        clock.now = millisAt(2026, 10, 23, 8, 1)
        assertThat(calculator.next(Repeat.Daily, millisAt(2026, 10, 23, 8, 0))).isEqualTo(millisAt(2026, 10, 24, 8, 0))
    }

    @Test
    fun `next daily skips days missed while the phone was off`() {
        clock.now = millisAt(2026, 10, 23, 9, 0)
        assertThat(calculator.next(Repeat.Daily, millisAt(2026, 10, 20, 8, 0))).isEqualTo(millisAt(2026, 10, 24, 8, 0))
    }

    @Test
    fun `next daily keeps the local time across a daylight saving change`() {
        val london = ZoneId.of("Europe/London")
        // UK clocks go forward on Sunday 29 March 2026.
        val londonClock = FakeClock(now = millisAt(2026, 3, 28, 9, 30, london), zone = london)
        val next = ReminderTimeCalculator(londonClock).next(Repeat.Daily, millisAt(2026, 3, 28, 9, 0, london))!!

        val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), london)
        assertThat(local.toLocalDate().dayOfMonth).isEqualTo(29)
        assertThat(local.hour).isEqualTo(9)
        assertThat(local.minute).isEqualTo(0)
    }

    // ---- Recurrence (now: Friday 23 Oct 2026, 10:00) ----

    @Test
    fun `on chosen days the first time waits for a matching day`() {
        val sundays = Repeat.Weekly(setOf(DayOfWeek.SUNDAY))
        val fridaysAndSundays = Repeat.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY))

        assertThat(calculator.firstTrigger(ReminderTime.AtTime(9, 0), sundays)).isEqualTo(millisAt(2026, 10, 25, 9, 0))
        assertThat(calculator.firstTrigger(ReminderTime.AtTime(17, 0), fridaysAndSundays))
            .isEqualTo(millisAt(2026, 10, 23, 17, 0))
        assertThat(calculator.firstTrigger(ReminderTime.InMinutes(0), sundays)).isEqualTo(clock.now)
    }

    @Test
    fun `on chosen days the next time is the next ticked day, skipping missed ones`() {
        val sundaysAndThursdays = Repeat.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY))

        clock.now = millisAt(2026, 10, 25, 9, 1)
        assertThat(
            calculator.next(sundaysAndThursdays, millisAt(2026, 10, 25, 9, 0))
        ).isEqualTo(millisAt(2026, 10, 29, 9, 0))

        clock.now = millisAt(2026, 11, 6, 10, 0) // phone was off for a while
        assertThat(
            calculator.next(sundaysAndThursdays, millisAt(2026, 10, 25, 9, 0))
        ).isEqualTo(millisAt(2026, 11, 8, 9, 0))
    }

    @Test
    fun `monthly on the 31st uses short months' last day without drifting`() {
        val on31st = Repeat.Monthly(31)

        clock.now = millisAt(2027, 1, 31, 8, 1)
        val february = calculator.next(on31st, millisAt(2027, 1, 31, 8, 0))!!
        assertThat(february).isEqualTo(millisAt(2027, 2, 28, 8, 0))

        clock.now = february + 60_000
        assertThat(calculator.next(on31st, february)).isEqualTo(millisAt(2027, 3, 31, 8, 0))
    }

    @Test
    fun `every few hours counts real time and catches up after the phone was off`() {
        val everyTwoHours = Repeat.Every(120)

        clock.now = millisAt(2026, 10, 23, 10, 1)
        assertThat(
            calculator.next(everyTwoHours, millisAt(2026, 10, 23, 10, 0))
        ).isEqualTo(millisAt(2026, 10, 23, 12, 0))

        clock.now = millisAt(2026, 10, 23, 15, 30)
        assertThat(
            calculator.next(everyTwoHours, millisAt(2026, 10, 23, 10, 0))
        ).isEqualTo(millisAt(2026, 10, 23, 16, 0))
    }

    @Test
    fun `a one-off has no next time`() {
        assertThat(calculator.next(Repeat.Never, clock.now)).isNull()
    }

    @Test
    fun `repeats are stored as text and damaged text reads as one-off`() {
        val all = listOf(
            Repeat.Never,
            Repeat.Daily,
            Repeat.Weekly(setOf(DayOfWeek.SUNDAY, DayOfWeek.THURSDAY)),
            Repeat.Monthly(31),
            Repeat.Every(90),
        )
        all.forEach { assertThat(Repeat.decode(it.encode())).isEqualTo(it) }

        assertThat(Repeat.decode("DAILY")).isEqualTo(Repeat.Daily) // rows saved by older versions
        assertThat(Repeat.decode("NONE")).isEqualTo(Repeat.Never)
        assertThat(Repeat.decode("WEEKLY:")).isEqualTo(Repeat.Never)
        assertThat(Repeat.decode("EVERY:5")).isEqualTo(Repeat.Never) // below the 15 minute minimum
        assertThat(Repeat.decode("garbage")).isEqualTo(Repeat.Never)
    }
}
