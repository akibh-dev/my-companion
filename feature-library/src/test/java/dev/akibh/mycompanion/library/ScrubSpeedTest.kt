package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.reader.fineAcceleration
import dev.akibh.mycompanion.library.reader.pushedUp
import dev.akibh.mycompanion.library.reader.tickOffsetDegrees
import org.junit.Test

class ScrubSpeedTest {

    @Test
    fun theDialOpensOnlyOnceTheFingerIsPushedUp() {
        assertThat(pushedUp(0f)).isFalse()
        assertThat(pushedUp(-60f)).isFalse() // dragging down never opens it
        assertThat(pushedUp(30f)).isTrue()
    }

    @Test
    fun thePageUnderTheMarkerIsAtTheTopAndNeighboursSpreadEvenly() {
        assertThat(tickOffsetDegrees(10, 10f)).isEqualTo(0f)
        assertThat(tickOffsetDegrees(11, 10f)).isEqualTo(-tickOffsetDegrees(9, 10f))
        assertThat(tickOffsetDegrees(11, 10f)).isGreaterThan(0f)
        // Halfway between two pages, both sit either side of the marker.
        assertThat(tickOffsetDegrees(10, 10.5f)).isLessThan(0f)
    }

    @Test
    fun slowTurnsArePageByPageAndFlicksAccelerate() {
        assertThat(fineAcceleration(.05f)).isEqualTo(1f)
        assertThat(fineAcceleration(-.05f)).isEqualTo(1f)
        assertThat(fineAcceleration(.6f)).isGreaterThan(2f)
        assertThat(fineAcceleration(10f)).isEqualTo(8f)
    }
}
