package com.akib.mycompanion.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.akib.mycompanion.domain.history.ListeningVisit
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.repository.HistoryVisit
import com.akib.mycompanion.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** All player snapshots happen on the player's main looper; database writes outlive this service. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackHistoryRecorder(
    private val player: Player,
    private val history: PlaybackHistoryRepository,
    private val playerScope: CoroutineScope,
    persistenceScope: CoroutineScope,
) : Player.Listener {
    private data class Write(val visit: HistoryVisit, val entry: PlaybackHistoryEntry)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private var item: MediaItem? = null
    private var visit: HistoryVisit? = null
    private var listening = ListeningVisit(SystemClock.elapsedRealtime())
    private var positionMs = 0L
    private var durationMs = 0L
    private var ticker: Job? = null

    init {
        persistenceScope.launch {
            for (write in writes) {
                try {
                    history.record(write.visit, write.entry)
                } catch (
                    cancelled: CancellationException
                ) {
                    throw cancelled
                } catch (
                    _: Exception
                ) {
                    Log.w("PlaybackHistory", "Could not save listening history")
                }
            }
        }
        player.addListener(this)
        startVisit(player.currentMediaItem)
        update(force = false)
        ticker = playerScope.launch {
            while (true) {
                delay(1_000)
                update(force = false)
            }
        }
    }

    private fun startVisit(next: MediaItem?) {
        item = next
        visit = next?.mediaId?.takeIf(String::isNotBlank)?.let(history::beginVisit)
        listening = ListeningVisit(SystemClock.elapsedRealtime())
        positionMs = 0
        durationMs = 0
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // The discontinuity callback already captured the previous item's last position.
        persist(
            SystemClock.elapsedRealtime(),
            false,
            completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            force = true
        )
        startVisit(mediaItem)
        update(force = false)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (oldPosition.mediaItem?.mediaId == item?.mediaId) {
            positionMs = oldPosition.positionMs.coerceAtLeast(0)
            persist(SystemClock.elapsedRealtime(), false, completed = false, force = true)
            // A deliberate restart is a new listening visit, unlike pause/resume or progress.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && newPosition.positionMs == 0L &&
                oldPosition.positionMs > 0 && newPosition.mediaItem?.mediaId == item?.mediaId
            ) {
                startVisit(newPosition.mediaItem)
            }
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        update(force = !player.isPlaying)
    }

    private fun update(force: Boolean) {
        if (item?.mediaId != player.currentMediaItem?.mediaId) return
        positionMs = player.currentPosition.coerceAtLeast(0)
        durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: (item?.let(MediaItems::toVideo)?.durationSeconds ?: 0) * 1_000
        persist(SystemClock.elapsedRealtime(), player.isPlaying, player.playbackState == Player.STATE_ENDED, force)
    }

    private fun persist(now: Long, playing: Boolean, completed: Boolean, force: Boolean) {
        if (!listening.update(now, playing, durationMs, completed, force)) return
        val current = item ?: return
        val token = visit ?: return
        writes.trySend(
            Write(
                token,
                PlaybackHistoryEntry(
                    MediaItems.toVideo(current),
                    System.currentTimeMillis(),
                    positionMs.coerceIn(0, durationMs.coerceAtLeast(positionMs)),
                    durationMs,
                )
            )
        )
    }

    fun close() {
        update(force = true)
        player.removeListener(this)
        ticker?.cancel()
        writes.close()
    }
}
