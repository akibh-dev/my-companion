package com.akib.mycompanion.capture

import com.akib.mycompanion.capture.ui.triggerStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The volume-key switch never claims to work while the system service it needs is off (FR-4.1). */
class TriggerStatusTest {

    @Test
    fun `off says so and points to the other ways in`() {
        assertThat(triggerStatus(triggerOn = false, paused = false, serviceOn = true))
            .isEqualTo("Off. You can still use the tile or the button below.")
        assertThat(triggerStatus(triggerOn = false, paused = true, serviceOn = false))
            .isEqualTo("Off. You can still use the tile or the button below.")
    }

    @Test
    fun `on without the accessibility service warns instead of promising`() {
        assertThat(triggerStatus(triggerOn = true, paused = false, serviceOn = false))
            .contains("accessibility service is off")
        // The missing service is the thing to fix first, even while paused.
        assertThat(triggerStatus(triggerOn = true, paused = true, serviceOn = false))
            .contains("accessibility service is off")
    }

    @Test
    fun `on and paused`() {
        assertThat(triggerStatus(triggerOn = true, paused = true, serviceOn = true))
            .isEqualTo("On, but everything is paused right now.")
    }

    @Test
    fun `on and ready`() {
        assertThat(triggerStatus(triggerOn = true, paused = false, serviceOn = true))
            .isEqualTo("Hold both for a second to open the capture panel.")
    }
}
