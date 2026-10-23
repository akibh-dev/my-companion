package com.akib.mycompanion.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.model.RepeatMode
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.domain.port.Playback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Talks to [PlaybackService] through a Media3 MediaController (the same channel the notification
 * and lock screen use), and mirrors the player into [state]. Must be used from the main thread.
 */
@Singleton
class MediaControllerPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
) : Playback {

    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    override fun connect() = withController { }

    override fun playNow(videos: List<Video>, startIndex: Int, shuffle: Boolean) = withController { player ->
        if (videos.isEmpty()) return@withController
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(videos.map(MediaItems::from), startIndex.coerceIn(0, videos.lastIndex), 0)
        player.prepare()
        player.play()
    }

    override fun addToQueue(video: Video) = withController { player ->
        player.addMediaItem(MediaItems.from(video))
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    override fun togglePlayPause() = withController { player ->
        when {
            player.isPlaying -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekToDefaultPosition(0)
                player.play()
            }
            else -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    override fun next() = withController { it.seekToNextMediaItem() }

    /** Like most players: restart the song unless we're in its first few seconds. */
    override fun previous() = withController { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    override fun skipTo(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    override fun removeAt(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    override fun cycleRepeat() = withController { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let {
            action(it)
            return
        }
        pending += action
        if (connecting) return
        connecting = true

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    pending.clear()
                    mutableState.value = mutableState.value.copy(error = "Couldn't start the player")
                    return@addListener
                }
                controller = connected
                connected.addListener(listener)
                publish(connected)
                pending.toList().forEach { it(connected) }
                pending.clear()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun publish(player: Player) {
        mutableState.value = snapshot(player)
        if (player.isPlaying) startTicker(player) else ticker?.cancel()
    }

    /** Position isn't evented; refresh it twice a second while playing. */
    private fun startTicker(player: Player) {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && player.isPlaying) {
                mutableState.value = mutableState.value.copy(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: mutableState.value.durationMs,
                )
                delay(TICK_MS)
            }
        }
    }

    companion object {
        private const val TICK_MS = 500L

        fun snapshot(player: Player): PlaybackState = PlaybackState(
            connected = true,
            queue = (0 until player.mediaItemCount).map { MediaItems.toVideo(player.getMediaItemAt(it)) },
            currentIndex = if (player.mediaItemCount == 0) -1 else player.currentMediaItemIndex,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }
                ?: ((player.currentMediaItem?.let(MediaItems::toVideo)?.durationSeconds ?: 0) * 1000),
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            shuffle = player.shuffleModeEnabled,
            error = player.playerError?.let { "Couldn't play this: ${it.errorCodeName}" },
        )
    }
}
