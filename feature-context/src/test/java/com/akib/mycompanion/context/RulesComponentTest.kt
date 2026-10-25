package com.akib.mycompanion.context

import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.ContextSensors
import com.akib.mycompanion.context.device.DeviceSettings
import com.akib.mycompanion.context.device.PlaceDetector
import com.akib.mycompanion.context.device.RulesComponent
import com.akib.mycompanion.context.device.RulesEngine
import com.akib.mycompanion.context.device.TakenOverStorage
import com.akib.mycompanion.context.device.WriteResult
import com.akib.mycompanion.context.domain.ContextSnapshot
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleAction
import com.akib.mycompanion.context.domain.SoundMode
import com.akib.mycompanion.context.domain.Trigger
import com.akib.mycompanion.core.context.ContextStatus
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.rules.TakenOver
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RulesComponentTest {

    private val device = object : DeviceSettings {
        val values = mutableMapOf("ringer" to "NORMAL")
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String): WriteResult {
            values[key] = value
            return WriteResult.Applied
        }
    }
    private val storage = object : TakenOverStorage {
        var saved = TakenOver()
        override suspend fun load() = saved
        override suspend fun save(takenOver: TakenOver) {
            saved = takenOver
        }
    }
    private val status = ContextStatus()
    private val engine = RulesEngine(
        device,
        storage,
        { _, _, _ -> },
        AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false),
        object : Clock {
            override fun nowMillis() = 0L
            override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
        },
        status,
    )

    private val charging = Rule(
        id = 1,
        name = "Charging",
        triggers = listOf(Trigger.Charging()),
        actions = listOf(RuleAction.Sound(SoundMode.VIBRATE)),
    )
    private val rules = MutableStateFlow(listOf(charging))
    private val snapshot = MutableStateFlow(ContextSnapshot(LocalDateTime.of(2026, 10, 25, 10, 0)))
    private val repository = mockk<ContextRepository> { every { rules } returns this@RulesComponentTest.rules }
    private val places = mockk<PlaceDetector>(relaxed = true)
    private val component = RulesComponent(
        repository,
        object : ContextSensors {
            override fun snapshots(): Flow<ContextSnapshot> = snapshot
        },
        places,
        engine,
    )

    @Test
    fun `wanted only while context rules are on and nothing is paused`() {
        assertThat(component.name).isEqualTo("context-rules")
        assertThat(component.isWanted(Settings())).isFalse()
        assertThat(component.isWanted(Settings(enabled = setOf(Feature.CONTEXT_RULES)))).isTrue()
        assertThat(component.isWanted(Settings(paused = true, enabled = setOf(Feature.CONTEXT_RULES)))).isFalse()
        assertThat(component.isWanted(Settings(enabled = setOf(Feature.VOICE_MODE)))).isFalse()
    }

    @Test
    fun `rules follow the phone's state while running and are undone when stopped`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        component.start(scope)
        runCurrent()
        verify { places.start(scope) }
        assertThat(device.values["ringer"]).isEqualTo("NORMAL")

        snapshot.value = snapshot.value.copy(charging = true)
        runCurrent()
        assertThat(device.values["ringer"]).isEqualTo("VIBRATE")
        assertThat(engine.active.value).containsExactly(1L)
        assertThat(status.activeRules.value).containsExactly("Charging")

        scope.cancel()
        runCurrent()
        assertThat(device.values["ringer"]).isEqualTo("NORMAL")
        assertThat(engine.active.value).isEmpty()
        assertThat(status.activeRules.value).isEmpty()
    }

    @Test
    fun `an edited rule applies straight away`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        snapshot.value = snapshot.value.copy(charging = true)
        component.start(scope)
        runCurrent()
        assertThat(device.values["ringer"]).isEqualTo("VIBRATE")

        rules.value = listOf(charging.copy(actions = listOf(RuleAction.Sound(SoundMode.SILENT))))
        runCurrent()
        assertThat(device.values["ringer"]).isEqualTo("SILENT")

        rules.value = listOf(charging.copy(enabled = false))
        runCurrent()
        assertThat(device.values["ringer"]).isEqualTo("NORMAL")
        scope.cancel()
    }
}
