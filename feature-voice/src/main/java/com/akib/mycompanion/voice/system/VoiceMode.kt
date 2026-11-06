package com.akib.mycompanion.voice.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.log.AppLog
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
import com.akib.mycompanion.voice.domain.ReadingMode
import com.akib.mycompanion.voice.domain.VoiceDecider
import com.akib.mycompanion.voice.domain.VoiceSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
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

    private fun decode(p: Preferences): VoiceSettings {
        val defaults = VoiceSettings()
        return VoiceSettings(
            privacy = enumOf<Privacy>(p[PRIVACY]) ?: defaults.privacy,
            apps = enumsOf<ChatApp>(p[APPS]) ?: defaults.apps,
            senders = p[SENDERS].orEmpty().sorted(),
            maxChars = (p[MAX_CHARS] ?: defaults.maxChars).coerceIn(60, 600),
            speechRate = (p[RATE] ?: defaults.speechRate).takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f,
            readingMode = enumOf<ReadingMode>(p[MODE]) ?: defaults.readingMode,
        )
    }

    val settings: Flow<VoiceSettings> = store.data.map(::decode)

    suspend fun update(change: (VoiceSettings) -> VoiceSettings) {
        store.edit { p ->
            val s = change(decode(p))
            p[PRIVACY] = s.privacy.name
            p[APPS] = s.apps.map { it.name }.toSet()
            p[SENDERS] = s.senders.map(String::trim).filter(String::isNotEmpty).toSet()
            p[MAX_CHARS] = s.maxChars.coerceIn(60, 600)
            p[RATE] = s.speechRate.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
            p[MODE] = s.readingMode.name
        }
    }

    private companion object {
        val PRIVACY = stringPreferencesKey("privacy")
        val APPS = stringSetPreferencesKey("apps")
        val SENDERS = stringSetPreferencesKey("senders")
        val MAX_CHARS = intPreferencesKey("max_chars")
        val RATE = floatPreferencesKey("rate")
        val MODE = stringPreferencesKey("reading_mode")
    }
}

/** Actual anticipated speech routes; connected devices alone do not prove private output. */
class AudioWatcher @Inject constructor(@ApplicationContext private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun situation(): AudioSituation {
        val routes = runCatching { audio.getAudioDevicesForAttributes(ATTRIBUTES) }.getOrDefault(emptyList())
        return AudioSituation(
            privateOutput = routes.isNotEmpty() && routes.all { it.type in PRIVATE_OUTPUTS },
            inCall = audio.mode != AudioManager.MODE_NORMAL,
        )
    }

    fun onPrivateRouteLost(onLoss: () -> Unit): Closeable {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onLoss()
            }
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any { it.type in PRIVATE_OUTPUTS }) onLoss()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        try {
            audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        } catch (failure: Exception) {
            context.unregisterReceiver(receiver)
            throw failure
        }
        return Closeable {
            audio.unregisterAudioDeviceCallback(callback)
            context.unregisterReceiver(receiver)
        }
    }

    suspend fun <T> ducking(block: suspend () -> T): T = coroutineScope {
        val operation = currentCoroutineContext()[Job]!!
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            .setOnAudioFocusChangeListener(
                { if (it < 0) operation.cancel(VoiceInterrupted("Audio focus lost")) },
                Handler(Looper.getMainLooper()),
            ).build()
        check(audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio focus denied" }
        try {
            block()
        } finally {
            audio.abandonAudioFocusRequest(request)
        }
    }

    companion object {
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        private val PRIVATE_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}

internal class VoiceInterrupted(reason: String) : CancellationException(reason)

@Singleton
class VoiceQueue @Inject constructor() {
    data class Item(val message: IncomingMessage, val queuedAt: Long, val generation: Long)
    val items = Channel<Item>(capacity = 20, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var generation = 0L
    private var accepting = false
    private val seen = linkedMapOf<List<Any>, Long>()

    @Synchronized fun open() {
        accepting = true
    }

    @Synchronized fun close() {
        accepting = false
        clear()
        seen.clear()
    }

    @Synchronized fun clear() {
        generation++
        while (items.tryReceive().isSuccess) Unit
    }

    @Synchronized fun current(item: Item): Boolean = accepting && item.generation == generation

    @Synchronized fun add(message: IncomingMessage, now: Long = SystemClock.elapsedRealtime()) {
        if (!accepting) return
        seen.entries.removeAll { now - it.value > STALE_MILLIS }
        val fingerprint = listOf(message.app, message.key, message.sender, message.text, message.postedAt)
        if (fingerprint in seen) return
        seen[fingerprint] = now
        if (seen.size > 200) seen.remove(seen.keys.first())
        items.trySend(Item(message, now, generation))
    }
    companion object {
        const val STALE_MILLIS = 5 * 60_000L
    }
}

class VoiceConsumer @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val audio: AudioWatcher,
    private val queue: VoiceQueue,
    private val log: AppLog,
) : MessageConsumer {
    override suspend fun onMessage(message: IncomingMessage) {
        if (!settingsStore.settings.first().isActive(Feature.VOICE_MODE)) return
        val skip = VoiceDecider.skipReason(message, voiceSettings.settings.first(), audio.situation())
        if (skip != null) {
            log.i("Voice", "not queued: $skip")
            return
        }
        queue.add(message)
    }
}

@Singleton
class VoiceComponent @Inject constructor(
    private val queue: VoiceQueue,
    private val playback: VoicePlayback,
    private val audio: AudioWatcher,
) : HostedComponent {
    private val lifecycle = kotlinx.coroutines.sync.Mutex()
    override val name = "voice-mode"
    override fun isWanted(settings: Settings) = settings.isActive(Feature.VOICE_MODE)
    override fun start(scope: CoroutineScope) {
        scope.launch {
            lifecycle.lock()
            try {
                queue.open()
                consume()
            } finally {
                try {
                    withContext(NonCancellable) {
                        queue.close()
                        playback.stop()
                        playback.release()
                    }
                } finally {
                    lifecycle.unlock()
                }
            }
        }
    }

    private suspend fun consume() {
        for (item in queue.items) {
            while (audio.situation().inCall &&
                SystemClock.elapsedRealtime() - item.queuedAt < VoiceQueue.STALE_MILLIS
            ) {
                delay(250)
            }
            if (!queue.current(item) ||
                SystemClock.elapsedRealtime() - item.queuedAt >= VoiceQueue.STALE_MILLIS
            ) {
                continue
            }
            playback.read(item)
        }
    }
}
