package com.akib.mycompanion.context.screenon

import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenOnStateTest {

    private val watching = ScreenOnState(running = true, screenOn = true)

    @Test
    fun `summary says what it sees`() {
        assertThat(ScreenOnState().summary()).isEqualTo("Not running")
        assertThat(watching.copy(screenOn = false).summary()).isEqualTo("Screen off · sensors off")
        assertThat(watching.copy(overlayAllowed = false).summary())
            .isEqualTo("Needs \"Display over other apps\" to keep the screen on")
        assertThat(watching.copy(outside = true, raised = true, keepingOn = true).summary())
            .isEqualTo("Outside · raised · keeping the screen on")
        assertThat(watching.copy(outside = false, raised = true).summary())
            .isEqualTo("At a saved place · raised · normal screen timeout")
        assertThat(watching.copy(outside = true).summary()).isEqualTo("Outside · not raised · normal screen timeout")
    }

    @Test
    fun `status line shows off and paused before the live status`() {
        val on = Settings(enabled = setOf(Feature.KEEP_SCREEN_ON))

        assertThat(ScreenOnUiState(settings = Settings()).statusLine()).isEqualTo("Off")
        assertThat(ScreenOnUiState(settings = on.copy(paused = true)).statusLine()).isEqualTo("Paused from Home")
        assertThat(ScreenOnUiState(settings = on, status = watching.copy(outside = true)).statusLine())
            .isEqualTo("Outside · not raised · normal screen timeout")
    }

    @Test
    fun `the screen is kept on only when on, outside and raised`() {
        assertThat(shouldKeepScreenOn(screenOn = true, outside = true, raised = true)).isTrue()
        assertThat(shouldKeepScreenOn(screenOn = false, outside = true, raised = true)).isFalse()
        assertThat(shouldKeepScreenOn(screenOn = true, outside = false, raised = true)).isFalse()
        assertThat(shouldKeepScreenOn(screenOn = true, outside = true, raised = false)).isFalse()
    }
}
