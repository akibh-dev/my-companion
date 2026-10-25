package com.akib.mycompanion.context.screenon

import com.akib.mycompanion.context.screenon.Samples.gravity
import com.akib.mycompanion.context.screenon.Samples.sample
import com.akib.mycompanion.context.screenon.Samples.times
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostureDetectorTest {

    /** Feeds samples from [from] to [to] and returns the answer after each, by time. */
    private fun PostureDetector.feed(
        from: Long,
        to: Long,
        pitch: Double,
        roll: Double = 0.0,
        shaking: Boolean = true,
    ): Map<Long, Boolean> = times(from, to).associateWith { update(sample(it, pitch, roll, shaking)) }

    @Test
    fun `angles are read from gravity`() {
        assertThat(PostureDetector.pitchDegrees(gravity(0.0))).isWithin(0.01).of(0.0)
        assertThat(PostureDetector.pitchDegrees(gravity(45.0))).isWithin(0.01).of(45.0)
        assertThat(PostureDetector.pitchDegrees(gravity(90.0))).isWithin(0.01).of(90.0)
        assertThat(PostureDetector.rollDegrees(gravity(45.0, roll = 20.0))).isWithin(0.01).of(20.0)
        assertThat(PostureDetector.rollDegrees(gravity(45.0, roll = -30.0))).isWithin(0.01).of(-30.0)
    }

    @Test
    fun `holding the phone up counts as raised after one and a half seconds`() {
        val detector = PostureDetector()

        val answers = detector.feed(0, 1_400, pitch = 45.0)

        assertThat(answers.values).doesNotContain(true)
        assertThat(detector.update(sample(1_500, 45.0))).isTrue()
    }

    @Test
    fun `lowering the phone stops counting only after three seconds`() {
        val detector = PostureDetector()
        detector.feed(0, 2_000, pitch = 45.0)

        val lowered = detector.feed(2_100, 5_000, pitch = 0.0)

        assertThat(lowered.values).doesNotContain(false)
        assertThat(detector.update(sample(5_100, 0.0))).isFalse()
    }

    @Test
    fun `a short glance away doesn't flicker`() {
        val detector = PostureDetector()
        detector.feed(0, 2_000, pitch = 45.0)

        val glance = detector.feed(2_100, 3_500, pitch = 5.0)
        val back = detector.feed(3_600, 8_000, pitch = 45.0)

        assertThat(glance.values).doesNotContain(false)
        assertThat(back.values).doesNotContain(false)
    }

    @Test
    fun `lying flat on a table is never raised`() {
        val detector = PostureDetector()

        assertThat(detector.feed(0, 10_000, pitch = 0.0).values).doesNotContain(true)
        assertThat(detector.feed(10_100, 20_000, pitch = 2.0, shaking = false).values).doesNotContain(true)
    }

    @Test
    fun `propped up still on a stand is not raised`() {
        val detector = PostureDetector()

        assertThat(detector.feed(0, 10_000, pitch = 45.0, shaking = false).values).doesNotContain(true)
    }

    @Test
    fun `putting the phone down on a stand ends raised once it has been still a while`() {
        val detector = PostureDetector()
        detector.feed(0, 2_000, pitch = 45.0)

        val resting = detector.feed(2_100, 10_000, pitch = 45.0, shaking = false)

        // Still for 2 s (until 4 s), then 3 s to let go (until 7 s).
        assertThat(resting.filterKeys { it <= 7_000 }.values).doesNotContain(false)
        assertThat(resting.filterKeys { it >= 7_100 }.values).doesNotContain(true)
    }

    @Test
    fun `screen facing away or upside down is not raised`() {
        assertThat(PostureDetector().feed(0, 5_000, pitch = 135.0).values).doesNotContain(true)
        assertThat(PostureDetector().feed(0, 5_000, pitch = -45.0).values).doesNotContain(true)
        assertThat(PostureDetector().feed(0, 5_000, pitch = 90.0).values).doesNotContain(true)
    }

    @Test
    fun `turned far sideways is not raised`() {
        assertThat(PostureDetector().feed(0, 5_000, pitch = 45.0, roll = 60.0).values).doesNotContain(true)
        assertThat(PostureDetector().feed(0, 5_000, pitch = 45.0, roll = -60.0).values).doesNotContain(true)
    }

    @Test
    fun `sensitivity widens or narrows the angles`() {
        fun raisedWith(sensitivity: Sensitivity, pitch: Double, roll: Double = 0.0) =
            PostureDetector(sensitivity).feed(0, 2_000, pitch, roll).getValue(2_000)

        assertThat(raisedWith(Sensitivity.NORMAL, pitch = 30.0)).isTrue()
        assertThat(raisedWith(Sensitivity.STRICT, pitch = 30.0)).isFalse()
        assertThat(raisedWith(Sensitivity.RELAXED, pitch = 20.0)).isTrue()
        assertThat(raisedWith(Sensitivity.NORMAL, pitch = 20.0)).isFalse()
        assertThat(raisedWith(Sensitivity.RELAXED, pitch = 45.0, roll = 40.0)).isTrue()
        assertThat(raisedWith(Sensitivity.NORMAL, pitch = 45.0, roll = 40.0)).isFalse()
        assertThat(raisedWith(Sensitivity.STRICT, pitch = 45.0, roll = 30.0)).isFalse()
    }

    @Test
    fun `reset starts over as not raised`() {
        val detector = PostureDetector()
        detector.feed(0, 2_000, pitch = 45.0)

        detector.reset()

        assertThat(detector.update(sample(2_100, 45.0))).isFalse()
        assertThat(detector.feed(2_200, 3_500, pitch = 45.0).values).doesNotContain(true)
        assertThat(detector.update(sample(3_600, 45.0))).isTrue()
    }
}
