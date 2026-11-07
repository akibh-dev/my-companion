package com.akib.mycompanion

import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.repository.RoomPlaybackHistoryRepository
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.player.MediaItems
import com.akib.mycompanion.player.PlaybackHistoryRecorder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real local playback and Room persistence: no YouTube network or fabricated player callbacks. */
@RunWith(AndroidJUnit4::class)
class PlaybackHistoryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun completedLocalAudioIsStoredByTheServiceRecorder() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val history = RoomPlaybackHistoryRepository(database.playbackHistoryDao())
        val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val persistence = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var player: ExoPlayer? = null
        var recorder: PlaybackHistoryRecorder? = null
        try {
            withContext(Dispatchers.Main.immediate) {
                val actual = ExoPlayer.Builder(context).build()
                player = actual
                recorder = PlaybackHistoryRecorder(actual, history, playerScope, persistence)
                val fixture = MediaItems.from(Video("local-history-fixture", "Local history fixture"))
                    .buildUpon().setUri(Uri.parse("android.resource://${context.packageName}/${R.raw.reminder_chime}"))
                    .build()
                actual.setMediaItem(fixture)
                actual.prepare()
                actual.play()
            }
            val entries = withTimeout(20_000) { history.observe().first { it.isNotEmpty() } }
            assertThat(entries.single().video.id).isEqualTo("local-history-fixture")
            assertThat(entries.single().positionMs).isGreaterThan(0L)
            assertThat(entries.single().durationMs).isGreaterThan(0L)
            withContext(Dispatchers.Main.immediate) {
                assertThat(requireNotNull(player).playbackState).isEqualTo(Player.STATE_ENDED)
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                recorder?.close()
                player?.release()
            }
            playerScope.cancel()
            persistence.cancel()
            database.close()
        }
    }
}
