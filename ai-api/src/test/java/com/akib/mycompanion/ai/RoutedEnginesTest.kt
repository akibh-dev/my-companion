package com.akib.mycompanion.ai

import android.content.Context
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoutedEnginesTest {

    private val settings = object : SettingsStore {
        override val settings = MutableStateFlow(Settings())
        override suspend fun setPaused(paused: Boolean) = Unit
        override suspend fun setEnabled(feature: Feature, enabled: Boolean) = Unit
        override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) {
            val current = settings.value.modelEngines
            settings.value = settings.value.copy(modelEngines = if (useModel) current + engine else current - engine)
        }
    }

    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)

    /** No model is registered with the loader, so the router calls the model directly. */
    private val router = AiRouter(settings, ModelLoader(emptySet(), log, releaseAfterMillis = 50))

    private class FakeSummarizer(var answer: AiResult<Summary>) : Summarizer {
        val asked = mutableListOf<String>()
        override suspend fun summarize(text: String): AiResult<Summary> {
            asked += text
            return answer
        }
    }

    private class FakeRecognizer(val answer: AiResult<String>) : SpeechRecognizer {
        override suspend fun transcribe(audio: File, language: Locale) = answer
    }

    private class FakeSynth(val answer: AiResult<Unit>) : SpeechSynth {
        var stopped = 0
        val spoken = mutableListOf<Pair<String, Locale>>()
        override suspend fun speak(text: String, language: Locale): AiResult<Unit> {
            spoken += text to language
            return answer
        }
        override fun stop() {
            stopped++
        }
    }

    private val summary = Summary("Landlord", "Rent by the 5th.", listOf("send photos"))

    @Test
    fun `the summarizer model is only asked once it's switched on`() = runTest {
        val model = FakeSummarizer(AiResult.Ready(summary))
        val summarizer = RoutedSummarizer(router, NoSummarizer(), Optional.of(model))

        assertThat(summarizer.summarize("note")).isInstanceOf(AiResult.NotReady::class.java)
        assertThat(model.asked).isEmpty()

        settings.setUseModel(AiEngine.SUMMARIZER, true)
        assertThat(summarizer.summarize("note")).isEqualTo(AiResult.Ready(summary))
        assertThat(model.asked).containsExactly("note")
    }

    @Test
    fun `switching one engine on doesn't switch on the others`() = runTest {
        settings.setUseModel(AiEngine.TRANSLITERATOR, true)
        val model = FakeSummarizer(AiResult.Ready(summary))

        RoutedSummarizer(router, NoSummarizer(), Optional.of(model)).summarize("note")

        assertThat(model.asked).isEmpty()
        assertThat(router.usesModel(AiEngine.TRANSLITERATOR)).isTrue()
        assertThat(router.usesModel(AiEngine.SUMMARIZER)).isFalse()
    }

    @Test
    fun `speech to text uses the model when it's on and ready`() = runTest {
        settings.setUseModel(AiEngine.SPEECH_RECOGNIZER, true)
        val ready = RoutedSpeechRecognizer(
            router,
            NoSpeechRecognizer(),
            Optional.of(FakeRecognizer(AiResult.Ready("hi"))),
        )
        val loading = RoutedSpeechRecognizer(
            router,
            NoSpeechRecognizer(),
            Optional.of(FakeRecognizer(AiResult.NotReady("loading"))),
        )

        assertThat(ready.transcribe(File("note.m4a"))).isEqualTo(AiResult.Ready("hi"))
        assertThat(loading.transcribe(File("note.m4a"))).isInstanceOf(AiResult.NotReady::class.java)
    }

    @Test
    fun `the voice model speaks when it's on, and stop reaches both voices`() = runTest {
        settings.setUseModel(AiEngine.SPEECH_SYNTH, true)
        val model = FakeSynth(AiResult.Ready(Unit))
        val synth = RoutedSpeechSynth(router, PhoneSpeechSynth(mockk<Context>(relaxed = true)), Optional.of(model))

        assertThat(synth.speak("hello", Locale.UK)).isEqualTo(AiResult.Ready(Unit))
        assertThat(model.spoken).containsExactly("hello" to Locale.UK)

        synth.stop()
        assertThat(model.stopped).isEqualTo(1)
    }

    @Test
    fun `stop is safe without a voice model`() {
        RoutedSpeechSynth(router, PhoneSpeechSynth(mockk<Context>(relaxed = true)), Optional.empty()).stop()
    }

    @Test
    fun `valueOrNull unwraps only ready answers`() {
        val notReady: AiResult<String> = AiResult.NotReady("no")

        assertThat(AiResult.Ready("x").valueOrNull()).isEqualTo("x")
        assertThat(notReady.valueOrNull()).isNull()
    }

    @Test
    fun `the pass-through transliterator keeps the text as it is`() = runTest {
        assertThat(PassThroughTransliterator().toBangla("ami bhalo achi")).isEqualTo(AiResult.Ready("ami bhalo achi"))
    }
}
