package com.akib.mycompanion.ai

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// The built-in fallbacks (plan §2, "Fallback now"). They need no model, so the features work today.

/** Text passes through unchanged until the Banglish model (Track B, B2/B3) arrives. */
class PassThroughTransliterator @Inject constructor() : Transliterator {
    override suspend fun toBangla(text: String): AiResult<String> = AiResult.Ready(text)
}

/** No speech-to-text yet: voice notes stay as audio. */
class NoSpeechRecognizer @Inject constructor() : SpeechRecognizer {
    override suspend fun transcribe(audio: File, language: Locale): AiResult<String> =
        AiResult.NotReady("Speech to text isn't installed yet")
}

/** No summaries yet: the summary option stays hidden. */
class NoSummarizer @Inject constructor() : Summarizer {
    override suspend fun summarize(
        text: String
    ): AiResult<Summary> = AiResult.NotReady("Summaries aren't installed yet")
}

/** Timing reported by the installed TTS engine; contains no message text. */
data class SpeechTiming(
    val voiceName: String,
    val languageTag: String,
    val queuedToStartMillis: Long,
    val playbackMillis: Long,
)

/** Installed offline phone voices, selected deterministically and reused for each requested locale. */
@Singleton
class PhoneSpeechSynth @Inject constructor(@ApplicationContext private val context: Context) : SpeechSynth {
    private var ready = CompletableDeferred<Boolean>()
    private val lock = Mutex()
    private var tts: TextToSpeech? = null
    private val selectedVoices = mutableMapOf<String, Voice>()

    private fun engine(): TextToSpeech {
        tts?.let { return it }
        val readiness = CompletableDeferred<Boolean>()
        ready = readiness
        return TextToSpeech(context) { status -> readiness.complete(status == TextToSpeech.SUCCESS) }.also { tts = it }
    }

    /** Initializes and selects the voice while other work (such as transliteration) runs. */
    override suspend fun prepare(language: Locale): AiResult<Unit> = lock.withLock { prepare(engine(), language) }

    override suspend fun speak(text: String, language: Locale): AiResult<Unit> = when (
        val result = speakMeasured(text, language)
    ) {
        is AiResult.Ready -> AiResult.Ready(Unit)
        is AiResult.NotReady -> result
    }

    override suspend fun speakConfigured(text: String, language: Locale, rate: Float): AiResult<Unit> = when (
        val result = speakMeasured(text, language, rate)
    ) {
        is AiResult.Ready -> AiResult.Ready(Unit)
        is AiResult.NotReady -> result
    }

    private suspend fun prepare(engine: TextToSpeech, language: Locale): AiResult<Unit> {
        if (withTimeoutOrNull(10_000) { ready.await() } != true) {
            engine.shutdown()
            tts = null
            selectedVoices.clear()
            return AiResult.NotReady("The phone's text-to-speech isn't available")
        }
        val voice = selectedVoices[language.toLanguageTag()] ?: engine.voices.orEmpty().filter {
            it.locale.language == language.language && !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }.sortedWith(
            compareByDescending<Voice> { it.locale.country == language.country }
                .thenByDescending { it.quality }.thenBy { it.name },
        ).firstOrNull()?.also { selectedVoices[language.toLanguageTag()] = it }
            ?: return AiResult.NotReady("No offline ${language.displayLanguage} voice installed on this phone")
        if (engine.voice?.name != voice.name && engine.setVoice(voice) != TextToSpeech.SUCCESS) {
            selectedVoices.remove(language.toLanguageTag())
            return AiResult.NotReady("The phone couldn't select its ${language.displayLanguage} voice")
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        return AiResult.Ready(Unit)
    }

    suspend fun speakMeasured(
        text: String,
        language: Locale,
        rate: Float = 1f,
    ): AiResult<SpeechTiming> = lock.withLock {
        val engine = engine()
        val prepared = prepare(engine, language)
        if (prepared is AiResult.NotReady) return prepared
        if (!rate.isFinite() || rate !in 0.5f..2f || engine.setSpeechRate(rate) != TextToSpeech.SUCCESS) {
            return AiResult.NotReady("Speech speed must be between 0.5 and 2")
        }
        if (text.length > TextToSpeech.getMaxSpeechInputLength()) {
            return AiResult.NotReady("The message is too long to read")
        }
        val id = UUID.randomUUID().toString()
        val voiceName = requireNotNull(engine.voice).name
        val queued = System.nanoTime()
        val started = AtomicLong(0)
        withTimeoutOrNull((text.length * 150L / rate).toLong().coerceIn(30_000L, 180_000L)) {
            suspendCancellableCoroutine<AiResult<SpeechTiming>> { continuation ->
                val completed = AtomicBoolean(false)
                fun finish(result: AiResult<SpeechTiming>) {
                    if (completed.compareAndSet(false, true)) continuation.resume(result)
                }
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId == id) started.compareAndSet(0, System.nanoTime())
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == id) {
                                val beginning = started.get()
                                if (beginning == 0L) {
                                    finish(AiResult.NotReady("Speech finished without a start callback"))
                                } else {
                                    finish(
                                        AiResult.Ready(
                                            SpeechTiming(
                                                voiceName,
                                                language.toLanguageTag(),
                                                (beginning - queued) / 1_000_000,
                                                (System.nanoTime() - beginning) / 1_000_000,
                                            )
                                        )
                                    )
                                }
                            }
                        }

                        override fun onStop(utteranceId: String?, interrupted: Boolean) {
                            if (utteranceId == id) finish(AiResult.NotReady("Speech was interrupted"))
                        }

                        @Suppress("OVERRIDE_DEPRECATION") // still abstract, so it has to be implemented
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == id) finish(AiResult.NotReady("The phone's speech engine failed"))
                        }
                    },
                )
                continuation.invokeOnCancellation { engine.stop() }
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
                    finish(AiResult.NotReady("The phone couldn't start speaking"))
                }
            }
        } ?: AiResult.NotReady("Speech timed out")
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override suspend fun shutdown() {
        stop()
        lock.withLock {
            tts?.shutdown()
            tts = null
            selectedVoices.clear()
        }
    }
}
