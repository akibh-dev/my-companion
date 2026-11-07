package com.akib.mycompanion

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit local listening comparison. Leaves the app's chosen voice and system settings unchanged. */
@RunWith(AndroidJUnit4::class)
class PhoneVoiceAuditionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun auditionTwoInstalledBengaliVoices() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val initialized = CompletableDeferred<Int>()
        lateinit var engine: TextToSpeech
        instrumentation.runOnMainSync { engine = TextToSpeech(context) { initialized.complete(it) } }
        val audio = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { if (it < 0) engine.stop() }
            .build()
        try {
            assertThat(withTimeout(15_000) { initialized.await() }).isEqualTo(TextToSpeech.SUCCESS)
            assertThat(audio.mode).isEqualTo(AudioManager.MODE_NORMAL)
            assertThat(audio.requestAudioFocus(focus)).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            engine.setAudioAttributes(attributes)
            engine.setSpeechRate(1.0f)
            engine.setPitch(1.0f)
            val report = StringBuilder("label\tvoice\tlocale\tresult\n")
            val voices = listOf("A" to "bn-in-x-bnc-local", "B" to "bn-in-x-bnd-local")
            for ((label, name) in voices) {
                val voice = requireNotNull(engine.voices.firstOrNull { it.name == name }) { "Voice unavailable: $name" }
                assertThat(voice.isNetworkConnectionRequired).isFalse()
                assertThat(voice.features.orEmpty()).doesNotContain(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                assertThat(engine.setVoice(voice)).isEqualTo(TextToSpeech.SUCCESS)
                val completion = CompletableDeferred<String>()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == label) completion.complete("completed")
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == label) completion.complete("interrupted")
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == label) completion.complete("failed")
                    }
                })
                val announcement = if (label == "A") "প্রথম কণ্ঠ।" else "দ্বিতীয় কণ্ঠ।"
                val text = "$announcement Mr X just subscribed to your channel. Ha ha ha. " +
                    "আমি এখন বাসায়। কাল দেখা হবে। Please call me later."
                assertThat(engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, label)).isEqualTo(TextToSpeech.SUCCESS)
                val result = withTimeout(45_000) { completion.await() }
                report.appendLine("$label\t$name\t${voice.locale.toLanguageTag()}\t$result")
                assertThat(result).isEqualTo("completed")
            }
            File(context.filesDir, "female-announcer-audition.tsv").writeText(report.toString())
        } finally {
            engine.stop()
            audio.abandonAudioFocusRequest(focus)
            instrumentation.runOnMainSync { engine.shutdown() }
        }
    }
}
