package com.akib.mycompanion.data.sound

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.akib.mycompanion.domain.model.ReminderSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Explicit foreground preview only. Notification delivery uses the platform channel player. */
@Singleton
class ReminderSoundPreview @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sounds: ReminderSoundChannels,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var generation = 0L

    suspend fun play(sound: ReminderSound) = withContext(Dispatchers.Main.immediate) {
        stop()
        val requestGeneration = generation
        val selection = sounds.resolve(sound)
        if (requestGeneration != generation) return@withContext
        val uri = selection.uri ?: return@withContext
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ReminderSoundChannels.ATTRIBUTES)
            .setOnAudioFocusChangeListener { if (it < 0) stop() }
            .build()
        check(
            audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) { "Audio focus is unavailable" }
        focus = request
        val current = MediaPlayer()
        player = current
        try {
            current.setAudioAttributes(ReminderSoundChannels.ATTRIBUTES)
            current.setDataSource(context, uri)
            withTimeout(10_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    current.setOnPreparedListener { if (continuation.isActive) continuation.resume(Unit) }
                    current.setOnErrorListener { _, _, _ ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("This sound cannot be played")
                            )
                        }
                        true
                    }
                    continuation.invokeOnCancellation { stop() }
                    current.prepareAsync()
                }
            }
            if (requestGeneration != generation) return@withContext
            current.setOnCompletionListener { if (player === it) stop() }
            current.setOnErrorListener { failed, _, _ ->
                if (player === failed) stop()
                true
            }
            current.start()
        } catch (failure: Exception) {
            stop()
            throw failure
        }
    }

    /** Called on Android's main thread from the picker lifecycle and audio-focus callback. */
    fun stop() {
        generation++
        player?.release()
        player = null
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
    }
}
