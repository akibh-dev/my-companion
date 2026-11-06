package com.akib.mycompanion

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Plays locally rendered style auditions only; does not replace or install a speech backend. */
@RunWith(AndroidJUnit4::class)
class RoboticVoiceAuditionDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun playBrighterAndFullerRoboticSamples() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(
            "Audition WAVs must be installed explicitly",
            listOf("c", "d").all { File(context.filesDir, "robotic-$it.wav").isFile }
        )
        val audio = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val current = AtomicReference<MediaPlayer?>()
        val completion = AtomicReference<CompletableDeferred<Boolean>?>()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener {
                if (it < 0) {
                    current.get()?.pause()
                    completion.get()?.complete(false)
                }
            }
            .build()
        assertThat(audio.mode).isEqualTo(AudioManager.MODE_NORMAL)
        assertThat(audio.requestAudioFocus(focus)).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        try {
            val report = StringBuilder("sample\tplayback_completed\n")
            for (label in listOf("c", "d")) {
                val file = File(context.filesDir, "robotic-$label.wav")
                assertThat(file.isFile).isTrue()
                val done = CompletableDeferred<Boolean>()
                val player = MediaPlayer()
                current.set(player)
                completion.set(done)
                try {
                    player.setAudioAttributes(attributes)
                    player.setOnCompletionListener { done.complete(true) }
                    player.setOnErrorListener { _, _, _ ->
                        done.complete(false)
                        true
                    }
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    player.start()
                    val completed = withTimeout(45_000) { done.await() }
                    assertThat(completed).isTrue()
                    report.appendLine("${label.uppercase()}\t$completed")
                } finally {
                    current.compareAndSet(player, null)
                    completion.compareAndSet(done, null)
                    player.release()
                }
            }
            File(context.filesDir, "robotic-voice-audition.tsv").writeText(report.toString())
        } finally {
            audio.abandonAudioFocusRequest(focus)
        }
    }
}
