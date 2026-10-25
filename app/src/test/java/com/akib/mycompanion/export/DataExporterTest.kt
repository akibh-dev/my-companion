package com.akib.mycompanion.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakePlaylistRepository
import com.akib.mycompanion.fakes.FakeReminderRepository
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.system.VoiceSettingsStore
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

private class FakeSettingsStore(initial: Settings = Settings()) : SettingsStore {
    override val settings = MutableStateFlow(initial)

    override suspend fun setPaused(paused: Boolean) {
        settings.value = settings.value.copy(paused = paused)
    }

    override suspend fun setEnabled(feature: Feature, enabled: Boolean) {
        val current = settings.value.enabled
        settings.value = settings.value.copy(enabled = if (enabled) current + feature else current - feature)
    }

    override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) {
        val current = settings.value.modelEngines
        settings.value = settings.value.copy(modelEngines = if (useModel) current + engine else current - engine)
    }
}

/** org.json is part of Android, so these run on Robolectric. */
@RunWith(AndroidJUnit4::class)
class DataExporterTest {

    private val settingsStore = FakeSettingsStore(Settings(enabled = setOf(Feature.CAPTURE)))
    private val voiceChange = slot<(VoiceSettings) -> VoiceSettings>()
    private val voiceStore = mockk<VoiceSettingsStore> {
        every { settings } returns flowOf(VoiceSettings())
        coEvery { update(capture(voiceChange)) } just Runs
    }
    private val replyChange = slot<(AutoReplySettings) -> AutoReplySettings>()
    private val replyStore = mockk<AutoReplySettingsStore> {
        every { settings } returns flowOf(AutoReplySettings())
        coEvery { update(capture(replyChange)) } just Runs
    }
    private val backup = SettingsBackup(settingsStore, voiceStore, replyStore)

    @Test
    fun `the zip holds one readable file per area and says the vault is left out`() = runTest {
        val playlists = FakePlaylistRepository()
        val focus = playlists.create("Focus")
        playlists.addItem(focus.id, video("abc", "Lo-fi"))
        val home = Place(id = 1, name = "Home", latitude = 23.8, longitude = 90.4)
        val context = mockk<ContextRepository> {
            every { rules } returns flowOf(emptyList())
            every { places } returns flowOf(listOf(home))
        }
        val autoReply = mockk<AutoReplyRepository> {
            every { templates } returns flowOf(
                listOf(ReplyTemplate(name = "Busy", text = "Later", context = ReplyContext.Busy)),
            )
        }
        val exporter = DataExporter(
            contacts = FakeContactRepository(listOf(Contact(id = 1, name = "Mum", phone = "+8801711123456"))),
            reminders = FakeReminderRepository(),
            playlists = playlists,
            context = context,
            autoReply = autoReply,
            settings = backup,
            clock = FakeClock(),
            io = UnconfinedTestDispatcher(testScheduler),
        )
        val output = ByteArrayOutputStream()

        val count = exporter.writeZip(output)

        val entries = unzip(output.toByteArray())
        assertThat(count).isEqualTo(entries.size)
        assertThat(entries.keys).containsExactly(
            "README.txt",
            "people.json",
            "reminders.json",
            "playlists.json",
            "rules.json",
            "places.json",
            "auto-reply-templates.json",
            "settings.json",
        ).inOrder()
        assertThat(entries.getValue("README.txt")).contains("Not included: the vault")
        assertThat(JSONObject(entries.getValue("people.json")).getInt("count")).isEqualTo(1)
        assertThat(JSONObject(entries.getValue("playlists.json")).toString()).contains("Lo-fi")
        assertThat(JSONObject(entries.getValue("places.json")).toString()).contains("Home")
        assertThat(ExportJson.parseSettings(entries.getValue("settings.json")).app.enabled)
            .containsExactly(Feature.CAPTURE)
    }

    @Test
    fun `restoring a settings backup replaces every setting`() = runTest {
        val bundle = SettingsBundle(
            app = Settings(
                paused = true,
                enabled = setOf(Feature.VOICE_MODE),
                modelEngines = setOf(AiEngine.SUMMARIZER),
            ),
            voice = VoiceSettings(privacy = Privacy.ALWAYS),
            autoReply = AutoReplySettings(label = "[Bot]"),
        )

        backup.restore(backup.parse(ExportJson.settings(bundle).toString()))

        assertThat(settingsStore.settings.value).isEqualTo(bundle.app)
        assertThat(voiceChange.captured(VoiceSettings())).isEqualTo(bundle.voice)
        assertThat(replyChange.captured(AutoReplySettings())).isEqualTo(bundle.autoReply)
    }

    @Test
    fun `the settings export is the current settings`() = runTest {
        val exported = ExportJson.parseSettings(backup.exportJson())

        assertThat(exported).isEqualTo(SettingsBundle(app = Settings(enabled = setOf(Feature.CAPTURE))))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
