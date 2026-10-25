package com.akib.mycompanion.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.SpeechRecognizer
import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.capture.data.CaptureDatabase
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.data.CaptureProcessor
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.sinks.VaultSink
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.time.ZoneId
import java.util.Locale
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, CaptureDatabase::class.java).allowMainThreadQueries().build()
    private val dir = Files.createTempDirectory("captures").toFile()
    private val files = CaptureFiles(File(dir, "captures"))
    private val clock = object : Clock {
        var now = 1_000_000L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }

    /** A software key instead of the Keystore (Robolectric has no AndroidKeyStore). */
    private val cipher = FieldCipher { SecretKeySpec(ByteArray(32), "AES") }
    private val log = AppLog(File(dir, "logs"), mirrorToLogcat = false)
    private val repository = CaptureRepository(db.dao(), cipher, files, clock, log)

    private class FakeVault : VaultSink {
        val notes = mutableListOf<Pair<String, String>>()
        val files = mutableListOf<Triple<String, String, ByteArray>>()

        override suspend fun saveNote(title: String, text: String) {
            notes += title to text
        }

        override suspend fun saveLink(url: String, title: String) = Unit

        override suspend fun saveFile(title: String, mime: String, fileName: String, content: InputStream) {
            files += Triple(title, mime, content.readBytes())
        }
    }

    private class FakeRecognizer(private val answer: AiResult<String>) : SpeechRecognizer {
        override suspend fun transcribe(audio: File, language: Locale) = answer
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `notes are stored encrypted and read back`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "Gate code 4321")

        val row = db.dao().get(id)!!
        assertThat(row.textEnc).isNotNull()
        assertThat(row.textEnc).doesNotContain("4321")
        assertThat(row.type).isEqualTo("TEXT")
        assertThat(repository.get(id)!!.text).isEqualTo("Gate code 4321")
        assertThat(repository.captures.first().single().created).isEqualTo(1_000_000L)
    }

    @Test
    fun `edits, transcripts and summaries are encrypted too`() = runTest {
        val file = files.newFile(CaptureType.VOICE, clock.now).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val id = repository.addFile(CaptureType.VOICE, file)
        repository.setTranscript(id, "  pick up the parcel  ")
        repository.applySummary(id, Summary("Parcel", "Collect the parcel today", listOf("Go to the post office")))
        repository.update(repository.get(id)!!.copy(text = "before 5 pm"))

        val capture = repository.get(id)!!
        assertThat(capture.transcript).isEqualTo("pick up the parcel")
        assertThat(capture.title).isEqualTo("Parcel")
        assertThat(capture.summary).isEqualTo("Collect the parcel today")
        assertThat(capture.actions).containsExactly("Go to the post office")
        assertThat(capture.text).isEqualTo("before 5 pm")
        val row = db.dao().get(id)!!
        listOf(row.transcriptEnc, row.titleEnc, row.summaryEnc, row.actionsEnc, row.textEnc).forEach {
            assertThat(it).isNotNull()
            assertThat(it).doesNotContain("parcel")
        }
        assertThat(repository.fileOf(capture)).isEqualTo(file)
    }

    @Test
    fun `a summary doesn't replace a title you gave`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "milk, eggs")
        repository.update(repository.get(id)!!.copy(title = "Shopping"))
        repository.applySummary(id, Summary("Groceries", "Buy food", emptyList()))
        assertThat(repository.get(id)!!.title).isEqualTo("Shopping")
    }

    @Test
    fun `delete removes the row and the file`() = runTest {
        val file = files.newFile(CaptureType.PHOTO, clock.now).apply { writeBytes(byteArrayOf(9)) }
        val id = repository.addFile(CaptureType.PHOTO, file)

        repository.delete(repository.get(id)!!)

        assertThat(repository.get(id)).isNull()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `move to vault hands it over and forgets it here`() = runTest {
        val vault = FakeVault()
        val file = files.newFile(CaptureType.VOICE, clock.now).apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val id = repository.addFile(CaptureType.VOICE, file)
        repository.setTranscript(id, "remember the dentist")

        repository.moveToVault(repository.get(id)!!, vault)

        assertThat(vault.files.single().second).isEqualTo("audio/mp4")
        assertThat(vault.files.single().third).isEqualTo(byteArrayOf(4, 5, 6))
        assertThat(vault.notes.single().second).contains("remember the dentist")
        val moved = repository.get(id)!!
        assertThat(moved.movedToVault).isTrue()
        assertThat(moved.transcript).isEmpty()
        assertThat(moved.file).isNull()
        assertThat(moved.title).isEqualTo("remember the dentist")
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `text moves to the vault as a note`() = runTest {
        val vault = FakeVault()
        val id = repository.addText(CaptureType.CLIPBOARD, "tracking 123")

        repository.moveToVault(repository.get(id)!!, vault)

        assertThat(vault.notes).containsExactly("tracking 123" to "tracking 123")
        assertThat(vault.files).isEmpty()
        assertThat(repository.get(id)!!.text).isEmpty()
    }

    @Test
    fun `a voice note gets its transcript when speech to text is ready`() = runTest {
        val file = files.newFile(CaptureType.VOICE, clock.now).apply { writeBytes(byteArrayOf(1)) }
        val id = repository.addFile(CaptureType.VOICE, file)
        val processor = CaptureProcessor(repository, FakeRecognizer(AiResult.Ready("hello there")), log)

        assertThat(processor.transcribe(id)).isTrue()
        assertThat(repository.get(id)!!.transcript).isEqualTo("hello there")
    }

    @Test
    fun `a voice note stays audio-only when speech to text isn't ready`() = runTest {
        val file = files.newFile(CaptureType.VOICE, clock.now).apply { writeBytes(byteArrayOf(1)) }
        val id = repository.addFile(CaptureType.VOICE, file)
        val processor = CaptureProcessor(repository, FakeRecognizer(AiResult.NotReady("not installed")), log)

        assertThat(processor.transcribe(id)).isFalse()
        assertThat(repository.get(id)!!.transcript).isEmpty()
        assertThat(repository.fileOf(repository.get(id)!!)).isEqualTo(file)
    }

    @Test
    fun `text that can't be decrypted reads as empty instead of crashing`() = runTest {
        val id = repository.addText(CaptureType.TEXT, "secret")
        val otherKey = FieldCipher { SecretKeySpec(ByteArray(32) { 1 }, "AES") }
        val other = CaptureRepository(db.dao(), otherKey, files, clock, log)

        assertThat(other.get(id)!!.text).isEmpty()
    }
}
