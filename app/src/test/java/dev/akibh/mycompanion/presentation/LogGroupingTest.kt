package dev.akibh.mycompanion.presentation

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.log.LogLevel
import dev.akibh.mycompanion.core.log.LogLine
import dev.akibh.mycompanion.presentation.log.groupLog
import java.time.LocalDate
import org.junit.Test

class LogGroupingTest {
    private val today = LocalDate.of(2026, 11, 22)

    private fun line(time: String, message: String = "m") = LogLine(time, LogLevel.INFO, "Tag", message)

    @Test
    fun `lines are grouped by day and hour, newest first, keeping their order`() {
        val lines = listOf(
            line("2026-11-22 13:47:48.058", "a"),
            line("2026-11-22 13:02:00.000", "b"),
            line("2026-11-22 09:15:00.000", "c"),
            line("2026-11-21 23:59:59.999", "d"),
        )

        val groups = groupLog(lines, today)

        assertThat(groups.map { it.label }).containsExactly("Today · 13:00", "Today · 09:00", "Yesterday · 23:00")
            .inOrder()
        assertThat(groups.first().lines.map { it.message }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `an unreadable time still shows its line`() {
        val groups = groupLog(listOf(line("garbled")), today)

        assertThat(groups.single().lines.single().time).isEqualTo("garbled")
    }

    @Test
    fun `an empty log has no groups`() {
        assertThat(groupLog(emptyList(), today)).isEmpty()
    }
}
