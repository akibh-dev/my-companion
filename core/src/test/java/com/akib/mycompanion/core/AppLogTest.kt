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
}
