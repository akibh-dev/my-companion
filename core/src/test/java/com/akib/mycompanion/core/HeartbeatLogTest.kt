package com.akib.mycompanion.core

import com.akib.mycompanion.core.health.HeartbeatLog
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HeartbeatLogTest {

    private val clock = object : Clock {
        var now = 0L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val heartbeats = HeartbeatLog(File(Files.createTempDirectory("hb").toFile(), "beats.txt"), clock)

    @Test
    fun `gaps show when the app was stopped`() = runTest {
        listOf(0L, 15L, 30L, 120L, 135L).forEach { minute ->
            clock.now = minute * MINUTE
            heartbeats.beat()
        }
        clock.now = 140 * MINUTE

        assertThat(heartbeats.gaps()).containsExactly(HeartbeatLog.Gap(30 * MINUTE, 120 * MINUTE))
    }

    @Test
    fun `no beat for a long time is a gap up to now`() = runTest {
        heartbeats.beat()
        clock.now = 60 * MINUTE

        assertThat(heartbeats.gaps()).containsExactly(HeartbeatLog.Gap(0, 60 * MINUTE))
    }

    @Test
    fun `no beats yet means no times and no gaps`() = runTest {
        clock.now = 999 * MINUTE

        assertThat(heartbeats.times()).isEmpty()
        assertThat(heartbeats.gaps()).isEmpty()
    }

    @Test
    fun `beats on time leave no gaps`() = runTest {
        (0L..120L step 15).forEach { minute ->
            clock.now = minute * MINUTE
            heartbeats.beat()
        }

        assertThat(heartbeats.gaps()).isEmpty()
        assertThat(heartbeats.times()).hasSize(9)
    }

    @Test
    fun `a custom threshold finds shorter gaps`() = runTest {
        listOf(0L, 15L, 40L).forEach { minute ->
            clock.now = minute * MINUTE
            heartbeats.beat()
        }

        assertThat(heartbeats.gaps(thresholdMillis = 20 * MINUTE))
            .containsExactly(HeartbeatLog.Gap(15 * MINUTE, 40 * MINUTE))
    }

    @Test
    fun `only the newest beats are kept`() = runTest {
        repeat(HeartbeatLog.MAX_BEATS + 20) { i ->
            clock.now = i * 15 * MINUTE
            heartbeats.beat()
        }

        val times = heartbeats.times()
        assertThat(times).hasSize(HeartbeatLog.MAX_BEATS)
        assertThat(times.first()).isEqualTo(20 * 15 * MINUTE)
        assertThat(times.last()).isEqualTo((HeartbeatLog.MAX_BEATS + 19) * 15 * MINUTE)
    }

    @Test
    fun `a damaged line in the file is skipped`() = runTest {
        val file = File(Files.createTempDirectory("hb").toFile(), "beats.txt")
        file.writeText("60000\nnot a time\n\n 120000 \n")

        assertThat(HeartbeatLog(file, clock).times()).containsExactly(60_000L, 120_000L).inOrder()
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
