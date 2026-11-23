package dev.akibh.mycompanion.presentation

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.fakes.FakeClock
import dev.akibh.mycompanion.fakes.millisAt
import dev.akibh.mycompanion.presentation.contacts.initials
import dev.akibh.mycompanion.presentation.reminders.ReminderTimeFormatter
import java.util.Locale
import org.junit.Test

class FormattingTest {

    // Friday 23 Oct 2026, 10:00
    private val formatter = ReminderTimeFormatter(FakeClock(now = millisAt(2026, 10, 23, 10, 0)), Locale.US)

    @Test
    fun `today and tomorrow are spelled out`() {
        assertThat(formatter.format(millisAt(2026, 10, 23, 17, 30))).isEqualTo("Today, 5:30 PM")
        assertThat(formatter.format(millisAt(2026, 10, 24, 8, 0))).isEqualTo("Tomorrow, 8:00 AM")
    }

    @Test
    fun `later days show weekday and date`() {
        assertThat(formatter.format(millisAt(2026, 10, 26, 9, 0))).isEqualTo("Mon 26 Oct, 9:00 AM")
    }

    @Test
    fun `a date in another year shows the year`() {
        assertThat(formatter.format(millisAt(2027, 10, 25, 9, 0))).isEqualTo("Mon 25 Oct 2027, 9:00 AM")
        assertThat(formatter.format(millisAt(2026, 12, 31, 9, 0))).isEqualTo("Thu 31 Dec, 9:00 AM")
    }

    @Test
    fun `clock time label`() {
        assertThat(formatter.clockTime(8, 5)).isEqualTo("8:05 AM")
        assertThat(formatter.clockTime(0, 0)).isEqualTo("12:00 AM")
    }

    @Test
    fun `phones set to 24-hour time get 24-hour times`() {
        val formatter = ReminderTimeFormatter(FakeClock(now = millisAt(2026, 10, 23, 10, 0)), Locale.US) { true }

        assertThat(formatter.format(millisAt(2026, 10, 23, 17, 30))).isEqualTo("Today, 17:30")
        assertThat(formatter.clockTime(8, 5)).isEqualTo("08:05")
    }

    @Test
    fun `initials for the avatar`() {
        assertThat(initials("Akib Hasan")).isEqualTo("AH")
        assertThat(initials("  akib  ")).isEqualTo("A")
        assertThat(initials("Abdullah Al Mamun")).isEqualTo("AM")
        assertThat(initials("")).isEqualTo("?")
    }
}
