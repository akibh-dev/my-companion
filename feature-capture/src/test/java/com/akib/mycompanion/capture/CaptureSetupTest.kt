package com.akib.mycompanion.capture

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.SpeechRecognizer
import com.akib.mycompanion.capture.data.CaptureDatabase
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.data.CaptureProcessor
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.di.CaptureModule
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.util.Locale
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class CaptureSetupTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(app, CaptureDatabase::class.java).allowMainThreadQueries().build()
    private val dir = Files.createTempDirectory("captures").toFile()
    private val files = CaptureFiles(File(dir, "captures"))
    private val log = AppLog(File(dir, "logs"), mirrorToLogcat = false)
    private val clock = object : Clock {
        override fun nowMillis() = 1_000L
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val cipher = FieldCipher { SecretKeySpec(ByteArray(32), "AES") }
    private val repository = CaptureRepository(db.dao(), cipher, files, clock, log)

    private class Recognizer(private val answer: AiResult<String>) : SpeechRecognizer {
        var calls = 0
        override suspend fun transcribe(audio: File, language: Locale): AiResult<String> {
            calls++
            return answer
        }
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun voiceNote(): Long {
        val file = files.newFile(CaptureType.VOICE, clock.nowMillis()).apply { writeBytes(byteArrayOf(1, 2)) }
        return repository.addFile(CaptureType.VOICE, file)
    }

    // ---- Transcripts ----

    @Test
    fun `a blank transcript isn't stored`() = runTest {
        val id = voiceNote()

        assertThat(CaptureProcessor(repository, Recognizer(AiResult.Ready("   ")), log).transcribe(id)).isFalse()
        assertThat(repository.get(id)!!.transcript).isEmpty()
    }

    @Test
    fun `a transcript is stored trimmed`() = runTest {
        val id = voiceNote()

        assertThat(CaptureProcessor(repository, Recognizer(AiResult.Ready("  hello  ")), log).transcribe(id)).isTrue()
        assertThat(repository.get(id)!!.transcript).isEqualTo("hello")
    }

    @Test
    fun `nothing to transcribe for a missing capture, a note, or a deleted recording`() = runTest {
        val recognizer = Recognizer(AiResult.Ready("text"))
        val processor = CaptureProcessor(repository, recognizer, log)
        val note = repository.addText(CaptureType.TEXT, "typed")
        val gone = voiceNote()
        files.file(repository.get(gone)!!.file!!).delete()

        assertThat(processor.transcribe(12_345)).isFalse()
        assertThat(processor.transcribe(note)).isFalse()
        assertThat(processor.transcribe(gone)).isFalse()
        assertThat(recognizer.calls).isEqualTo(0)
    }

    @Test
    fun `transcribing later happens in the background`() = runTest {
        val id = voiceNote()

        CaptureProcessor(repository, Recognizer(AiResult.Ready("later")), log).transcribeLater(id)

        assertThat(repository.capture(id).first { it?.transcript?.isNotEmpty() == true }!!.transcript)
            .isEqualTo("later")
    }

    // ---- What quick capture needs ----

    @Test
    fun `quick capture lists accessibility, microphone and a camera app`() {
        val requirements = listOf(CaptureModule.accessibility(), CaptureModule.microphone(), CaptureModule.camera())

        assertThat(requirements.map { it.id })
            .containsExactly("capture_accessibility", "capture_microphone", "capture_camera_app")
        requirements.forEach { assertThat(it.feature).isEqualTo(Feature.CAPTURE) }
        assertThat(CaptureModule.accessibility().settingsIntent(app).action)
            .isEqualTo(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        assertThat(CaptureModule.microphone().runtimePermissions).containsExactly(Manifest.permission.RECORD_AUDIO)
        assertThat(CaptureModule.camera().runtimePermissions).isEmpty()
    }

    @Test
    fun `nothing is granted on a fresh phone, the microphone once allowed`() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)

        assertThat(CaptureModule.accessibility().isGranted(app)).isFalse()
        assertThat(CaptureModule.camera().isGranted(app)).isFalse()
        assertThat(CaptureModule.microphone().isGranted(app)).isFalse()

        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        assertThat(CaptureModule.microphone().isGranted(app)).isTrue()
    }

    @Test
    fun `capture files live in their own private folder`() {
        assertThat(CaptureModule.files(app).dir).isEqualTo(File(app.filesDir, "captures"))
    }
}
