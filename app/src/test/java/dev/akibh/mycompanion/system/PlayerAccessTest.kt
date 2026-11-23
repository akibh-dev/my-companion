package dev.akibh.mycompanion.system

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.fakes.video
import dev.akibh.mycompanion.player.MediaItems
import dev.akibh.mycompanion.player.PlaybackService
import dev.akibh.mycompanion.player.PlayerSessionCallback
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Audit plan 19 §3: other apps must not read or rewrite the queue, and nothing odd reaches history. */
@RunWith(AndroidJUnit4::class)
class PlayerAccessTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val callback = PlayerSessionCallback(app.packageName)
    private val player = ExoPlayer.Builder(app).build()
    private val session = MediaSession.Builder(app, player).setId("test").setCallback(callback).build()

    @After
    fun release() {
        session.release()
        player.release()
    }

    private fun controller(packageName: String, trusted: Boolean = false) =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(packageName, 1, 1, 0, 0, trusted, Bundle.EMPTY)

    private val queueAndNowPlaying = listOf(
        Player.COMMAND_CHANGE_MEDIA_ITEMS,
        Player.COMMAND_SET_MEDIA_ITEM,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_SET_PLAYLIST_METADATA,
    )

    @Test
    fun `this app gets full control`() {
        val result = callback.onConnect(session, controller(app.packageName))

        assertThat(result.isAccepted).isTrue()
        queueAndNowPlaying.forEach { assertThat(result.availablePlayerCommands.contains(it)).isTrue() }
    }

    @Test
    fun `controllers Android trusts (System UI, Bluetooth) get full control`() {
        val result = callback.onConnect(session, controller("com.android.systemui", trusted = true))

        queueAndNowPlaying.forEach { assertThat(result.availablePlayerCommands.contains(it)).isTrue() }
    }

    @Test
    fun `any other app can only play, pause and seek`() {
        val result = callback.onConnect(session, controller("com.example.snoop"))

        assertThat(result.isAccepted).isTrue()
        assertThat(result.availablePlayerCommands).isEqualTo(PlayerSessionCallback.LIMITED_COMMANDS)
        assertThat(result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE)).isTrue()
        assertThat(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isTrue()
        queueAndNowPlaying.forEach { assertThat(result.availablePlayerCommands.contains(it)).isFalse() }
        assertThat(result.availableSessionCommands.commands).isEmpty()
    }

    @Test
    fun `items with a made-up id are dropped and foreign artwork is removed`() {
        val good = MediaItems.from(video("dQw4w9WgXcQ"))
        val foreignArt = MediaItem.Builder()
            .setMediaId("9bZkp7q19f0")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(
                    "x"
                ).setArtworkUri(Uri.parse("https://evil.example/track.png")).build(),
            )
            .build()
        val badIds = listOf("", "abc", "dQw4w9WgXcQ/../x", "content://contacts/1", "dQw4w9WgXcQQ").map {
            MediaItem.Builder().setMediaId(it).build()
        }

        val added = callback.onAddMediaItems(
            session,
            controller(app.packageName),
            (listOf(good, foreignArt) + badIds).toMutableList(),
        ).get()

        assertThat(added.map { it.mediaId }).containsExactly("dQw4w9WgXcQ", "9bZkp7q19f0").inOrder()
        assertThat(added[0].mediaMetadata.artworkUri.toString()).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq.jpg")
        assertThat(added[1].mediaMetadata.artworkUri).isNull()
        assertThat(added[1].mediaMetadata.title.toString()).isEqualTo("x")
        assertThat(added.map { it.localConfiguration!!.uri }).containsExactly(
            MediaItems.uriFor("dQw4w9WgXcQ"),
            MediaItems.uriFor("9bZkp7q19f0"),
        ).inOrder()
    }

    @Test
    fun `only https thumbnails from i_ytimg_com count as artwork`() {
        assertThat(MediaItems.isAllowedArtwork(Uri.parse("https://i.ytimg.com/vi/x/hq.jpg"))).isTrue()
        assertThat(MediaItems.isAllowedArtwork(null)).isTrue()
        assertThat(MediaItems.isAllowedArtwork(Uri.parse("http://i.ytimg.com/vi/x/hq.jpg"))).isFalse()
        assertThat(MediaItems.isAllowedArtwork(Uri.parse("https://i.ytimg.com.evil.example/x"))).isFalse()
        assertThat(MediaItems.isAllowedArtwork(Uri.parse("https://i.ytimg.com@evil.example/x"))).isFalse()
        assertThat(MediaItems.isAllowedArtwork(Uri.parse("content://media/external/images/1"))).isFalse()
    }

    @Test
    fun `setting a queue keeps the start on the same item after bad ones are dropped`() {
        val items = mutableListOf(
            MediaItem.Builder().setMediaId("bad").build(),
            MediaItems.from(video("dQw4w9WgXcQ")),
            MediaItem.Builder().setMediaId("also bad").build(),
            MediaItems.from(video("9bZkp7q19f0")),
        )
        val own = controller(app.packageName)

        val atSecondGood = callback.onSetMediaItems(session, own, items, 3, 5_000).get()
        assertThat(atSecondGood.mediaItems.map { it.mediaId }).containsExactly("dQw4w9WgXcQ", "9bZkp7q19f0").inOrder()
        assertThat(atSecondGood.startIndex).isEqualTo(1)
        assertThat(atSecondGood.startPositionMs).isEqualTo(5_000)

        val atDropped = callback.onSetMediaItems(session, own, items, 2, 5_000).get()
        assertThat(atDropped.startIndex).isEqualTo(0)
        assertThat(atDropped.startPositionMs).isEqualTo(C.TIME_UNSET)

        val unset = callback.onSetMediaItems(session, own, items, C.INDEX_UNSET, C.TIME_UNSET).get()
        assertThat(unset.startIndex).isEqualTo(C.INDEX_UNSET)
    }

    @Test
    fun `stream requests never follow a redirect from https to http`() {
        val source = PlaybackService.httpDataSourceFactory().createDataSource()

        val field = DefaultHttpDataSource::class.java.getDeclaredField("allowCrossProtocolRedirects")
        field.isAccessible = true
        assertThat(field.getBoolean(source)).isFalse()
    }
}
