package com.akib.mycompanion.capture

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.Summarizer
import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.capture.data.CaptureDatabase
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.domain.CaptureFilter
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.capture.system.RecorderState
import com.akib.mycompanion.capture.system.RecordingStatus
import com.akib.mycompanion.capture.system.ScreenshotBridge
import com.akib.mycompanion.capture.ui.CaptureDetailViewModel
import com.akib.mycompanion.capture.ui.CapturePanelViewModel
import com.akib.mycompanion.capture.ui.CaptureSpaceViewModel
import com.akib.mycompanion.capture.ui.DetailAction
import com.akib.mycompanion.capture.ui.DetailState
import com.akib.mycompanion.capture.ui.PanelEvent
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.sinks.VaultSink
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.time.ZoneId
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CaptureViewModelsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CaptureDatabase::class.java).allowMainThreadQueries().build()
    private val dir = Files.createTempDirectory("captures").toFile()
    private val files = CaptureFiles(File(dir, "captures"))
    private val clock = object : Clock {
        var now = 1_761_380_000_000L // 25 Oct 2025, around 14:13 in Dhaka
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val repository = CaptureRepository(
        db.dao(),
        FieldCipher { SecretKeySpec(ByteArray(32), "AES") },
        files,
        clock,
        AppLog(File(dir, "logs"), mirrorToLogcat = false),
    )
    private val recording = RecordingStatus()
    private val screenshots = ScreenshotBridge()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        dir.deleteRecursively()
    }

    private fun panel(saved: SavedStateHandle = SavedStateHandle()) =
        CapturePanelViewModel(saved, repository, files, clock, recording, screenshots)

    // ---- Panel ----

    @Test
    fun `a typed note is saved trimmed and the panel closes`() = runTest {
        val viewModel = panel()

        viewModel.events.test {
            viewModel.saveNote("  Gate code 4321  ")
            assertThat(awaitItem()).isEqualTo(PanelEvent.Close("Note saved"))
        }
        val saved = repository.captures.first().single()
        assertThat(saved.type).isEqualTo(CaptureType.TEXT)
        assertThat(saved.text).isEqualTo("Gate code 4321")
    }

    @Test
    fun `a blank note is ignored`() = runTest {
        val viewModel = panel()

        viewModel.events.test {
            viewModel.saveNote("   ")
            expectNoEvents()
        }
        assertThat(repository.captures.first()).isEmpty()
    }

    @Test
    fun `the clipboard is saved when it has text, otherwise the panel says so`() = runTest {
        val viewModel = panel()

        viewModel.events.test {
            viewModel.saveClipboard(null)
            assertThat(awaitItem()).isEqualTo(PanelEvent.Message("There's no text on the clipboard"))

            viewModel.saveClipboard("https://example.com/order/77")
            assertThat(awaitItem()).isEqualTo(PanelEvent.Close("Clipboard saved"))
        }
        val saved = repository.captures.first().single()
        assertThat(saved.type).isEqualTo(CaptureType.CLIPBOARD)
        assertThat(saved.text).isEqualTo("https://example.com/order/77")
    }

    @Test
    fun `a photo is kept only when the camera wrote one`() = runTest {
        val saved = SavedStateHandle()
        val viewModel = panel(saved)

        val cancelled = viewModel.newPhotoFile()
        cancelled.writeBytes(byteArrayOf(1))
        viewModel.onPhotoTaken(taken = false)
        assertThat(cancelled.exists()).isFalse()

        val empty = viewModel.newPhotoFile()
        empty.createNewFile()
        viewModel.onPhotoTaken(taken = true)
        assertThat(empty.exists()).isFalse()

        viewModel.events.test {
            val photo = viewModel.newPhotoFile()
            photo.writeBytes(ByteArray(100) { 7 })
            viewModel.onPhotoTaken(taken = true)
            assertThat(awaitItem()).isEqualTo(PanelEvent.Close("Photo saved"))
            assertThat(photo.exists()).isTrue()
        }
        val capture = repository.captures.first().single()
        assertThat(capture.type).isEqualTo(CaptureType.PHOTO)
        assertThat(repository.fileOf(capture)!!.length()).isEqualTo(100)
        assertThat(saved.get<String>("pending_photo")).isNull()
    }

    @Test
    fun `a photo result without a pending photo is ignored`() = runTest {
        panel().onPhotoTaken(taken = true)

        assertThat(repository.captures.first()).isEmpty()
    }

    @Test
    fun `a pending photo survives the panel being recreated`() = runTest {
        val saved = SavedStateHandle()
        val photo = panel(saved).newPhotoFile()
        photo.writeBytes(byteArrayOf(1, 2, 3))

        val recreated = panel(saved)
        recreated.events.test {
            recreated.onPhotoTaken(taken = true)
            assertThat(awaitItem()).isEqualTo(PanelEvent.Close("Photo saved"))
        }
    }

    @Test
    fun `screenshots need the accessibility service`() = runTest {
        val viewModel = panel()
        var requested = 0

        viewModel.events.test {
            assertThat(viewModel.requestScreenshot()).isFalse()
            assertThat(awaitItem().message).contains("accessibility service")

            screenshots.attach { requested++ }
            assertThat(viewModel.screenshotAvailable.value).isTrue()
            assertThat(viewModel.requestScreenshot()).isTrue()
            expectNoEvents()
        }
        assertThat(requested).isEqualTo(1)
    }

    @Test
    fun `the recorder's result is shown once and then cleared`() = runTest {
        recording.set(RecorderState.Saved(1))
        val viewModel = panel()
        assertThat(recording.state.value).isEqualTo(RecorderState.Idle)

        viewModel.events.test {
            recording.set(RecorderState.Recording(startedAt = 1))
            expectNoEvents()

            recording.set(RecorderState.Saved(id = 9))
            assertThat(awaitItem()).isEqualTo(PanelEvent.Close("Voice note saved"))
            assertThat(recording.state.value).isEqualTo(RecorderState.Idle)

            recording.set(RecorderState.Failed("Microphone is busy"))
            assertThat(awaitItem()).isEqualTo(PanelEvent.Message("Microphone is busy"))
            assertThat(recording.state.value).isEqualTo(RecorderState.Idle)
        }
    }

    @Test
    fun `messages from the panel pass through`() = runTest {
        val viewModel = panel()

        viewModel.events.test {
            viewModel.onMessage("Allow the microphone first")
            assertThat(awaitItem()).isEqualTo(PanelEvent.Message("Allow the microphone first"))
        }
    }

    // ---- Capture space ----

    private val settings = MutableStateFlow(Settings(enabled = setOf(Feature.CAPTURE)))
    private val settingsStore = object : SettingsStore {
        override val settings = this@CaptureViewModelsTest.settings
        override suspend fun setPaused(paused: Boolean) = Unit
        override suspend fun setEnabled(feature: Feature, enabled: Boolean) {
            val current = settings.value.enabled
            settings.value = settings.value.copy(enabled = if (enabled) current + feature else current - feature)
        }
        override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) = Unit
    }

    @Test
    fun `the capture space groups by day, filters, and shows the trigger switch`() = runTest {
        repository.addText(CaptureType.TEXT, "milk and eggs")
        clock.now += 24 * 60 * 60 * 1000L
        repository.addText(CaptureType.CLIPBOARD, "tracking 123")
        repository.addText(CaptureType.TEXT, "call the plumber")
        val viewModel = CaptureSpaceViewModel(repository, settingsStore, clock)

        viewModel.state.test {
            var state = awaitItem()
            while (!state.loaded || state.total < 3) state = awaitItem()
            assertThat(state.groups.map { it.captures.size }).containsExactly(2, 1).inOrder()
            assertThat(state.groups.first().day).isEqualTo(state.today)
            assertThat(state.triggerOn).isTrue()
            assertThat(state.paused).isFalse()

            viewModel.onFilter { it.copy(type = CaptureType.CLIPBOARD) }
            state = awaitItem()
            assertThat(state.groups.flatMap { it.captures }.map { it.text }).containsExactly("tracking 123")
            assertThat(state.total).isEqualTo(3)
            assertThat(state.filter).isEqualTo(CaptureFilter(type = CaptureType.CLIPBOARD))

            viewModel.setTrigger(false)
            assertThat(awaitItem().triggerOn).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Detail ----

    private class FakeSummarizer(private val answer: AiResult<Summary>) : Summarizer {
        val asked = CompletableDeferred<String>()
        override suspend fun summarize(text: String): AiResult<Summary> {
            asked.complete(text)
            return answer
        }
    }

    private class FakeVault : VaultSink {
        val notes = mutableListOf<Pair<String, String>>()
        override suspend fun saveNote(title: String, text: String) {
            notes += title to text
        }
        override suspend fun saveLink(url: String, title: String) = Unit
        override suspend fun saveFile(title: String, mime: String, fileName: String, content: InputStream) = Unit
    }

    private val summary = Summary("Plumber", "Call him on Sunday.", listOf("call the plumber"))

    private fun detail(id: Long, summarizer: Summarizer, vault: VaultSink = FakeVault()) =
        CaptureDetailViewModel(SavedStateHandle(mapOf("id" to id.toString())), repository, summarizer, vault)

    @Test
    fun `a ready summary is offered once and kept on request`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "Leak under the sink. Call the plumber on Sunday.")
        val summarizer = FakeSummarizer(AiResult.Ready(summary))
        val viewModel = detail(id, summarizer)

        assertThat(summarizer.asked.await()).isEqualTo("Leak under the sink. Call the plumber on Sunday.")
        assertThat(viewModel.suggestion.first { it != null }).isEqualTo(summary)

        viewModel.keepSummary()
        assertThat(viewModel.suggestion.value).isNull()
        val kept = repository.capture(id).first { it?.summary?.isNotEmpty() == true }!!
        assertThat(kept.title).isEqualTo("Plumber")
        assertThat(kept.actions).containsExactly("call the plumber")
    }

    @Test
    fun `summaries that aren't installed keep the button hidden`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "some words")
        val summarizer = FakeSummarizer(AiResult.NotReady("Summaries aren't installed yet"))
        val viewModel = detail(id, summarizer)

        summarizer.asked.await()
        viewModel.keepSummary()

        assertThat(viewModel.suggestion.value).isNull()
        assertThat(repository.get(id)!!.summary).isEmpty()
    }

    @Test
    fun `the detail shows the capture, or that it's gone`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "hello")
        val summarizer = FakeSummarizer(AiResult.NotReady("no"))

        val shown = detail(id, summarizer).state.first { it != DetailState.Loading }
        val missing = detail(999, summarizer).state.first { it != DetailState.Loading }

        assertThat((shown as DetailState.Loaded).capture.text).isEqualTo("hello")
        assertThat(missing).isEqualTo(DetailState.Missing)
    }

    @Test
    fun `deleting closes the screen`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "bye")
        val viewModel = detail(id, FakeSummarizer(AiResult.NotReady("no")))

        viewModel.delete(repository.get(id)!!)

        assertThat(viewModel.action.first { it != DetailAction.Idle }).isEqualTo(DetailAction.Finished)
        assertThat(repository.get(id)).isNull()
    }

    @Test
    fun `a note moves to the vault and the screen closes`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "Wi-Fi password: tea4two")
        val vault = FakeVault()
        val viewModel = detail(id, FakeSummarizer(AiResult.NotReady("no")), vault)

        viewModel.moveToVault(repository.get(id)!!)

        assertThat(viewModel.action.first { it == DetailAction.Finished }).isEqualTo(DetailAction.Finished)
        assertThat(vault.notes).containsExactly("Wi-Fi password: tea4two" to "Wi-Fi password: tea4two")
        assertThat(repository.get(id)!!.movedToVault).isTrue()
    }

    @Test
    fun `a voice note whose file is gone can't move and says why`() = runTest {
        val file = files.newFile(CaptureType.VOICE, clock.now)
        file.writeBytes(byteArrayOf(1))
        val id = repository.addFile(CaptureType.VOICE, file)
        file.delete()
        val viewModel = detail(id, FakeSummarizer(AiResult.NotReady("no")))

        viewModel.moveToVault(repository.get(id)!!)

        assertThat(viewModel.action.first { it is DetailAction.Failed })
            .isEqualTo(DetailAction.Failed("The capture's file is missing"))
        assertThat(repository.get(id)!!.movedToVault).isFalse()
    }
}
