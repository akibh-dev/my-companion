package dev.akibh.mycompanion.voice.system

import dev.akibh.mycompanion.ai.AiResult
import dev.akibh.mycompanion.ai.SpeechSynth
import dev.akibh.mycompanion.ai.Transliterator
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.messaging.IncomingMessage
import dev.akibh.mycompanion.messaging.Notice
import dev.akibh.mycompanion.messaging.toNotice
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.VoiceBody
import dev.akibh.mycompanion.voice.domain.VoiceDecider
import dev.akibh.mycompanion.voice.domain.VoiceText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Preparing : VoiceState
    data object Speaking : VoiceState
    data class Blocked(val reason: String) : VoiceState
    data class Failed(val reason: String) : VoiceState
}

/** One serializer and cancellation path for queued messages and explicit foreground samples. */
@Singleton
class VoicePlayback @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val speech: SpeechSynth,
    transliterator: Transliterator,
    private val audio: AudioWatcher,
    private val queue: VoiceQueue,
    private val log: AppLog,
) {
    private val body = VoiceBody(transliterator)
    private val lock = Mutex()
    private val mutableState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state = mutableState.asStateFlow()

    @Volatile private var active: Job? = null

    @Volatile private var privateOnly = true

    fun stop() {
        queue.clear()
        active?.cancel(VoiceInterrupted("Stopped"))
        speech.stop()
    }

    suspend fun release() = lock.withLock { speech.shutdown() }
    suspend fun read(item: VoiceQueue.Item): AiResult<Unit> = play(item.notice, item, false)

    suspend fun sample(): AiResult<Unit> = play(
        IncomingMessage(
            "voice-sample",
            ChatApp.WHATSAPP,
            "Rahim",
            "ami bangla boli. Please call me later.",
            "Rahim",
            false,
            0,
            false
        ).toNotice(),
        null,
        true,
    )

    private suspend fun reason(notice: Notice, item: VoiceQueue.Item?, sample: Boolean): String? {
        val app = settingsStore.settings.first()
        if (app.paused) return "Paused from Home"
        if (!sample && !app.isActive(Feature.VOICE_MODE)) return "Voice mode is off"
        if (item != null && !queue.current(item)) return "Reading session ended"
        val situation = audio.situation()
        if (situation.inCall) return "Silent during calls"
        val settings = voiceSettings.settings.first()
        return if (sample) {
            if (settings.privacy == Privacy.HEADPHONES_OR_CAR && !situation.privateOutput) {
                "Connect headphones or Bluetooth audio, or choose speaker playback"
            } else {
                null
            }
        } else {
            VoiceDecider.skipReason(notice, settings, situation)
        }
    }

    private suspend fun play(
        notice: Notice,
        item: VoiceQueue.Item?,
        sample: Boolean,
    ): AiResult<Unit> = lock.withLock {
        val blocked = reason(notice, item, sample)
        if (blocked != null) {
            mutableState.value = VoiceState.Blocked(blocked)
            return AiResult.NotReady(blocked)
        }
        privateOnly = voiceSettings.settings.first().privacy == Privacy.HEADPHONES_OR_CAR
        mutableState.value = VoiceState.Preparing
        try {
            val result = coroutineScope {
                val operation = async(start = CoroutineStart.LAZY) {
                    val preparation = async { speech.prepare(VoiceText.readingLanguage) }
                    val settings = voiceSettings.settings.first()
                    val text = if (notice.text.isBlank()) "" else body.prepare(notice.text, settings)
                    val ready = preparation.await()
                    if (ready is AiResult.NotReady) return@async ready
                    val changed = reason(notice, item, sample)
                    if (changed != null) return@async AiResult.NotReady(changed)
                    audio.ducking {
                        val finalCheck = reason(notice, item, sample)
                        if (finalCheck != null) return@ducking AiResult.NotReady(finalCheck)
                        mutableState.value = VoiceState.Speaking
                        speech.speakConfigured(
                            VoiceText.announce(notice, text),
                            VoiceText.readingLanguage,
                            settings.speechRate
                        )
                    }
                }
                active = operation
                val routeGuard = audio.onPrivateRouteLost {
                    if (privateOnly) {
                        operation.cancel(VoiceInterrupted("Audio route disconnected"))
                        speech.stop()
                    }
                }
                val guard = launch {
                    while (isActive) {
                        privateOnly = voiceSettings.settings.first().privacy == Privacy.HEADPHONES_OR_CAR
                        val changed = reason(notice, item, sample)
                        if (changed != null) {
                            operation.cancel(VoiceInterrupted(changed))
                            speech.stop()
                            break
                        }
                        delay(75)
                    }
                }
                try {
                    operation.await()
                } finally {
                    guard.cancel()
                    routeGuard.close()
                    active = null
                }
            }
            when (result) {
                is AiResult.Ready -> {
                    mutableState.value = VoiceState.Idle
                    log.i("Voice", "read a notification")
                }
                is AiResult.NotReady -> {
                    mutableState.value = VoiceState.Failed(result.reason)
                    log.w("Voice", result.reason)
                }
            }
            result
        } catch (interrupted: VoiceInterrupted) {
            speech.stop()
            val text = interrupted.message ?: "Reading interrupted"
            mutableState.value = VoiceState.Blocked(text)
            AiResult.NotReady(text)
        } catch (cancelled: CancellationException) {
            speech.stop()
            mutableState.value = VoiceState.Idle
            throw cancelled
        } catch (_: Exception) {
            speech.stop()
            val error = "Reading failed. Check the offline voice and try again."
            mutableState.value = VoiceState.Failed(error)
            log.w("Voice", error)
            AiResult.NotReady(error)
        } finally {
            active = null
        }
    }
}
