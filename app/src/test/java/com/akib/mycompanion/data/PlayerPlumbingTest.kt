package com.akib.mycompanion.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.youtube.AudioStreamSource
import com.akib.mycompanion.data.youtube.CachingStreamResolver
import com.akib.mycompanion.data.youtube.ResolvedStream
import com.akib.mycompanion.domain.model.RepeatMode
import com.akib.mycompanion.fakes.FakeClock
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.player.MediaControllerPlayback
import com.akib.mycompanion.player.MediaItems
import com.akib.mycompanion.player.RetryExpiredStream
import com.akib.mycompanion.player.YouTubeSpecResolver
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith

/** The glue between our queue and Media3 (needs android.net.Uri, hence Robolectric). */
@RunWith(AndroidJUnit4::class)
class PlayerPlumbingTest {

    private val song = video("dQw4w9WgXcQ", "Never Gonna Give You Up", seconds = 213)

    @Test
    fun `a video becomes a media item and back`() {
        val item = MediaItems.from(song)

        assertThat(item.mediaId).isEqualTo(song.id)
        assertThat(item.localConfiguration!!.uri.toString()).isEqualTo("ytaudio:dQw4w9WgXcQ")
        assertThat(item.mediaMetadata.title.toString()).isEqualTo(song.title)
        assertThat(item.mediaMetadata.artist.toString()).isEqualTo("Channel")
        assertThat(MediaItems.toVideo(item)).isEqualTo(song)
    }

    @Test
    fun `items arriving from a controller get their playable URI back`() {
        val stripped = MediaItem.Builder().setMediaId(song.id).build()

        val restored = MediaItems.withPlayableUris(listOf(stripped)).single()

        assertThat(restored.localConfiguration!!.uri).isEqualTo(MediaItems.uriFor(song.id))
    }

    @Test
    fun `only ytaudio URIs carry a video id`() {
        assertThat(MediaItems.videoId(Uri.parse("ytaudio:abc"))).isEqualTo("abc")
        assertThat(MediaItems.videoId(Uri.parse("https://youtu.be/abc"))).isNull()
        assertThat(MediaItems.videoId(null)).isNull()
    }

    @Test
    fun `the data spec is swapped for the real stream with its headers`() {
        val source = object : AudioStreamSource {
            override fun audioStream(videoId: String) = ResolvedStream(
                url = "https://rr.googlevideo.com/videoplayback?id=$videoId",
                headers = mapOf("User-Agent" to "yt"),
                expiresAtMillis = Long.MAX_VALUE,
            )
        }
        val resolver = YouTubeSpecResolver(CachingStreamResolver(source, FakeClock()))
        val spec = DataSpec.Builder()
            .setUri(MediaItems.uriFor("abc"))
            .setPosition(1_000)
            .setHttpRequestHeaders(mapOf("Range-Hint" to "x"))
            .build()

        val resolved = resolver.resolveDataSpec(spec)

        assertThat(resolved.uri.toString()).isEqualTo("https://rr.googlevideo.com/videoplayback?id=abc")
        assertThat(resolved.position).isEqualTo(1_000)
        assertThat(resolved.httpRequestHeaders).containsExactly("Range-Hint", "x", "User-Agent", "yt")
    }

    @Test
    fun `other URIs pass through untouched`() {
        val resolver = YouTubeSpecResolver(CachingStreamResolver(mockk(), FakeClock()))
        val spec = DataSpec(Uri.parse("https://example.com/a.mp3"))

        assertThat(resolver.resolveDataSpec(spec)).isSameInstanceAs(spec)
    }

    @Test
    fun `an expired stream is retried once with a fresh URL`() {
        val item = MediaItems.from(song)
        val player = mockk<Player>(relaxed = true) { every { currentMediaItem } returns item }
        val cache = mockk<CachingStreamResolver>(relaxed = true)
        val retry = RetryExpiredStream(player, cache)
        val error = PlaybackException("403", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        retry.onPlayerError(error)
        retry.onPlayerError(error)

        verify(exactly = 1) { cache.invalidate(song.id) }
        verify(exactly = 1) { player.prepare() }

        retry.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        retry.onPlayerError(error)
        verify(exactly = 2) { player.prepare() }
    }

    @Test
    fun `the player is mirrored into playback state`() {
        val other = video("bbbbbbbbbbb", "Second")
        val player = mockk<Player>(relaxed = true) {
            every { mediaItemCount } returns 2
            every { getMediaItemAt(0) } returns MediaItems.from(song)
            every { getMediaItemAt(1) } returns MediaItems.from(other)
            every { currentMediaItemIndex } returns 1
            every { currentMediaItem } returns MediaItems.from(other)
            every { isPlaying } returns true
            every { playbackState } returns Player.STATE_READY
            every { currentPosition } returns 42_000
            every { duration } returns C.TIME_UNSET
            every { repeatMode } returns Player.REPEAT_MODE_ONE
            every { shuffleModeEnabled } returns true
            every { playerError } returns null
        }

        val state = MediaControllerPlayback.snapshot(player)

        assertThat(state.queue).containsExactly(song, other).inOrder()
        assertThat(state.current).isEqualTo(other)
        assertThat(state.isPlaying).isTrue()
        assertThat(state.positionMs).isEqualTo(42_000)
        // Duration not known yet: fall back to the length YouTube reported.
        assertThat(state.durationMs).isEqualTo(other.durationSeconds * 1000)
        assertThat(state.repeat).isEqualTo(RepeatMode.ONE)
        assertThat(state.shuffle).isTrue()
        assertThat(state.error).isNull()
    }

    @Test
    fun `an empty player has no current item`() {
        val player = mockk<Player>(relaxed = true) {
            every { mediaItemCount } returns 0
            every { duration } returns C.TIME_UNSET
            every { currentMediaItem } returns null
            every { playerError } returns null
        }

        val state = MediaControllerPlayback.snapshot(player)

        assertThat(state.currentIndex).isEqualTo(-1)
        assertThat(state.current).isNull()
    }
}
