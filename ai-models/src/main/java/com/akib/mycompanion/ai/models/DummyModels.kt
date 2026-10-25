package com.akib.mycompanion.ai.models

import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.LoadableModel
import com.akib.mycompanion.ai.ModelBacked
import com.akib.mycompanion.ai.SpeechRecognizer
import com.akib.mycompanion.ai.SpeechSynth
import com.akib.mycompanion.ai.Summarizer
import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.core.settings.AiEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/*
 * Dummy stand-ins for the Track B models. They answer through the real interfaces, load and
 * release through the ModelLoader like a real model would, and are replaced one at a time when a
 * local model passes its tests (plan §7). Every answer is marked so it can't be mistaken for a model's.
 */

/** B2/B3 stand-in: a handful of common Banglish words, everything else unchanged. */
@Singleton
class DummyTransliterator @Inject constructor() : Transliterator, LoadableModel {
    override val engine = AiEngine.TRANSLITERATOR

    override suspend fun load() = delay(LOAD_MILLIS)

    override fun release() = Unit

    override suspend fun toBangla(text: String): AiResult<String> = AiResult.Ready(
        text.split(' ').joinToString(" ") { word ->
            val bare = word.lowercase().trimEnd('.', ',', '!', '?')
            WORDS[bare]?.let { it + word.substring(bare.length.coerceAtMost(word.length)) } ?: word
        },
    )

    private companion object {
        val WORDS = mapOf(
            "ami" to "আমি", "tumi" to "তুমি", "apni" to "আপনি", "kemon" to "কেমন", "acho" to "আছো",
            "achen" to "আছেন", "achi" to "আছি", "bhalo" to "ভালো", "valo" to "ভালো", "ki" to "কি",
            "korcho" to "করছো", "khabar" to "খাবার", "khaiso" to "খাইছো", "kotha" to "কথা", "bolo" to "বলো",
            "ashchi" to "আসছি", "jabo" to "যাবো", "kal" to "কাল", "aj" to "আজ", "na" to "না", "hae" to "হ্যাঁ",
            "tnx" to "ধন্যবাদ", "thanks" to "ধন্যবাদ", "ok" to "ঠিক আছে",
        )
    }
}

/** B4 stand-in: there's no Bangla voice model yet, so it says "not ready" and the phone's voice reads. */
@Singleton
class DummySpeechSynth @Inject constructor() : SpeechSynth, LoadableModel {
    override val engine = AiEngine.SPEECH_SYNTH

    override suspend fun load() = delay(LOAD_MILLIS)

    override fun release() = Unit

    override suspend fun speak(text: String, language: Locale): AiResult<Unit> =
        AiResult.NotReady("Dummy voice model: using the phone's voice")

    override fun stop() = Unit
}

/** B5 stand-in: a placeholder transcript that says how long the recording is. */
@Singleton
class DummySpeechRecognizer @Inject constructor() : SpeechRecognizer, LoadableModel {
    override val engine = AiEngine.SPEECH_RECOGNIZER

    override suspend fun load() = delay(LOAD_MILLIS)

    override fun release() = Unit

    override suspend fun transcribe(audio: File, language: Locale): AiResult<String> {
        if (!audio.exists()) return AiResult.NotReady("Recording not found")
        val kb = (audio.length() / 1024).coerceAtLeast(1)
        return AiResult.Ready(
            "[Dummy transcript] Voice note in ${language.displayLanguage}, $kb KB. " +
                "The real text appears when the speech model is installed."
        )
    }
}

/** B6 stand-in: first line as title, first sentences as summary, to-do-like lines as action items. */
@Singleton
class DummySummarizer @Inject constructor() : Summarizer, LoadableModel {
    override val engine = AiEngine.SUMMARIZER

    override suspend fun load() = delay(LOAD_MILLIS)

    override fun release() = Unit

    override suspend fun summarize(text: String): AiResult<Summary> {
        val clean = text.trim()
        if (clean.isEmpty()) return AiResult.NotReady("Nothing to summarise")
        val firstLine = clean.lineSequence().first().trim()
        val title = firstLine.split(Regex("\\s+")).take(TITLE_WORDS).joinToString(" ").trimEnd('.', ',', ':')
        val sentences = clean.replace('\n', ' ').split(Regex("(?<=[.!?।])\\s+")).filter { it.isNotBlank() }
        val actions = clean.lines().map { it.trim() }.filter { line ->
            line.startsWith("-") || line.startsWith("*") || ACTION_WORDS.any { line.lowercase().contains(it) }
        }.map { it.trimStart('-', '*', ' ') }
        return AiResult.Ready(
            Summary(
                title = "[Dummy] $title",
                summary = sentences.take(SUMMARY_SENTENCES).joinToString(" "),
                actionItems = actions,
            ),
        )
    }

    private companion object {
        const val TITLE_WORDS = 6
        const val SUMMARY_SENTENCES = 2
        val ACTION_WORDS = listOf("todo", "to do", "need to", "must", "remember to", "call ", "buy ", "send ")
    }
}

private const val LOAD_MILLIS = 50L

@Module
@InstallIn(SingletonComponent::class)
abstract class DummyModelsModule {
    @Binds @ModelBacked
    abstract fun transliterator(impl: DummyTransliterator): Transliterator

    @Binds @ModelBacked
    abstract fun speechSynth(impl: DummySpeechSynth): SpeechSynth

    @Binds @ModelBacked
    abstract fun speechRecognizer(impl: DummySpeechRecognizer): SpeechRecognizer

    @Binds @ModelBacked
    abstract fun summarizer(impl: DummySummarizer): Summarizer

    @Binds @IntoSet
    abstract fun transliteratorModel(impl: DummyTransliterator): LoadableModel

    @Binds @IntoSet
    abstract fun speechSynthModel(impl: DummySpeechSynth): LoadableModel

    @Binds @IntoSet
    abstract fun speechRecognizerModel(impl: DummySpeechRecognizer): LoadableModel

    @Binds @IntoSet
    abstract fun summarizerModel(impl: DummySummarizer): LoadableModel
}
