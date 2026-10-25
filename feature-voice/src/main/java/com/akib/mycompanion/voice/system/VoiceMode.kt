package com.akib.mycompanion.voice.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akib.mycompanion.ai.ModelLoader
import com.akib.mycompanion.ai.SpeechSynth
import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.ai.valueOrNull
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.util.enumOf
import com.akib.mycompanion.core.util.enumsOf
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.messaging.MessageConsumer
import com.akib.mycompanion.voice.domain.AudioSituation
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceDecider
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.domain.VoiceText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.voiceStore: DataStore<Preferences> by preferencesDataStore(name = "voice")

@Singleton
class VoiceSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.voiceStore

    val settings: Flow<VoiceSettings> = store.data.map { p ->
        val defaults = VoiceSettings()
        VoiceSettings(
            privacy = enumOf<Privacy>(p[PRIVACY]) ?: defaults.privacy,
            apps = enumsOf<ChatApp>(p[APPS]) ?: defaults.apps,
            senders = p[SENDERS].orEmpty().sorted(),
            maxChars = p[MAX_CHARS] ?: defaults.maxChars,
            speechRate = p[RATE] ?: defaults.speechRate,
        )
    }

    suspend fun update(change: (VoiceSettings) -> VoiceSettings) {
        val s = change(settings.first())
        store.edit { p ->
            p[PRIVACY] = s.privacy.name
            p[APPS] = s.apps.map { it.name }.toSet()
            p[SENDERS] = s.senders.toSet()
            p[MAX_CHARS] = s.maxChars
            p[RATE] = s.speechRate
        }
    }

    private companion object {
        val PRIVACY = stringPreferencesKey("privacy")
        val APPS = stringSetPreferencesKey("apps")
        val SENDERS = stringSetPreferencesKey("senders")
        val MAX_CHARS = intPreferencesKey("max_chars")
        val RATE = floatPreferencesKey("rate")
    }
}

/** Audio output and call state, from the AudioManager. */
class AudioWatcher @Inject constructor(@ApplicationContext context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun situation(): AudioSituation {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        return AudioSituation(
            privateOutput = outputs.any { it in PRIVATE_OUTPUTS },
            inCall = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audio.mode == AudioManager.MODE_RINGTONE,
        )
    }

    /** Lowers other audio (music) while a message is read, then lets it come back (FR-5.9). */
    suspend fun <T> ducking(block: suspend () -> T): T {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        audio.requestAudioFocus(request)
        return try {
            block()
        } finally {
            audio.abandonAudioFocusRequest(request)
        }
    }

    private companion object {
        /** Headphones, headsets, and Bluetooth audio (headphones or the car). */
        val PRIVATE_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
    }
}

/** The reading queue (FR-5.2): one message at a time, in order. */
@Singleton
class VoiceQueue @Inject constructor() {
    data class Item(val text: String, val queuedAt: Long)

    val items =
        Channel<Item>(capacity = MAX_QUEUED, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    fun add(text: String, now: Long) {
        items.trySend(Item(text, now))
    }

    private companion object {
        const val MAX_QUEUED = 20
    }
}

/** Puts readable messages in the queue while voice mode is on. */
class VoiceConsumer @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val audio: AudioWatcher,
    private val queue: VoiceQueue,
    private val log: AppLog,
) : MessageConsumer {
    override suspend fun onMessage(message: IncomingMessage) {
        if (!settingsStore.settings.first().isActive(Feature.VOICE_MODE)) return
        val settings = voiceSettings.settings.first()
        val skip = VoiceDecider.skipReason(message, settings, audio.situation())
        if (skip != null) {
            log.i(TAG, "not read: $skip")
            return
        }
        queue.add(VoiceText.sentence(message, settings.maxChars), System.currentTimeMillis())
    }

    private companion object {
        const val TAG = "Voice"
    }
}

/**
 * Reads the queue aloud while voice mode is on. Waits out phone calls (FR-5.6), drops messages that
 * waited too long, and keeps the voice model loaded only while it runs (plan §2, RAM rule).
 */
class VoiceComponent @Inject constructor(
    private val queue: VoiceQueue,
    private val speech: SpeechSynth,
    private val transliterator: Transliterator,
    private val audio: AudioWatcher,
    private val loader: ModelLoader,
    private val log: AppLog,
) : HostedComponent {
    override val name = "voice-mode"

    override fun isWanted(settings: Settings) = settings.isActive(Feature.VOICE_MODE)

    override fun start(scope: CoroutineScope) {
        scope.launch {
            loader.keep(AiEngine.SPEECH_SYNTH, true)
            try {
                for (item in queue.items) {
                    while (audio.situation().inCall) delay(CALL_CHECK_MILLIS)
                    if (System.currentTimeMillis() - item.queuedAt > STALE_MILLIS) continue
                    read(item.text)
                }
            } finally {
                withContext(NonCancellable) {
                    speech.stop()
                    loader.keep(AiEngine.SPEECH_SYNTH, false)
                }
            }
        }
    }

    private suspend fun read(text: String) {
        // Banglish goes through the transliterator; its fallback passes it through until Track B's model.
        val spoken = if (VoiceText.looksLikeBanglish(
                text
            )
        ) {
            transliterator.toBangla(text).valueOrNull() ?: text
        } else {
            text
        }
        audio.ducking {
            speech.speak(spoken, VoiceText.language(spoken))
        }
        log.i("Voice", "read a message")
    }

    private companion object {
        const val CALL_CHECK_MILLIS = 2_000L
        const val STALE_MILLIS = 5 * 60_000L
    }
}
