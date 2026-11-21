package dev.akibh.mycompanion.export

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.autoreply.data.AutoReplyRepository
import dev.akibh.mycompanion.autoreply.data.AutoReplySettingsStore
import dev.akibh.mycompanion.autoreply.domain.AutoReplySettings
import dev.akibh.mycompanion.context.data.ContextRepository
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.settings.AiEngine
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.fakes.FakeClock
import dev.akibh.mycompanion.fakes.FakeContactRepository
import dev.akibh.mycompanion.fakes.FakePlaylistRepository
import dev.akibh.mycompanion.fakes.FakeReminderRepository
import dev.akibh.mycompanion.fakes.MainDispatcherRule
import dev.akibh.mycompanion.quran.data.BackupBookmark
import dev.akibh.mycompanion.quran.data.BookmarkRepository
import dev.akibh.mycompanion.quran.data.QuranBackup
import dev.akibh.mycompanion.quran.domain.AyahRef
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.VoiceSettings
import dev.akibh.mycompanion.voice.system.VoiceSettingsStore
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
        override val settings = MutableStateFlow(Settings(enabled = setOf(Feature.CONTEXT_RULES)))
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
    private val restoredQuran = slot<QuranBackup>()
    private val quranBookmarks = mockk<BookmarkRepository> {
        coEvery { backup() } returns QuranBackup(listOf(BackupBookmark(AyahRef(2, 255), "Ayat al-Kursi", 1_000)))
        coEvery { restore(capture(restoredQuran)) } just Runs
    }
    private val backup = SettingsBackup(settingsStore, voiceStore, replyStore, quranBookmarks)
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
    fun `export my data writes the zip and reports the file count`() {
        val (uri, stream) = output("data.zip")

        viewModel.onExportAll(uri)

        val state = viewModel.state.value
        assertThat(state.busy).isFalse()
        assertThat(state.status).isEqualTo("Saved 8 files.")
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
        assertThat(bundle.app.enabled).containsExactly(Feature.CONTEXT_RULES)
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
        assertThat(settingsStore.settings.value.enabled).containsExactly(Feature.CONTEXT_RULES)

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
