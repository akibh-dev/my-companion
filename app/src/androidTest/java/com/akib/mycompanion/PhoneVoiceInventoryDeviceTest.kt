package com.akib.mycompanion

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/** Inspects local voice metadata only; does not play audio or change the system TTS default. */
@RunWith(AndroidJUnit4::class)
class PhoneVoiceInventoryDeviceTest {
    @Test
    fun recordInstalledOfflineEnglishAndBengaliVoices() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ready = CompletableDeferred<Int>()
        lateinit var engine: TextToSpeech
        instrumentation.runOnMainSync { engine = TextToSpeech(context) { ready.complete(it) } }
        try {
            assertThat(withTimeout(15_000) { ready.await() }).isEqualTo(TextToSpeech.SUCCESS)
            val voices = engine.voices.orEmpty().filter {
                it.locale.language in setOf("bn", "en") && !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }.sortedBy { it.name }
            assertThat(voices.any { it.locale.language == "bn" }).isTrue()
            File(context.filesDir, "offline-voice-inventory.tsv").writeText(
                "name\tlocale\tquality\tlatency\n" + voices.joinToString("\n") {
                    "${it.name}\t${it.locale.toLanguageTag()}\t${it.quality}\t${it.latency}"
                } + "\n",
            )
        } finally {
            instrumentation.runOnMainSync { engine.shutdown() }
        }
    }
}
