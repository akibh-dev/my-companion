package com.akib.mycompanion.capture

import com.akib.mycompanion.capture.domain.VolumeComboDetector
import com.akib.mycompanion.capture.domain.VolumeComboDetector.Companion.VOLUME_DOWN
import com.akib.mycompanion.capture.domain.VolumeComboDetector.Companion.VOLUME_UP
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class VolumeComboDetectorTest {

    private val clock = object : Clock {
        var now = 0L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("UTC")
    }
    private val detector = VolumeComboDetector(clock)

    @Test
    fun `both keys held for a second fire once`() {
        assertThat(detector.onKey(VOLUME_UP, down = true, time = 0)).isFalse()
        assertThat(detector.onKey(VOLUME_DOWN, down = true, time = 100)).isFalse()
        // Auto-repeat while held.
        assertThat(detector.onKey(VOLUME_UP, down = true, time = 600)).isFalse()
        assertThat(detector.onKey(VOLUME_UP, down = true, time = 1_100)).isTrue()
        assertThat(detector.onKey(VOLUME_UP, down = true, time = 1_600)).isFalse()
        assertThat(detector.onKey(VOLUME_DOWN, down = true, time = 3_000)).isFalse()
    }

    @Test
    fun `the hold counts from when the second key went down`() {
        detector.onKey(VOLUME_DOWN, down = true, time = 0)
        detector.onKey(VOLUME_UP, down = true, time = 2_000)
        assertThat(detector.millisUntilFire(now = 2_000)).isEqualTo(1_000)
        assertThat(detector.check(now = 2_999)).isFalse()
        assertThat(detector.check(now = 3_000)).isTrue()
    }

    @Test
    fun `a pending hold fires from the clock without more key events`() {
        detector.onKey(VOLUME_UP, down = true, time = 0)
        detector.onKey(VOLUME_DOWN, down = true, time = 0)
        clock.now = 1_000
        assertThat(detector.check()).isTrue()
        assertThat(detector.millisUntilFire()).isNull()
    }

    @Test
    fun `single presses never fire`() {
        assertThat(detector.onKey(VOLUME_UP, down = true, time = 0)).isFalse()
        assertThat(detector.check(now = 5_000)).isFalse()
        assertThat(detector.onKey(VOLUME_UP, down = false, time = 5_000)).isFalse()
        assertThat(detector.onKey(VOLUME_DOWN, down = true, time = 6_000)).isFalse()
        assertThat(detector.check(now = 9_000)).isFalse()
        assertThat(detector.millisUntilFire(now = 9_000)).isNull()
    }

    @Test
    fun `letting go too early cancels`() {
        detector.onKey(VOLUME_UP, down = true, time = 0)
        detector.onKey(VOLUME_DOWN, down = true, time = 0)
        detector.onKey(VOLUME_DOWN, down = false, time = 900)
        assertThat(detector.check(now = 1_500)).isFalse()
    }

    @Test
    fun `releasing and holding again fires again`() {
        detector.onKey(VOLUME_UP, down = true, time = 0)
        detector.onKey(VOLUME_DOWN, down = true, time = 0)
        assertThat(detector.check(now = 1_000)).isTrue()
        detector.onKey(VOLUME_UP, down = false, time = 1_200)
        detector.onKey(VOLUME_DOWN, down = false, time = 1_250)

        detector.onKey(VOLUME_UP, down = true, time = 5_000)
        detector.onKey(VOLUME_DOWN, down = true, time = 5_050)
        assertThat(detector.onKey(VOLUME_DOWN, down = true, time = 6_050)).isTrue()
    }

    @Test
    fun `other keys are ignored`() {
        detector.onKey(VOLUME_UP, down = true, time = 0)
        assertThat(detector.onKey(KEYCODE_POWER, down = true, time = 0)).isFalse()
        assertThat(detector.check(now = 2_000)).isFalse()
    }

    @Test
    fun `reset forgets held keys`() {
        detector.onKey(VOLUME_UP, down = true, time = 0)
        detector.onKey(VOLUME_DOWN, down = true, time = 0)
        detector.reset()
        assertThat(detector.check(now = 2_000)).isFalse()
    }

    private companion object {
        const val KEYCODE_POWER = 26
    }
}
