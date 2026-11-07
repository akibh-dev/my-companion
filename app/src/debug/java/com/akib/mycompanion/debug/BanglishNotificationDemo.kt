package com.akib.mycompanion.debug

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.service.notification.StatusBarNotification
import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.PhoneSpeechSynth
import com.akib.mycompanion.ai.models.IndicXlitPrototype
import com.akib.mycompanion.ai.models.TransliterationPreview
import com.akib.mycompanion.messaging.MessageSamples
import com.akib.mycompanion.messaging.NotificationExtractor
import com.akib.mycompanion.messaging.NotificationObserver
import com.akib.mycompanion.voice.domain.VoiceText
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Debug-only, fixed invented fixtures. Mixed spans are explicitly labeled, not auto-detected. */
@Singleton
class BanglishNotificationDemo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prototype: IndicXlitPrototype,
    private val speech: PhoneSpeechSynth,
    private val samples: MessageSamples,
) : NotificationObserver {
    data class State(
        val stage: String,
        val converted: String = "",
        val detail: String = "",
        val completedSegments: Int = 0,
        val voiceName: String = "",
        val modelLoadMillis: Long = 0,
        val conversionMillis: Long = 0,
        val ttsStartMillis: Long = 0,
    )

    private val mutableState = MutableStateFlow(State("idle"))
    val state = mutableState.asStateFlow()
    val listenerConnected get() = samples.connected

    fun reset() {
        check(state.value.stage !in setOf("received", "speaking")) { "Demo is already running" }
        mutableState.value = State("idle")
    }

    override suspend fun onPosted(notification: StatusBarNotification) {
        if (notification.packageName != context.packageName || notification.id != ID ||
            notification.notification.channelId != CHANNEL
        ) {
            return
        }
        val parts = NotificationExtractor.parts(notification)
        val body = parts.messages.lastOrNull()?.second
        if (body != MESSAGE && body != MIXED_MESSAGE) return
        if (!mutableState.compareAndSet(State("idle"), State("received"))) return
        try {
            val mixed = body == MIXED_MESSAGE
            val (converted, prepared) = coroutineScope {
                val preparation = async { speech.prepare(VoiceText.readingLanguage) }
                prototype.preview(if (mixed) MIXED_BANGLISH else MESSAGE) to preparation.await()
            }
            if (converted !is TransliterationPreview.Ready) {
                mutableState.value = State("failed", detail = "Offline conversion failed")
                return
            }
            if (prepared is AiResult.NotReady) {
                mutableState.value = State("failed", converted.text, prepared.reason)
                return
            }
            val spoken = if (mixed) {
                "$MIXED_NATIVE ${converted.text}. $MIXED_ENGLISH"
            } else {
                converted.text
            }
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio.mode != AudioManager.MODE_NORMAL) {
                mutableState.value = State("failed", spoken, "Phone audio is busy")
                return
            }
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                .setOnAudioFocusChangeListener { focus ->
                    if (focus < 0) speech.stop()
                }
                .build()
            if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mutableState.value = State("failed", spoken, "Audio focus was denied")
                return
            }
            try {
                mutableState.value = State("speaking", spoken)
                when (val result = speech.speakMeasured(spoken, VoiceText.readingLanguage)) {
                    is AiResult.Ready -> mutableState.value = State(
                        "completed", spoken, completedSegments = 1,
                        voiceName = result.value.voiceName,
                        modelLoadMillis = converted.loadMillis,
                        conversionMillis = converted.inferenceMillis,
                        ttsStartMillis = result.value.queuedToStartMillis,
                    )
                    is AiResult.NotReady -> mutableState.value = State("failed", spoken, result.reason)
                }
            } finally {
                audio.abandonAudioFocusRequest(request)
            }
        } catch (cancelled: CancellationException) {
            speech.stop()
            mutableState.value = State("failed", detail = "Test cancelled")
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = State("failed", detail = "Notification speech test failed")
        }
    }

    companion object {
        const val CHANNEL = "banglish-spoken-test"
        const val ID = 99034
        const val MESSAGE = "ami bangla boli"
        const val MIXED_NATIVE = "আমি এখন বাসায়।"
        const val MIXED_BANGLISH = "kal dekha hobe"
        const val MIXED_ENGLISH = "Please call me later."
        const val MIXED_MESSAGE = "$MIXED_NATIVE $MIXED_BANGLISH. $MIXED_ENGLISH"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BanglishDemoModule {
    @Binds @IntoSet
    abstract fun observer(impl: BanglishNotificationDemo): NotificationObserver
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BanglishDemoEntryPoint {
    fun demo(): BanglishNotificationDemo
    fun playback(): com.akib.mycompanion.voice.system.VoicePlayback
    fun voiceSettings(): com.akib.mycompanion.voice.system.VoiceSettingsStore
    fun settings(): com.akib.mycompanion.core.settings.SettingsStore
    fun transliterator(): com.akib.mycompanion.ai.Transliterator
}
