package com.akib.mycompanion.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * The phone's own text-to-speech. Bangla is read with a Bangla voice when the phone has one (FR-5.11).
 * Nothing to ship and no RAM cost to the app.
 */
@Singleton
class PhoneSpeechSynth @Inject constructor(@ApplicationContext private val context: Context) : SpeechSynth {
    private val ready = CompletableDeferred<Boolean>()
    private val lock = Mutex()
    private var tts: TextToSpeech? = null

    private fun engine(): TextToSpeech = tts ?: TextToSpeech(context) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }.also { tts = it }

    override suspend fun speak(text: String, language: Locale): AiResult<Unit> = lock.withLock {
        val engine = engine()
        if (!ready.await()) return AiResult.NotReady("The phone's text-to-speech isn't available")
        if (engine.isLanguageAvailable(language) < TextToSpeech.LANG_AVAILABLE) {
            return AiResult.NotReady("No ${language.displayLanguage} voice on this phone")
        }
        engine.language = language
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { continuation ->
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                    }

                    @Suppress("OVERRIDE_DEPRECATION") // still abstract, so it has to be implemented
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
            continuation.invokeOnCancellation { engine.stop() }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        AiResult.Ready(Unit)
    }

    override fun stop() {
        tts?.stop()
    }
}
