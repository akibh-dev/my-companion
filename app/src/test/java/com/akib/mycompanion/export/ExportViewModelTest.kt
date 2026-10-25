package com.akib.mycompanion.export

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.autoreply.data.AutoReplySettingsStore
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakePlaylistRepository
import com.akib.mycompanion.fakes.FakeReminderRepository
import com.akib.mycompanion.fakes.MainDispatcherRule
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
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ExportViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private val settingsStore = object : SettingsStore {
        override val settings = MutableStateFlow(Settings(enabled = setOf(Feature.CAPTURE)))
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
    private val voiceChange = slot<(VoiceSettings) -> VoiceSettings>()
    private val voiceStore = mockk<VoiceSettingsStore> {
        every { settings } returns flowOf(VoiceSettings())
        coEvery { update(capture(voiceChange)) } just Runs
    }
    private val replyStore = mockk<AutoReplySettingsStore> {
        every { settings } returns flowOf(AutoReplySettings())
        coEvery { update(any()) } just Runs
    }
    private val backup = SettingsBackup(settingsStore, voiceStore, replyStore)
    private val exporter = DataExporter(
        contacts = FakeContactRepository(),
        reminders = FakeReminderRepository(),
        playlists = FakePlaylistRepository(),
        context = mockk<ContextRepository> {
            every { rules } returns flowOf(emptyList())
            every { places } returns flowOf(emptyList())
        },
        autoReply = mockk<AutoReplyRepository> { every { templates } returns flowOf(emptyList()) },
        settings = backup,
        clock = FakeClock(),
        io = main.dispatcher,
    )
    private val viewModel = ExportViewModel(
        app,
        exporter,
        backup,
        AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false),
        UnconfinedTestDispatcher(main.dispatcher.scheduler),
    )

    private fun output(name: String): Pair<Uri, ByteArrayOutputStream> {
        val uri = Uri.parse("content://com.example.files/$name")
        val stream = ByteArrayOutputStream()
        shadowOf(app.contentResolver).registerOutputStream(uri, stream)
        return uri to stream
    }

    private fun input(name: String, text: String): Uri {
        val uri = Uri.parse("content://com.example.files/$name")
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(text.toByteArray()))
        return uri
    }

    @Test
    fun `export my data writes the zip and says the vault isn't in it`() {
        val (uri, stream) = output("data.zip")

        viewModel.onExportAll(uri)

        val state = viewModel.state.value
        assertThat(state.busy).isFalse()
        assertThat(state.status).isEqualTo("Saved 8 files. The vault isn't included: it has its own encrypted backup.")
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(stream.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { names += it.name }
        }
        assertThat(names).hasSize(8)
        assertThat(names).contains("settings.json")
    }

    @Test
    fun `settings are saved as a backup that can be read back`() {
        val (uri, stream) = output("settings.json")

        viewModel.onExportSettings(uri)

        assertThat(viewModel.state.value.status).isEqualTo("Settings saved")
        val bundle = backup.parse(stream.toString(Charsets.UTF_8.name()))
        assertThat(bundle.app.enabled).containsExactly(Feature.CAPTURE)
    }

    @Test
    fun `a file that can't be opened is reported, and the screen isn't left busy`() {
        viewModel.onExportSettings(Uri.parse("content://nobody.home/settings.json"))

        val state = viewModel.state.value
        assertThat(state.busy).isFalse()
        assertThat(state.status).isNotNull()
        assertThat(state.status).isNotEqualTo("Settings saved")
    }

    @Test
    fun `restoring asks first, then replaces the settings`() {
        val bundle = SettingsBundle(
            app = Settings(
                paused = true,
                enabled = setOf(Feature.VOICE_MODE),
                modelEngines = setOf(AiEngine.SUMMARIZER),
            ),
            voice = VoiceSettings(privacy = Privacy.ALWAYS),
        )
        val uri = input("backup.json", ExportJson.settings(bundle).toString())

        viewModel.onRestorePicked(uri)

        assertThat(viewModel.state.value.pendingRestore).isEqualTo(bundle)
        assertThat(viewModel.state.value.status).isNull()
        assertThat(settingsStore.settings.value.enabled).containsExactly(Feature.CAPTURE)

        viewModel.onConfirmRestore()

        val state = viewModel.state.value
        assertThat(state.pendingRestore).isNull()
        assertThat(state.status).isEqualTo("Settings restored")
        assertThat(settingsStore.settings.value).isEqualTo(bundle.app)
        assertThat(voiceChange.captured(VoiceSettings())).isEqualTo(bundle.voice)
    }

    @Test
    fun `cancelling a restore changes nothing`() {
        val pausedBackup = ExportJson.settings(SettingsBundle(app = Settings(paused = true))).toString()
        val uri = input("backup.json", pausedBackup)
        viewModel.onRestorePicked(uri)

        viewModel.onCancelRestore()
        viewModel.onConfirmRestore()

        assertThat(viewModel.state.value.pendingRestore).isNull()
        assertThat(viewModel.state.value.status).isNull()
        assertThat(settingsStore.settings.value.paused).isFalse()
    }

    @Test
    fun `a file that isn't a settings backup is refused before anything changes`() {
        val uri = input("photo.jpg", "not json at all")

        viewModel.onRestorePicked(uri)

        val state = viewModel.state.value
        assertThat(state.status).isEqualTo("That file isn't a My Companion settings backup")
        assertThat(state.pendingRestore).isNull()
        assertThat(state.busy).isFalse()
    }
}
