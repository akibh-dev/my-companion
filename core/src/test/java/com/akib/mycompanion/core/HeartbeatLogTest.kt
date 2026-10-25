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

    private companion object {
        const val MINUTE = 60_000L
    }
}
