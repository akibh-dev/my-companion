package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.model.Repeat
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.millisAt
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
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
        assertThat(Repeat.decode("EVERY:5")).isEqualTo(Repeat.Every(5))
        assertThat(Repeat.decode("EVERY:0")).isEqualTo(Repeat.Never)
        assertThat(Repeat.decode("garbage")).isEqualTo(Repeat.Never)
    }

    @Test
    fun `twelve minute intervals retain cadence after days offline`() {
        clock.now = millisAt(2026, 10, 27, 10, 7)
        assertThat(calculator.next(Repeat.Every(12), millisAt(2026, 10, 23, 10, 0)))
            .isEqualTo(millisAt(2026, 10, 27, 10, 12))
    }

    @Test
    fun `calendar months retain original day through short months and leap years`() {
        val repeat = Repeat.CalendarMonths(1, LocalDateTime.of(2028, 1, 31, 9, 0))
        clock.now = millisAt(2028, 1, 31, 9, 0)
        val february = calculator.next(repeat, clock.now)!!
        assertThat(february).isEqualTo(millisAt(2028, 2, 29, 9, 0))
        clock.now = february
        assertThat(calculator.next(repeat, february)).isEqualTo(millisAt(2028, 3, 31, 9, 0))
        clock.now = millisAt(2032, 2, 1, 8, 0)
        assertThat(calculator.next(repeat, february)).isEqualTo(millisAt(2032, 2, 29, 9, 0))
    }

    @Test
    fun `multi month intervals use anchor periods rather than last delivered month`() {
        val repeat = Repeat.CalendarMonths(3, LocalDateTime.of(2026, 1, 31, 18, 0))
        clock.now = millisAt(2026, 2, 15, 12, 0)
        assertThat(calculator.next(repeat, millisAt(2026, 1, 31, 18, 0)))
            .isEqualTo(millisAt(2026, 4, 30, 18, 0))
        clock.now = millisAt(2036, 6, 1, 10, 0)
        assertThat(calculator.next(repeat, millisAt(2026, 4, 30, 18, 0)))
            .isEqualTo(millisAt(2036, 7, 31, 18, 0))
    }

    @Test
    fun `calendar days keep local time across daylight saving rather than elapsed hours`() {
        val london = ZoneId.of("Europe/London")
        val previous = millisAt(2026, 3, 28, 9, 30, london)
        val localClock = FakeClock(previous, london)
        val calc = ReminderTimeCalculator(localClock)
        val repeat = Repeat.CalendarDays(2, LocalDateTime.of(2026, 3, 28, 9, 30))
        val next = calc.next(repeat, previous)
        assertThat(next).isEqualTo(millisAt(2026, 3, 30, 9, 30, london))
        assertThat(next!! - previous).isEqualTo(47 * 60 * 60_000L)
        assertThat(calc.next(Repeat.Every(48 * 60), previous)).isEqualTo(previous + 48 * 60 * 60_000L)
    }

    @Test
    fun `calendar anchors restore their wall clock time after a timezone change`() {
        val anchor = LocalDateTime.of(2026, 10, 20, 9, 0)
        val changed = ZoneId.of("Europe/London")
        val localClock = FakeClock(millisAt(2026, 10, 23, 10, 0, changed), changed)
        assertThat(
            ReminderTimeCalculator(localClock).next(Repeat.CalendarDays(2, anchor), millisAt(2026, 10, 22, 9, 0))
        )
            .isEqualTo(millisAt(2026, 10, 24, 9, 0, changed))
    }

    @Test
    fun `custom calendar repeats round trip and invalid count or anchors are rejected`() {
        val anchor = LocalDateTime.of(2026, 10, 23, 8, 15)
        listOf(Repeat.CalendarDays(3, anchor), Repeat.CalendarMonths(6, anchor)).forEach {
            assertThat(Repeat.decode(it.encode())).isEqualTo(it)
        }
        listOf("CALENDAR_DAYS:0:$anchor", "CALENDAR_MONTHS:121:$anchor", "CALENDAR_DAYS:2:bad").forEach {
            assertThat(Repeat.decode(it)).isEqualTo(Repeat.Never)
        }
        assertThat(calculator.next(Repeat.Every(0), clock.now)).isNull()
    }

    @Test
    fun `an explicit first date and time does not roll into tomorrow`() {
        val time = ReminderTime.AtDateTime(LocalDateTime.of(2027, 1, 15, 18, 30))
        assertThat(calculator.firstTrigger(time)).isEqualTo(millisAt(2027, 1, 15, 18, 30))
    }

    @Test
    fun `overflow and out of range calendar anchors fail safely`() {
        assertThat(Repeat.decode("CALENDAR_DAYS:2:${LocalDateTime.MAX}")).isEqualTo(Repeat.Never)
        assertThat(Repeat.decode("CALENDAR_MONTHS:2:${LocalDateTime.MIN}")).isEqualTo(Repeat.Never)
        assertThat(calculator.next(Repeat.Every(12), Long.MIN_VALUE)).isNull()
        val nearMaximum = FakeClock(Long.MAX_VALUE)
        assertThat(ReminderTimeCalculator(nearMaximum).next(Repeat.Every(12), Long.MAX_VALUE)).isNull()
    }
}
