package com.akib.mycompanion.context.screenon

import com.akib.mycompanion.context.screenon.Samples.sample
import com.akib.mycompanion.context.screenon.Samples.times
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScreenOnComponentTest {

    private val screenFlow = MutableStateFlow(true)
    private val motionFlow = MutableSharedFlow<MotionSample>(extraBufferCapacity = 256)
    private val outsideFlow = MutableStateFlow(true)
    private val sensitivityFlow = MutableStateFlow(Sensitivity.NORMAL)
    private val keepAwake = object : KeepAwake {
        var allowed = true
        var holding = false
        override fun canHold() = allowed
        override fun hold(): Boolean {
            holding = allowed
            return holding
        }
        override fun release() {
            holding = false
        }
    }
    private val status = ScreenOnStatus()
    private val component = ScreenOnComponent(
        object : ScreenOnSensors {
            override fun screenOn(): Flow<Boolean> = screenFlow
            override fun motion(): Flow<MotionSample> = motionFlow
        },
        object : OutsideDetector {
            override fun outside(): Flow<Boolean> = outsideFlow
        },
        keepAwake,
        object : ScreenOnPreferences {
            override val sensitivity: Flow<Sensitivity> = sensitivityFlow
            override suspend fun setSensitivity(sensitivity: Sensitivity) {
                sensitivityFlow.value = sensitivity
            }
        },
        status,
        AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false),
    )

    /** Holds the phone up (45°, in a hand) from [from] to [to] ms. */
    private suspend fun TestScope.holdUp(from: Long, to: Long) {
        times(from, to).forEach { motionFlow.emit(sample(it, pitch = 45.0)) }
        runCurrent()
    }

    private fun TestScope.started(): CoroutineScope {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        component.start(scope)
        runCurrent()
        return scope
    }

    @Test
    fun `wanted only while the feature is on and nothing is paused`() {
        val on = Settings(enabled = setOf(Feature.KEEP_SCREEN_ON))

        assertThat(component.isWanted(on)).isTrue()
        assertThat(component.isWanted(on.copy(paused = true))).isFalse()
        assertThat(component.isWanted(Settings(enabled = setOf(Feature.CONTEXT_RULES)))).isFalse()
    }

    @Test
    fun `keeps the screen on outside while raised and lets go when the screen turns off`() = runTest {
        val scope = started()
        assertThat(motionFlow.subscriptionCount.value).isEqualTo(1)

        holdUp(0, 2_000)
        assertThat(keepAwake.holding).isTrue()
        assertThat(status.state.value.summary()).isEqualTo("Outside · raised · keeping the screen on")

        screenFlow.value = false
        runCurrent()
        assertThat(keepAwake.holding).isFalse()
        assertThat(motionFlow.subscriptionCount.value).isEqualTo(0)
        assertThat(status.state.value.summary()).isEqualTo("Screen off · sensors off")
        scope.cancel()
    }

    @Test
    fun `not before the phone has been held up for a moment`() = runTest {
        val scope = started()

        holdUp(0, 1_000)

        assertThat(keepAwake.holding).isFalse()
        assertThat(status.state.value.summary()).isEqualTo("Outside · not raised · normal screen timeout")
        scope.cancel()
    }

    @Test
    fun `never at a saved place, and starts once you're outside`() = runTest {
        outsideFlow.value = false
        val scope = started()

        holdUp(0, 2_000)
        assertThat(keepAwake.holding).isFalse()
        assertThat(status.state.value.summary()).isEqualTo("At a saved place · raised · normal screen timeout")

        outsideFlow.value = true
        runCurrent()
        assertThat(keepAwake.holding).isTrue()
        scope.cancel()
    }

    @Test
    fun `without the overlay permission nothing happens and the status says why`() = runTest {
        keepAwake.allowed = false
        val scope = started()

        holdUp(0, 2_000)

        assertThat(keepAwake.holding).isFalse()
        assertThat(status.state.value.overlayAllowed).isFalse()
        assertThat(status.state.value.raised).isTrue()
        scope.cancel()
    }

    @Test
    fun `stopping the feature releases the screen and clears the status`() = runTest {
        val scope = started()
        holdUp(0, 2_000)
        assertThat(keepAwake.holding).isTrue()

        scope.cancel()
        runCurrent()

        assertThat(keepAwake.holding).isFalse()
        assertThat(status.state.value).isEqualTo(ScreenOnState())
    }

    @Test
    fun `changing the sensitivity applies right away`() = runTest {
        val scope = started()
        times(0, 2_000).forEach { motionFlow.emit(sample(it, pitch = 30.0)) }
        runCurrent()
        assertThat(keepAwake.holding).isTrue()

        sensitivityFlow.value = Sensitivity.STRICT
        runCurrent()
        times(2_100, 6_000).forEach { motionFlow.emit(sample(it, pitch = 30.0)) }
        runCurrent()

        assertThat(keepAwake.holding).isFalse()
        scope.cancel()
    }
}
