package com.akib.mycompanion.ai.models

import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.core.settings.AiEngine
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DummyModelsTest {

    @Test
    fun `dummy transliterator knows a few words and keeps the rest`() = runTest {
        assertThat(DummyTransliterator().toBangla("ami bhalo achi, Rahim!"))
            .isEqualTo(AiResult.Ready("আমি ভালো আছি, Rahim!"))
    }

    @Test
    fun `dummy summarizer picks a title, the first sentences and to-do lines`() = runTest {
        val note = """
            Meeting with the landlord about the flat. He wants the rent by the 5th. The roof leaks.
            - send photos of the leak
            Need to call the plumber on Sunday.
        """.trimIndent()

        val summary = (DummySummarizer().summarize(note) as AiResult.Ready<Summary>).value

        assertThat(summary.title).isEqualTo("[Dummy] Meeting with the landlord about the")
        assertThat(summary.summary).isEqualTo("Meeting with the landlord about the flat. He wants the rent by the 5th.")
        assertThat(
            summary.actionItems
        ).containsExactly("send photos of the leak", "Need to call the plumber on Sunday.").inOrder()
    }

    @Test
    fun `dummy voice model hands over to the phone's voice`() = runTest {
        assertThat(DummySpeechSynth().speak("hello")).isInstanceOf(AiResult.NotReady::class.java)
    }

    @Test
    fun `dummy transliterator ignores case and keeps trailing punctuation`() = runTest {
        assertThat(DummyTransliterator().toBangla("Ami. KEMON acho?"))
            .isEqualTo(AiResult.Ready("আমি. কেমন আছো?"))
        assertThat(DummyTransliterator().toBangla("")).isEqualTo(AiResult.Ready(""))
    }

    @Test
    fun `dummy transcript says how long the recording is and is clearly marked`() = runTest {
        val audio = File.createTempFile("note", ".m4a").apply { writeBytes(ByteArray(3 * 1024)) }

        val transcript = (DummySpeechRecognizer().transcribe(audio, Locale.ENGLISH) as AiResult.Ready<String>).value

        assertThat(transcript).startsWith("[Dummy transcript]")
        assertThat(transcript).contains("3 KB")
        assertThat(transcript).contains("English")
        audio.delete()
    }

    @Test
    fun `dummy transcript of a tiny recording still says at least 1 KB`() = runTest {
        val audio = File.createTempFile("tiny", ".m4a").apply { writeBytes(ByteArray(10)) }

        val transcript = (DummySpeechRecognizer().transcribe(audio, Locale.ENGLISH) as AiResult.Ready<String>).value

        assertThat(transcript).contains("1 KB")
        audio.delete()
    }

    @Test
    fun `a missing recording isn't transcribed`() = runTest {
        assertThat(DummySpeechRecognizer().transcribe(File("/no/such/file.m4a")))
            .isEqualTo(AiResult.NotReady("Recording not found"))
    }

    @Test
    fun `an empty note has nothing to summarise`() = runTest {
        assertThat(DummySummarizer().summarize("   \n ")).isEqualTo(AiResult.NotReady("Nothing to summarise"))
    }

    @Test
    fun `bangla full stops end sentences too`() = runTest {
        val summary = (DummySummarizer().summarize("আজ বৃষ্টি। কাল রোদ। পরশু ঝড়।") as AiResult.Ready<Summary>).value

        assertThat(summary.summary).isEqualTo("আজ বৃষ্টি। কাল রোদ।")
        assertThat(summary.actionItems).isEmpty()
    }

    @Test
    fun `a short note's title is the whole first line, without trailing punctuation`() = runTest {
        val summary = (DummySummarizer().summarize("Buy milk.\n* eggs") as AiResult.Ready<Summary>).value

        assertThat(summary.title).isEqualTo("[Dummy] Buy milk")
        assertThat(summary.actionItems).containsExactly("Buy milk.", "eggs").inOrder()
    }

    @Test
    fun `every dummy is the model for its own engine and loads and releases`() = runTest {
        val models = listOf(DummyTransliterator(), DummySpeechSynth(), DummySpeechRecognizer(), DummySummarizer())

        assertThat(models.map { it.engine }).containsExactlyElementsIn(AiEngine.entries)
        models.forEach {
            it.load()
            it.release()
        }
    }
}
