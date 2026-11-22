package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.reader.scrubSpeed
import dev.akibh.mycompanion.library.reader.scrubSpeedLabel
import org.junit.Test

class ScrubSpeedTest {

    @Test
    fun slidingUpSlowsTheScrubberInSteps() {
        assertThat(scrubSpeed(0f)).isEqualTo(1f)
        assertThat(scrubSpeed(-30f)).isEqualTo(1f) // below the strip is full speed too
        assertThat(scrubSpeed(60f)).isEqualTo(.5f)
        assertThat(scrubSpeed(150f)).isEqualTo(.25f)
        assertThat(scrubSpeed(400f)).isEqualTo(.1f)
    }

    @Test
    fun onlySlowSpeedsAreAnnounced() {
        assertThat(scrubSpeedLabel(1f)).isNull()
        assertThat(scrubSpeedLabel(.25f)).isEqualTo("Quarter speed")
        assertThat(scrubSpeedLabel(.1f)).isEqualTo("Fine")
    }
}
