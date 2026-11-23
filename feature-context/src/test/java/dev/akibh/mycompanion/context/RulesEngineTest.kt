package dev.akibh.mycompanion.context

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.context.device.DeviceSettings
import dev.akibh.mycompanion.context.device.RulesEngine
import dev.akibh.mycompanion.context.device.TakenOverStorage
import dev.akibh.mycompanion.context.device.WriteResult
import dev.akibh.mycompanion.context.domain.ContextSnapshot
import dev.akibh.mycompanion.context.domain.DndMode
import dev.akibh.mycompanion.context.domain.Rule
import dev.akibh.mycompanion.context.domain.RuleAction
import dev.akibh.mycompanion.context.domain.SoundMode
import dev.akibh.mycompanion.context.domain.Trigger
import dev.akibh.mycompanion.core.context.ContextStatus
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.rules.TakenOver
import dev.akibh.mycompanion.core.time.Clock
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RulesEngineTest {

    private val device = object : DeviceSettings {
        val values = mutableMapOf("ringer" to "NORMAL", "dnd" to "OFF", "wifi" to "false")
        var dndAllowed = true
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String): WriteResult = when {
            key == "dnd" && !dndAllowed -> WriteResult.NotAllowed("Needs Do Not Disturb access")
            key == "wifi" -> WriteResult.AskedUser
            else -> {
                values[key] = value
                WriteResult.Applied
            }
        }
    }
    private val storage = object : TakenOverStorage {
        var saved = TakenOver()
        override suspend fun load() = saved
        override suspend fun save(takenOver: TakenOver) {
            saved = takenOver
        }
    }
    private val history = mutableListOf<Pair<String, String>>()
    private val clock = object : Clock {
        override fun nowMillis() = 0L
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val status = ContextStatus()
    private val engine = RulesEngine(
        device,
        storage,
        { _, name, changes -> history += name to changes },
        AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false),
        clock,
        status,
    )

    private val charging = ContextSnapshot(LocalDateTime.of(2026, 10, 25, 10, 0), charging = true)
    private val unplugged = charging.copy(charging = false)
    private val nightstand = Rule(
        id = 1,
        name = "Nightstand",
        triggers = listOf(Trigger.Charging(true)),
        actions = listOf(RuleAction.Sound(SoundMode.SILENT), RuleAction.DoNotDisturb(DndMode.ALARMS)),
    )

    @Test
    fun `a rule applies its changes and puts them back when it ends`() = runTest {
        engine.run(listOf(nightstand), charging)
        assertThat(device.values).containsAtLeast("ringer", "SILENT", "dnd", "ALARMS")
        assertThat(engine.active.value).containsExactly(1L)
        assertThat(status.activeRules.value).containsExactly("Nightstand")

        engine.run(listOf(nightstand), unplugged)
        assertThat(device.values).containsAtLeast("ringer", "NORMAL", "dnd", "OFF")
        assertThat(history.map { it.first }).containsExactly("Nightstand", "Restored").inOrder()
    }

    @Test
    fun `pausing restores everything`() = runTest {
        engine.run(listOf(nightstand), charging)

        engine.restoreAll()

        assertThat(device.values["ringer"]).isEqualTo("NORMAL")
        assertThat(engine.active.value).isEmpty()
        assertThat(storage.saved).isEqualTo(TakenOver())
    }

    @Test
    fun `what couldn't be changed is written in the history`() = runTest {
        device.dndAllowed = false
        val wifi = nightstand.copy(actions = listOf(RuleAction.DoNotDisturb(DndMode.ALARMS), RuleAction.Wifi(true)))

        engine.run(listOf(wifi), charging)

        assertThat(
            history.single().second
        ).isEqualTo("dnd → ALARMS (failed: Needs Do Not Disturb access), wifi → true (asked you)")
    }

    @Test
    fun `a run cancelled halfway still remembers what it took over`() = runTest {
        val historyWritten = CompletableDeferred<Unit>()
        val slowHistory = RulesEngine(
            device,
            storage,
            { _, _, _ -> historyWritten.await() },
            AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false),
            clock,
            status,
        )

        // A new snapshot arrives while the first run is still writing its history (collectLatest cancels it).
        val running = launch { slowHistory.run(listOf(nightstand), charging) }
        runCurrent()
        running.cancel()
        historyWritten.complete(Unit)
        running.join()

        assertThat(device.values["ringer"]).isEqualTo("SILENT")
        assertThat(storage.saved).isNotEqualTo(TakenOver())
        slowHistory.restoreAll()
        assertThat(device.values).containsAtLeast("ringer", "NORMAL", "dnd", "OFF")
    }
}
