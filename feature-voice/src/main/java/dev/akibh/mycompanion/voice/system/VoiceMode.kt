package dev.akibh.mycompanion.voice.system

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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.ai.SpeechAudio
import dev.akibh.mycompanion.core.host.HostedComponent
import dev.akibh.mycompanion.core.log.AppLog
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.core.util.enumOf
import dev.akibh.mycompanion.messaging.ChatApp
import dev.akibh.mycompanion.messaging.Notice
import dev.akibh.mycompanion.messaging.NoticeConsumer
import dev.akibh.mycompanion.voice.domain.AppScope
import dev.akibh.mycompanion.voice.domain.AudioSituation
import dev.akibh.mycompanion.voice.domain.BlockRule
import dev.akibh.mycompanion.voice.domain.Privacy
import dev.akibh.mycompanion.voice.domain.ReadingMode
import dev.akibh.mycompanion.voice.domain.VoiceDecider
import dev.akibh.mycompanion.voice.domain.VoiceSettings
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
            appScope = enumOf<AppScope>(p[SCOPE]) ?: defaults.appScope,
            mutedApps = p[MUTED_APPS] ?: legacyMutedApps(p[LEGACY_APPS]) ?: defaults.mutedApps,
            blockRules = p[RULES].orEmpty().mapNotNull(BlockRule::decode).distinct()
                .sortedBy { it.phrase.lowercase() },
            readSilent = p[READ_SILENT] ?: defaults.readSilent,
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
            p[SCOPE] = s.appScope.name
            p[MUTED_APPS] = s.mutedApps
            p[RULES] = s.blockRules.map(BlockRule::encode).toSet()
            p[READ_SILENT] = s.readSilent
            p.remove(LEGACY_APPS)
            p[SENDERS] = s.senders.map(String::trim).filter(String::isNotEmpty).toSet()
            p[MAX_CHARS] = s.maxChars.coerceIn(60, 600)
            p[RATE] = s.speechRate.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
            p[MODE] = s.readingMode.name
        }
    }

    private companion object {
        val PRIVACY = stringPreferencesKey("privacy")
        val SCOPE = stringPreferencesKey("app_scope")
        val MUTED_APPS = stringSetPreferencesKey("muted_apps")
        val RULES = stringSetPreferencesKey("block_rules")
        val READ_SILENT = booleanPreferencesKey("read_silent")

        /** Before every app was read: the chat apps that were on. The ones that were off become muted. */
        val LEGACY_APPS = stringSetPreferencesKey("apps")

        fun legacyMutedApps(on: Set<String>?): Set<String>? = on?.let { names ->
            ChatApp.entries.filter { it.name !in names }.map { it.packageName }.toSet()
        }
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
        /** The same attributes the voice plays with, so focus, routing and volume all follow media. */
        val ATTRIBUTES: AudioAttributes = SpeechAudio.ATTRIBUTES
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
    data class Item(val notice: Notice, val queuedAt: Long, val generation: Long)
    val items = Channel<Item>(capacity = 20, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var generation = 0L
    private var accepting = false
    private val seen = linkedMapOf<String, Long>()

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

    enum class Added { QUEUED, DUPLICATE, NOT_RUNNING }

    /** [Added.DUPLICATE] when the same notice was queued in the last five minutes. */
    @Synchronized fun add(notice: Notice, now: Long = SystemClock.elapsedRealtime()): Added {
        if (!accepting) return Added.NOT_RUNNING
        seen.entries.removeAll { now - it.value > STALE_MILLIS }
        if (notice.fingerprint in seen) return Added.DUPLICATE
        seen[notice.fingerprint] = now
        if (seen.size > 200) seen.remove(seen.keys.first())
        items.trySend(Item(notice, now, generation))
        return Added.QUEUED
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
    private val recent: RecentNotices,
    private val log: AppLog,
) : NoticeConsumer {
    override suspend fun onNotice(notice: Notice) {
        val app = settingsStore.settings.first()
        if (!app.isActive(Feature.VOICE_MODE)) {
            // Shown in Recent so a quiet phone has an explanation; voice mode switched off records nothing.
            if (app.paused && Feature.VOICE_MODE in app.enabled) recent.add(notice, PAUSED)
            return
        }
        val skip = VoiceDecider.skipReason(notice, voiceSettings.settings.first(), audio.situation())
        if (skip != null) {
            recent.add(notice, skip)
            log.i("Voice", "not queued: $skip")
            return
        }
        when (queue.add(notice)) {
            VoiceQueue.Added.QUEUED -> recent.add(notice, null)
            VoiceQueue.Added.DUPLICATE -> Unit
            VoiceQueue.Added.NOT_RUNNING -> {
                recent.add(notice, NOT_RUNNING)
                log.w("Voice", "not queued: the background service isn't running")
            }
        }
    }

    internal companion object {
        const val PAUSED = "paused from Home"
        const val NOT_RUNNING = "voice mode isn't running in the background; open the app to restart it"
    }
}

/**
 * The last notifications voice mode saw and whether they were read, in memory only, so the settings
 * screen can offer "mute this app" or "block this title" for something that was just read.
 */
@Singleton
class RecentNotices @Inject constructor() {
    data class Entry(val notice: Notice, val skipReason: String?, val at: Long)

    private val mutableEntries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = mutableEntries.asStateFlow()

    fun add(notice: Notice, skipReason: String?, at: Long = System.currentTimeMillis()) {
        mutableEntries.update { list ->
            (listOf(Entry(notice, skipReason, at)) + list.filter { it.notice.fingerprint != notice.fingerprint })
                .take(MAX)
        }
    }

    fun clear() {
        mutableEntries.value = emptyList()
    }

    private companion object {
        const val MAX = 25
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
