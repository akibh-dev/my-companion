package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.reader.bell
import dev.akibh.mycompanion.library.reader.bellLift
import dev.akibh.mycompanion.library.reader.fineAcceleration
import org.junit.Test

class ScrubSpeedTest {

    @Test
    fun theCurveRisesOnlyOnceTheFingerIsPushedUp() {
        assertThat(bellLift(0f)).isEqualTo(0f)
        assertThat(bellLift(-40f)).isEqualTo(0f) // below the strip stays flat
        assertThat(bellLift(48f)).isWithin(.01f).of(.5f)
        assertThat(bellLift(300f)).isEqualTo(1f)
    }

    @Test
    fun theBellPeaksUnderTheFingerAndFadesToTheSides() {
        assertThat(bell(0f, 26f)).isEqualTo(1f)
        assertThat(bell(26f, 26f)).isLessThan(.7f)
        assertThat(bell(-26f, 26f)).isEqualTo(bell(26f, 26f))
        assertThat(bell(120f, 26f)).isLessThan(.01f)
    }

    @Test
    fun slowMovesArePageByPageAndFlicksAccelerate() {
        assertThat(fineAcceleration(.05f)).isEqualTo(1f)
        assertThat(fineAcceleration(-.05f)).isEqualTo(1f)
        assertThat(fineAcceleration(.6f)).isGreaterThan(2f)
        assertThat(fineAcceleration(10f)).isEqualTo(8f)
    }
}
