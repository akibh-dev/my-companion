package com.akib.mycompanion.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.domain.repository.HistoryVisit
import com.akib.mycompanion.domain.repository.PlaybackHistoryRepository
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.player.MediaItems
import com.akib.mycompanion.player.PlaybackHistoryRecorder
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackHistoryRecorderTest {
    private class History : PlaybackHistoryRepository {
        val entries = mutableListOf<PlaybackHistoryEntry>()
        var visits = 0
        override fun observe() = flowOf(entries.toList())
        override fun beginVisit(
            videoId: String
        ): HistoryVisit {
            visits++
            return HistoryVisit(videoId, 0, visits.toLong())
        }
        override suspend fun record(visit: HistoryVisit, entry: PlaybackHistoryEntry) {
            entries += entry
        }
        override suspend fun remove(videoId: String) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun `service recorder ignores buffering and seeks, records five real playing seconds`() = runTest {
        val history = History()
        val player = mockk<Player>(relaxed = true)
        val item = MediaItems.from(video("a"))
        var playing = false
        var position = 0L
        every { player.currentMediaItem } returns item
        every { player.isPlaying } answers { playing }
        every { player.currentPosition } answers { position }
        every { player.duration } returns 60_000
        every { player.playbackState } returns Player.STATE_READY
        val recorder = PlaybackHistoryRecorder(player, history, backgroundScope, backgroundScope)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        position = 40_000 // Seeking to 40s while paused is not 40s listened.
        recorder.onEvents(player, mockk(relaxed = true))
        runCurrent()
        assertThat(history.entries).isEmpty()
        playing = true
        recorder.onEvents(player, mockk(relaxed = true))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        position = 45_000
        recorder.onEvents(player, mockk(relaxed = true))
        runCurrent()
        assertThat(history.entries).hasSize(1)
        assertThat(history.entries.single().positionMs).isEqualTo(45_000)
        recorder.close()
        runCurrent()
    }

    @Test fun `auto advancing a completed short item records it before the next visit`() = runTest {
        val history = History()
        val player = mockk<Player>(relaxed = true)
        var current = MediaItems.from(video("short", seconds = 3))
        every { player.currentMediaItem } answers { current }
        every { player.isPlaying } returns true
        every { player.currentPosition } returns 1_000
        every { player.duration } returns 3_000
        every { player.playbackState } returns Player.STATE_READY
        val recorder = PlaybackHistoryRecorder(player, history, backgroundScope, backgroundScope)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        current = MediaItem.Builder().setMediaId("next").build()
        recorder.onMediaItemTransition(current, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        runCurrent()
        assertThat(history.entries.map { it.video.id }).containsExactly("short")
        recorder.close()
        runCurrent()
    }

    @Test fun `seeking forward never counts as listening and an explicit restart starts a new visit`() = runTest {
        val history = History()
        val player = mockk<Player>(relaxed = true)
        val item = MediaItems.from(video("a"))
        var playing = false
        var position = 0L
        every { player.currentMediaItem } returns item
        every { player.isPlaying } answers { playing }
        every { player.currentPosition } answers { position }
        every { player.duration } returns 60_000
        every { player.playbackState } returns Player.STATE_READY
        val recorder = PlaybackHistoryRecorder(player, history, backgroundScope, backgroundScope)
        fun at(ms: Long) = Player.PositionInfo(null, 0, item, null, 0, ms, ms, -1, -1)
        position = 40_000
        recorder.onPositionDiscontinuity(at(0), at(position), Player.DISCONTINUITY_REASON_SEEK)
        recorder.onEvents(player, mockk(relaxed = true))
        runCurrent()
        assertThat(history.entries).isEmpty()
        assertThat(history.visits).isEqualTo(1)
        playing = true
        recorder.onEvents(player, mockk(relaxed = true))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        position = 0
        recorder.onPositionDiscontinuity(at(41_000), at(0), Player.DISCONTINUITY_REASON_SEEK)
        recorder.onEvents(player, mockk(relaxed = true))
        assertThat(history.visits).isEqualTo(2)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        position = 4_000
        recorder.onEvents(player, mockk(relaxed = true))
        runCurrent()
        assertThat(history.entries).isEmpty()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        position = 5_000
        recorder.onEvents(player, mockk(relaxed = true))
        runCurrent()
        assertThat(history.entries.single().positionMs).isEqualTo(5_000)
        recorder.close()
        runCurrent()
    }
}
