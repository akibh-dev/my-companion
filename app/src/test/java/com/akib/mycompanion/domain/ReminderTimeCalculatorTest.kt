package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.ReminderTime
import com.akib.mycompanion.domain.time.ReminderTimeCalculator
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.millisAt
import com.google.common.truth.Truth.assertThat
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
        assertThat(calculator.nextDaily(millisAt(2026, 10, 23, 8, 0))).isEqualTo(millisAt(2026, 10, 24, 8, 0))
    }

    @Test
    fun `next daily skips days missed while the phone was off`() {
        clock.now = millisAt(2026, 10, 23, 9, 0)
        assertThat(calculator.nextDaily(millisAt(2026, 10, 20, 8, 0))).isEqualTo(millisAt(2026, 10, 24, 8, 0))
    }

    @Test
    fun `next daily keeps the local time across a daylight saving change`() {
        val london = ZoneId.of("Europe/London")
        // UK clocks go forward on Sunday 29 March 2026.
        val londonClock = FakeClock(now = millisAt(2026, 3, 28, 9, 30, london), zone = london)
        val next = ReminderTimeCalculator(londonClock).nextDaily(millisAt(2026, 3, 28, 9, 0, london))

        val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), london)
        assertThat(local.toLocalDate().dayOfMonth).isEqualTo(29)
        assertThat(local.hour).isEqualTo(9)
        assertThat(local.minute).isEqualTo(0)
    }
}
