package com.akib.mycompanion.voice

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.SpeechSynth
import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.system.AudioWatcher
import com.akib.mycompanion.voice.system.VoiceComponent
import com.akib.mycompanion.voice.system.VoiceConsumer
import com.akib.mycompanion.voice.system.VoiceQueue
import com.akib.mycompanion.voice.system.VoiceSettingsStore
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.AudioDeviceInfoBuilder

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VoiceModeTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val log = AppLog(Files.createTempDirectory("log").toFile(), mirrorToLogcat = false)
    private val audio = AudioWatcher(app)
    private val queue = VoiceQueue()

    private val message =
        IncomingMessage("k", ChatApp.WHATSAPP, "Nadia", "On my way", "Nadia", false, 0, true)

    private fun output(type: Int): AudioDeviceInfo = AudioDeviceInfoBuilder.newBuilder().setType(type).build()

    @org.junit.Before
    fun openQueue() {
        queue.open()
    }

    private fun routes(devices: List<AudioDeviceInfo>) {
        shadowOf(
            audioManager
        ).setAudioDevicesForAttributes(AudioWatcher.ATTRIBUTES, com.google.common.collect.ImmutableList.copyOf(devices))
    }

    private fun drain(): List<String> =
        generateSequence { queue.items.tryReceive().getOrNull() }.map { it.message.text }.toList()

    // ---- Audio situation ----

    @Test
    fun `headphones and bluetooth count as private, the speaker doesn't`() {
        routes(listOf(output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertThat(audio.situation().privateOutput).isFalse()

        routes(
            listOf(output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)),
        )
        assertThat(audio.situation().privateOutput).isFalse()

        routes(listOf(output(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertThat(audio.situation().privateOutput).isTrue()
    }

    @Test
    fun `calls, ringing and video calls count as being in a call`() {
        audioManager.mode = AudioManager.MODE_NORMAL
        assertThat(audio.situation().inCall).isFalse()

        listOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE).forEach {
            audioManager.mode = it
            assertThat(audio.situation().inCall).isTrue()
        }
    }

    @Test
    fun `other audio is ducked while reading and comes back after, even on failure`() = runTest {
        val answer = audio.ducking { 42 }

        assertThat(answer).isEqualTo(42)
        assertThat(shadowOf(audioManager).lastAudioFocusRequest).isNotNull()
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { audio.ducking { error("speech failed") } }
        }
    }

    // ---- Queue ----

    @Test
    fun `the queue keeps order and drops the oldest when full`() {
        repeat(25) { queue.add(message.copy(text = "message $it"), now = it.toLong()) }

        val texts = drain()

        assertThat(texts).hasSize(20)
        assertThat(texts.first()).isEqualTo("message 5")
        assertThat(texts.last()).isEqualTo("message 24")
    }

    // ---- Consumer ----

    private val settings = MutableStateFlow(Settings(enabled = setOf(Feature.VOICE_MODE)))
    private val settingsStore = object : SettingsStore {
        override val settings = this@VoiceModeTest.settings
        override suspend fun setPaused(paused: Boolean) = Unit
        override suspend fun setEnabled(feature: Feature, enabled: Boolean) = Unit
        override suspend fun setUseModel(engine: AiEngine, useModel: Boolean) = Unit
    }
    private val voiceSettings = MutableStateFlow(VoiceSettings(privacy = Privacy.ALWAYS))
    private val voiceStore = mockk<VoiceSettingsStore> { every { settings } returns voiceSettings }
    private val consumer = VoiceConsumer(settingsStore, voiceStore, audio, queue, log)

    @Test
    fun `readable messages are queued as sentences`() = runTest {
        consumer.onMessage(message)

        assertThat(drain()).containsExactly("On my way")
    }

    @Test
    fun `nothing is queued while voice mode is off or paused`() = runTest {
        settings.value = Settings(paused = true, enabled = setOf(Feature.VOICE_MODE))
        consumer.onMessage(message)
        settings.value = Settings()
        consumer.onMessage(message)

        assertThat(drain()).isEmpty()
    }

    @Test
    fun `privacy keeps messages quiet without headphones`() = runTest {
        voiceSettings.value = VoiceSettings(privacy = Privacy.HEADPHONES_OR_CAR)
        routes(listOf(output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        consumer.onMessage(message)
        assertThat(drain()).isEmpty()

        routes(listOf(output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        consumer.onMessage(message)
        assertThat(drain()).hasSize(1)
    }

    @Test
    fun `the length limit applies to what's read`() = runTest {
        voiceSettings.value = VoiceSettings(privacy = Privacy.ALWAYS, maxChars = 10)

        consumer.onMessage(message.copy(text = "one two three four five six"))

        assertThat(drain().single()).isEqualTo("one two three four five six")
    }

    // ---- Reader ----

    private class FakeSynth : SpeechSynth {
        val spoken = mutableListOf<Pair<String, Locale>>()
        var stops = 0
        override suspend fun speak(text: String, language: Locale): AiResult<Unit> {
            spoken += text to language
            return AiResult.Ready(Unit)
        }
        override fun stop() {
            stops++
        }
    }

    private class FixedTransliterator : Transliterator {
        val asked = mutableListOf<String>()
        override suspend fun toBangla(text: String): AiResult<String> {
            asked += text
            return AiResult.Ready("আমি ভালো আছি")
        }
    }

    private val synth = FakeSynth()
    private val transliterator = FixedTransliterator()
    private val playback = com.akib.mycompanion.voice.system.VoicePlayback(
        settingsStore,
        voiceStore,
        synth,
        transliterator,
        audio,
        queue,
        log
    )
    private val component =
        VoiceComponent(queue, playback, audio)

    @Test
    fun `wanted only while voice mode is on and nothing is paused`() {
        assertThat(component.name).isEqualTo("voice-mode")
        assertThat(component.isWanted(Settings(enabled = setOf(Feature.VOICE_MODE)))).isTrue()
        assertThat(component.isWanted(Settings(paused = true, enabled = setOf(Feature.VOICE_MODE)))).isFalse()
        assertThat(component.isWanted(Settings())).isFalse()
    }

    @Test
    fun `messages are read in order, banglish through the transliterator, then it stops cleanly`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        component.start(scope)

        queue.add(message.copy(text = "see you at 5"))
        queue.add(message.copy(sender = "Rahim", app = ChatApp.SIGNAL, text = "ami bhalo achi"))
        runCurrent()

        assertThat(synth.spoken.map { it.first })
            .containsExactly(
                "Nadia on WhatsApp says: see you at 5",
                "Rahim on Signal says: আমি ভালো আছি আমি ভালো আছি আমি ভালো আছি"
            )
            .inOrder()
        assertThat(synth.spoken.last().second.toLanguageTag()).isEqualTo("bn-BD")
        assertThat(synth.spoken.first().second.toLanguageTag()).isEqualTo("bn-BD")
        assertThat(transliterator.asked).containsExactly("ami", "bhalo", "achi")

        scope.cancel()
        runCurrent()
        assertThat(synth.stops).isAtLeast(1)
    }

    @Test
    fun `messages that waited more than five minutes are dropped`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        queue.add(message.copy(text = "old news"), android.os.SystemClock.elapsedRealtime() - 6 * 60_000)
        queue.add(message.copy(text = "fresh"))

        component.start(scope)
        runCurrent()

        assertThat(synth.spoken.map { it.first }).containsExactly("Nadia on WhatsApp says: fresh")
        scope.cancel()
    }

    @Test
    fun `duplicate updates are suppressed but a later identical message is read`() {
        queue.add(message)
        queue.add(message)
        queue.add(message.copy(postedAt = 1))
        assertThat(drain()).hasSize(2)
    }

    @Test
    fun `stopping invalidates pending items and a closed queue accepts nothing`() {
        queue.add(message)
        val item = queue.items.tryReceive().getOrThrow()
        queue.clear()
        assertThat(queue.current(item)).isFalse()
        queue.close()
        queue.add(message.copy(postedAt = 2))
        assertThat(drain()).isEmpty()
    }

    @Test
    fun `denied audio focus never executes speech`() = runTest {
        shadowOf(audioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        var spoken = false
        assertThrows(IllegalStateException::class.java) { runBlocking { audio.ducking { spoken = true } } }
        assertThat(spoken).isFalse()
    }

    @Test
    fun `privacy is rechecked before reading a queued message`() = runTest {
        queue.add(message)
        val item = queue.items.tryReceive().getOrThrow()
        voiceSettings.value = VoiceSettings(privacy = Privacy.HEADPHONES_OR_CAR)
        routes(listOf(output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertThat(playback.read(item)).isInstanceOf(AiResult.NotReady::class.java)
        assertThat(synth.spoken).isEmpty()
    }

    @Test
    fun `pause during conversion prevents playback`() = runTest {
        val waiting = object : Transliterator {
            override suspend fun toBangla(text: String): AiResult<String> {
                settings.value = settings.value.copy(paused = true)
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val reader = com.akib.mycompanion.voice.system.VoicePlayback(
            settingsStore,
            voiceStore,
            synth,
            waiting,
            audio,
            queue,
            log
        )
        val result = reader.sample()
        assertThat(result).isInstanceOf(AiResult.NotReady::class.java)
        assertThat(synth.spoken).isEmpty()
        assertThat(synth.stops).isGreaterThan(0)
    }

    @Test
    fun `stop interrupts current speech and the next sample still works`() = runTest {
        var first = true
        var appliedRate = 0f
        val blocking = object : SpeechSynth {
            override suspend fun speak(text: String, language: Locale): AiResult<Unit> = error("Use configured speech")
            override suspend fun speakConfigured(text: String, language: Locale, rate: Float): AiResult<Unit> {
                appliedRate = rate
                if (first) {
                    first = false
                    kotlinx.coroutines.awaitCancellation()
                }
                return AiResult.Ready(Unit)
            }
            override fun stop() = Unit
        }
        voiceSettings.value = VoiceSettings(privacy = Privacy.ALWAYS, speechRate = 1.4f)
        val reader = com.akib.mycompanion.voice.system.VoicePlayback(
            settingsStore,
            voiceStore,
            blocking,
            transliterator,
            audio,
            queue,
            log
        )
        val reading = async { reader.sample() }
        runCurrent()
        assertThat(reader.state.value).isEqualTo(com.akib.mycompanion.voice.system.VoiceState.Speaking)
        reader.stop()
        runCurrent()
        assertThat(reading.await()).isInstanceOf(AiResult.NotReady::class.java)
        assertThat(reader.sample()).isEqualTo(AiResult.Ready(Unit))
        assertThat(appliedRate).isEqualTo(1.4f)
    }

    @Test
    fun `rapid restart cannot let previous cleanup close the new queue`() = runTest {
        val first = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val second = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        component.start(first)
        first.cancel()
        component.start(second)
        runCurrent()
        queue.add(message)
        runCurrent()
        assertThat(synth.spoken.map { it.first }).containsExactly("Nadia on WhatsApp says: On my way")
        second.cancel()
    }
}
