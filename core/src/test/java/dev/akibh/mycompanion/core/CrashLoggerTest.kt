package dev.akibh.mycompanion.core

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.log.CrashLogger
import dev.akibh.mycompanion.core.log.LogLevel
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class CrashLoggerTest {

    private val dir = Files.createTempDirectory("log").toFile()
    private val log = AppLog(dir, mirrorToLogcat = false)
    private val original = Thread.getDefaultUncaughtExceptionHandler()

    @After
    fun restore() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    @Test
    fun `a crash is on disk before the previous handler gets it`() = runTest {
        val handedOn = mutableListOf<Throwable>()
        var onDiskWhenHandedOn = false
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            // Read the file directly: the previous handler usually kills the process.
            onDiskWhenHandedOn = dir.resolve("app.log").readText().contains("Crash: crashed on")
            handedOn += error
        }
        val crash = IllegalStateException("boom")

        CrashLogger.install(log)
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), crash)

        assertThat(handedOn).containsExactly(crash)
        assertThat(onDiskWhenHandedOn).isTrue()
        val line = log.read().single()
        assertThat(line.level).isEqualTo(LogLevel.ERROR)
        assertThat(line.tag).isEqualTo(CrashLogger.TAG)
        assertThat(line.message).contains("IllegalStateException: boom")
        assertThat(line.message).contains("CrashLoggerTest")
    }

    @Test
    fun `the root cause and where it happened are kept`() = runTest {
        val crash = RuntimeException("wrapper", IllegalArgumentException("bad id"))

        CrashLogger(log, previous = null).uncaughtException(Thread.currentThread(), crash)

        val message = log.read().single().message
        assertThat(message).contains("caused by IllegalArgumentException: bad id")
        assertThat(message).contains(" at CrashLoggerTest")
    }

    @Test
    fun `without a previous handler it only logs`() = runTest {
        CrashLogger(log, previous = null).uncaughtException(Thread.currentThread(), Error("oom-ish"))

        assertThat(log.read().single().message).contains("Error: oom-ish")
    }
}
