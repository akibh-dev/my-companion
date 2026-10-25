package com.akib.mycompanion.ai.models

import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.Summary
import com.google.common.truth.Truth.assertThat
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
}
