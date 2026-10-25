package com.akib.mycompanion.core

import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.log.LogLevel
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppLogTest {

    private val dir = Files.createTempDirectory("log").toFile()
    private val log = AppLog(dir, mirrorToLogcat = false)

    @Test
    fun `lines come back in order with level and tag`() = runTest {
        log.i("Rules", "Office rule on")
        log.w("Host", "not running", IllegalStateException("killed"))

        val lines = log.read()
        assertThat(lines.map { it.level }).containsExactly(LogLevel.INFO, LogLevel.WARN).inOrder()
        assertThat(lines[0].tag).isEqualTo("Rules")
        assertThat(lines[1].message).isEqualTo("not running (IllegalStateException: killed)")
    }

    @Test
    fun `a full file rolls over and only two files are kept`() = runTest {
        val line = "x".repeat(1_000)
        repeat(800) { log.i("Fill", "$it $line") }

        log.flush()
        assertThat(dir.listFiles()!!.map { it.name }).containsExactly("app.log", "app.1.log")
        assertThat(dir.listFiles()!!.sumOf { it.length() }).isLessThan(3 * AppLog.MAX_BYTES)
        assertThat(log.read().last().message).startsWith("799 ")
    }

    @Test
    fun `clear empties the log`() = runTest {
        log.i("A", "b")
        log.clear()
        assertThat(log.read()).isEmpty()
    }

    @Test
    fun `a message with line breaks stays one line`() = runTest {
        log.e("Vault", "first\nsecond\nthird", RuntimeException("boom"))

        val line = log.read().single()
        assertThat(line.level).isEqualTo(LogLevel.ERROR)
        assertThat(line.message).isEqualTo("first second third (RuntimeException: boom)")
    }

    @Test
    fun `colons in the message don't confuse the tag`() = runTest {
        log.i("Rules", "Office: ringer → silent")

        val line = log.read().single()
        assertThat(line.tag).isEqualTo("Rules")
        assertThat(line.message).isEqualTo("Office: ringer → silent")
    }

    @Test
    fun `a warning without an error has no brackets`() = runTest {
        log.w("Host", "not running")

        assertThat(log.read().single().message).isEqualTo("not running")
    }

    @Test
    fun `lines are parsed back, and anything else is skipped`() {
        val line = AppLog.parse("2026-10-25 18:00:00.123 W Places: left place 3")!!

        assertThat(line.time).isEqualTo("2026-10-25 18:00:00.123")
        assertThat(line.level).isEqualTo(LogLevel.WARN)
        assertThat(line.tag).isEqualTo("Places")
        assertThat(line.message).isEqualTo("left place 3")
        assertThat(AppLog.parse("garbage")).isNull()
        assertThat(AppLog.parse("2026-10-25 18:00:00.123 X Tag: unknown level")).isNull()
        assertThat(AppLog.parse("")).isNull()
    }

    @Test
    fun `reading an empty folder gives nothing`() = runTest {
        assertThat(AppLog(Files.createTempDirectory("empty").toFile(), mirrorToLogcat = false).read()).isEmpty()
    }
}
