package com.akib.mycompanion.context

import com.akib.mycompanion.context.device.ForegroundEvents
import com.akib.mycompanion.context.domain.ContextSnapshot
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.RuleCodec
import com.akib.mycompanion.context.domain.RuleEvaluator
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.context.domain.watchForegroundApp
import com.akib.mycompanion.context.ui.Describe
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Test

/** The "App in use" trigger (FR-3.7): matching, storage, wording and reading the usage log. */
class ForegroundAppTriggerTest {

    private val maps = Trigger.AppInForeground("com.google.android.apps.maps", "Maps")
    private val now = ContextSnapshot(now = LocalDateTime.of(2026, 10, 25, 10, 0))

    @Test
    fun `matches only while that app is in front`() {
        assertThat(RuleEvaluator.matches(maps, now.copy(foregroundApp = "com.google.android.apps.maps"))).isTrue()
        assertThat(RuleEvaluator.matches(maps, now.copy(foregroundApp = "com.android.chrome"))).isFalse()
        assertThat(RuleEvaluator.matches(maps, now)).isFalse()
    }

    @Test
    fun `package names are compared exactly`() {
        assertThat(RuleEvaluator.matches(maps, now.copy(foregroundApp = "com.google.android.apps.Maps"))).isFalse()
        assertThat(RuleEvaluator.matches(maps, now.copy(foregroundApp = "com.google.android.apps.maps.lite"))).isFalse()
    }

    @Test
    fun `combined with other triggers all must match`() {
        val rule = Rule(
            name = "Driving",
            triggers = listOf(maps, Trigger.Charging(true)),
            actions = listOf(RuleAction.Sound(SoundMode.VIBRATE)),
        )
        val inMaps = now.copy(foregroundApp = maps.packageName)

        assertThat(RuleEvaluator.applies(rule, inMaps)).isFalse()
        assertThat(RuleEvaluator.applies(rule, inMaps.copy(charging = true))).isTrue()
    }

    @Test
    fun `stored and read back with its label`() {
        val odd = Trigger.AppInForeground("org.example.app", "Notes | Work\nApp")
        val text = RuleCodec.encodeTriggers(listOf(maps, odd))

        assertThat(RuleCodec.decodeTriggers(text)).containsExactly(maps, odd).inOrder()
    }

    @Test
    fun `a stored line without a label falls back to the package name`() {
        assertThat(RuleCodec.decodeTriggers("APP|org.example.app"))
            .containsExactly(Trigger.AppInForeground("org.example.app", "org.example.app"))
        assertThat(RuleCodec.decodeTriggers("APP|org.example.app|"))
            .containsExactly(Trigger.AppInForeground("org.example.app", "org.example.app"))
    }

    @Test
    fun `reads as plain words`() {
        assertThat(Describe.trigger(maps, emptyMap())).isEqualTo("Using Maps")
    }

    @Test
    fun `the app watcher is needed only by enabled rules that use the trigger`() {
        val withApp = Rule(name = "Maps", triggers = listOf(maps), actions = listOf(RuleAction.Brightness(80)))
        val withoutApp = Rule(name = "Charging", triggers = listOf(Trigger.Charging()), actions = emptyList())

        assertThat(listOf(withoutApp).watchForegroundApp()).isFalse()
        assertThat(listOf(withoutApp, withApp).watchForegroundApp()).isTrue()
        assertThat(listOf(withApp.copy(enabled = false)).watchForegroundApp()).isFalse()
        assertThat(emptyList<Rule>().watchForegroundApp()).isFalse()
    }

    // ---- Reading the usage log ----

    private fun resumed(time: Long, app: String) = ForegroundEvents.Event(time, ForegroundEvents.RESUMED, app)

    @Test
    fun `the newest app to come to the front wins`() {
        val events = listOf(resumed(100, "a.app"), resumed(300, "c.app"), resumed(200, "b.app"))

        assertThat(ForegroundEvents.latest(events, previous = null)).isEqualTo("c.app")
    }

    @Test
    fun `other kinds of events are ignored`() {
        val paused = ForegroundEvents.Event(500, PAUSED, "b.app")
        val screenOff = ForegroundEvents.Event(600, SCREEN_NON_INTERACTIVE, "android")

        assertThat(ForegroundEvents.latest(listOf(resumed(100, "a.app"), paused, screenOff), null)).isEqualTo("a.app")
    }

    @Test
    fun `with nothing new the previous app is still in front`() {
        assertThat(ForegroundEvents.latest(emptyList(), previous = "a.app")).isEqualTo("a.app")
        assertThat(ForegroundEvents.latest(listOf(ForegroundEvents.Event(1, PAUSED, "a.app")), "a.app"))
            .isEqualTo("a.app")
        assertThat(ForegroundEvents.latest(emptyList(), previous = null)).isNull()
    }

    @Test
    fun `entries without a package name are skipped`() {
        assertThat(ForegroundEvents.latest(listOf(resumed(100, "a.app"), resumed(200, "")), null)).isEqualTo("a.app")
    }

    private companion object {
        // Values of UsageEvents.Event.ACTIVITY_PAUSED and SCREEN_NON_INTERACTIVE.
        const val PAUSED = 2
        const val SCREEN_NON_INTERACTIVE = 16
    }
}
