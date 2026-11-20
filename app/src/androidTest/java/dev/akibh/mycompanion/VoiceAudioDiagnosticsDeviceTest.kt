package dev.akibh.mycompanion

import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.ai.SpeechAudio
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic for "the voice sounds off": how long and how loud the phone's voices render the same
 * announcement (English, Bangla), and whether the +6 dB booster changes playback underruns. Writes
 * `voice-audio-diagnostics.tsv` to the app's files dir and logs it under the tag below. Plays aloud.
 *
 * Run without uninstalling the app: install the test APK, then
 * `adb shell am instrument -w -e class dev.akibh.mycompanion.VoiceAudioDiagnosticsDeviceTest
 * dev.akibh.mycompanion.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@RunWith(AndroidJUnit4::class)
class VoiceAudioDiagnosticsDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var engine: TextToSpeech

    @Test
    fun measureVoicesAndPlayback() = runBlocking<Unit> {
        val initialized = CompletableDeferred<Int>()
        instrumentation.runOnMainSync { engine = TextToSpeech(context) { initialized.complete(it) } }
        try {
            assertThat(withTimeout(15_000) { initialized.await() }).isEqualTo(TextToSpeech.SUCCESS)
            engine.setAudioAttributes(SpeechAudio.ATTRIBUTES)
            val report = StringBuilder("what\tvoice\tseconds\tsampleRate\trmsDbfs\tpeakDbfs\n")
            val bangla = pick(Locale.forLanguageTag("bn-BD"))
            val english = pick(Locale.US)
            val texts = mapOf(
                "english" to "Shell: Bank Test. Your account received five hundred taka from Rahim. " +
                    "Please check the statement today.",
                "bangla" to "শেল: ব্যাংক টেস্ট। আপনার অ্যাকাউন্টে রহিমের কাছ থেকে পাঁচশো টাকা এসেছে। " +
                    "আজ স্টেটমেন্ট দেখে নিন।",
            )
            for (voice in listOfNotNull(bangla, english)) {
                assertThat(engine.setVoice(voice)).isEqualTo(TextToSpeech.SUCCESS)
                for ((label, text) in texts) {
                    val wav = File(context.filesDir, "diag-${voice.locale.language}-$label.wav")
                    render(text, wav)
                    val m = measure(wav)
                    report.appendLine(
                        "$label\t${voice.name}\t${"%.2f".format(m.seconds)}\t${m.rate}\t" +
                            "${"%.1f".format(m.rms)}\t${"%.1f".format(m.peak)}",
                    )
                }
            }

            // Playback through the speaker as voice mode does, with and without the booster.
            val voice = requireNotNull(bangla)
            engine.setVoice(voice)
            val audio = context.getSystemService(AudioManager::class.java)
            for (boosted in listOf(true, false)) {
                val session = audio.generateAudioSessionId()
                val booster = if (boosted) {
                    LoudnessEnhancer(session).apply {
                        setTargetGain(SpeechAudio.BOOST_MILLIBELS)
                        enabled = true
                    }
                } else {
                    null
                }
                try {
                    speak(
                        texts.getValue("english"),
                        Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, session) }
                    )
                } finally {
                    booster?.release()
                }
                Thread.sleep(500)
                val track = shell("dumpsys media.audio_flinger").lines()
                    .lastOrNull { "AT::remove" in it && " $session " in it }
                    ?.trim()
                report.appendLine("playback boosted=$boosted session=$session\t$track")
            }
            File(context.filesDir, "voice-audio-diagnostics.tsv").writeText(report.toString())
            report.lines().forEach { Log.i(TAG, it) }
        } finally {
            engine.shutdown()
        }
    }

    /** Listening A/B on the speaker: "Sample one" with the +6 dB booster (as shipped), "Sample two" without. */
    @Test
    fun auditionBooster() = runBlocking<Unit> {
        val initialized = CompletableDeferred<Int>()
        instrumentation.runOnMainSync { engine = TextToSpeech(context) { initialized.complete(it) } }
        try {
            assertThat(withTimeout(15_000) { initialized.await() }).isEqualTo(TextToSpeech.SUCCESS)
            engine.setAudioAttributes(SpeechAudio.ATTRIBUTES)
            engine.setVoice(requireNotNull(pick(Locale.forLanguageTag("bn-BD"))))
            val audio = context.getSystemService(AudioManager::class.java)
            val text = "Gmail: Meeting moved. The team meeting is now at four, আজ বিকেলে দেখা হবে।"
            for ((label, boosted) in listOf("Sample one." to true, "Sample two." to false)) {
                val session = audio.generateAudioSessionId()
                val booster = if (boosted) {
                    LoudnessEnhancer(session).apply {
                        setTargetGain(SpeechAudio.BOOST_MILLIBELS)
                        enabled = true
                    }
                } else {
                    null
                }
                try {
                    speak("$label $text", Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, session) })
                } finally {
                    booster?.release()
                }
                Thread.sleep(1_500)
            }
        } finally {
            engine.shutdown()
        }
    }

    /** The same choice as PhoneSpeechSynth: offline, installed, matching country first, best quality. */
    private fun pick(language: Locale): Voice? = engine.voices.orEmpty().filter {
        it.locale.language == language.language && !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
    }.sortedWith(
        compareByDescending<Voice> { it.locale.country == language.country }
            .thenByDescending { it.quality }.thenBy { it.name },
    ).firstOrNull()

    private suspend fun render(text: String, file: File) {
        val done = listen("render")
        assertThat(engine.synthesizeToFile(text, Bundle(), file, "render")).isEqualTo(TextToSpeech.SUCCESS)
        assertThat(withTimeout(60_000) { done.await() }).isEqualTo("done")
    }

    private suspend fun speak(text: String, params: Bundle) {
        val done = listen("speak")
        assertThat(engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "speak")).isEqualTo(TextToSpeech.SUCCESS)
        assertThat(withTimeout(60_000) { done.await() }).isEqualTo("done")
    }

    private fun listen(id: String): CompletableDeferred<String> {
        val result = CompletableDeferred<String>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) result.complete("done")
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) result.complete("error")
            }
        })
        return result
    }

    private data class Measured(val seconds: Double, val rate: Int, val rms: Double, val peak: Double)

    /** 16-bit PCM WAV: duration, RMS and peak in dBFS. */
    private fun measure(file: File): Measured {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 0
        var channels = 1
        while (pos + 8 <= bytes.limit()) {
            val id = String(ByteArray(4) { bytes.get(pos + it) })
            val size = bytes.getInt(pos + 4)
            if (id == "fmt ") {
                channels = bytes.getShort(pos + 10).toInt()
                rate = bytes.getInt(pos + 12)
            }
            if (id == "data") {
                val samples = minOf(size, bytes.limit() - pos - 8) / 2
                var sum = 0.0
                var peak = 0
                for (i in 0 until samples) {
                    val s = bytes.getShort(pos + 8 + i * 2).toInt()
                    sum += s.toDouble() * s
                    peak = maxOf(peak, kotlin.math.abs(s))
                }
                val rms = sqrt(sum / maxOf(samples, 1)) / 32768.0
                return Measured(
                    samples.toDouble() / channels / maxOf(rate, 1),
                    rate,
                    20 * log10(maxOf(rms, 1e-9)),
                    20 * log10(maxOf(peak / 32768.0, 1e-9)),
                )
            }
            pos += 8 + size
        }
        error("No data chunk in ${file.name}")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "VoiceAudioDiag"
    }
}
