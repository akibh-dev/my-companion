package dev.akibh.mycompanion.di

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.log.LogLevel
import java.nio.file.Files
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AppScopeTest {

    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)
    private val scope = CoroutinesModule.appScope(log)

    @Test
    fun `a failing job is logged and the scope keeps working`() = runBlocking {
        scope.launch { error("bad reminder") }.join()
        var ranAfter = false
        scope.launch { ranAfter = true }.join()

        assertThat(ranAfter).isTrue()
        val line = log.read().single()
        assertThat(line.level).isEqualTo(LogLevel.ERROR)
        assertThat(line.tag).isEqualTo("AppScope")
        assertThat(line.message).contains("IllegalStateException: bad reminder")
    }

    @Test
    fun `cancelling a job is not an error`() = runBlocking {
        scope.launch { cancel() }.join()

        assertThat(log.read()).isEmpty()
    }
}
