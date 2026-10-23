package com.akib.mycompanion.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.RepeatMode
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.presentation.player.LinkStatus
import com.akib.mycompanion.presentation.player.OpenPlaylist
import com.akib.mycompanion.presentation.player.PlayerActions
import com.akib.mycompanion.presentation.player.PlayerScreen
import com.akib.mycompanion.presentation.player.PlayerSection
import com.akib.mycompanion.presentation.player.PlayerUiState
import com.akib.mycompanion.presentation.player.VideoActionsUiState
import com.akib.mycompanion.presentation.player.formatMillis
import com.akib.mycompanion.presentation.player.formatSeconds
import com.akib.mycompanion.presentation.player.playlistSummary
import com.akib.mycompanion.presentation.share.ShareActivity
import com.akib.mycompanion.presentation.share.ShareSheet
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val a = video("aaaaaaaaaaa", "First song", seconds = 185)
    private val b = video("bbbbbbbbbbb", "Second song")
    private val calls = mutableListOf<String>()

    private fun record(call: String) {
        calls += call
    }

    private val actions = object : PlayerActions {
        override fun onLinkChange(text: String) = record("link:$text")

        override fun onPaste() = record("paste")

        override fun onSubmitLink() = record("submit")

        override fun onPlayNow() = record("playNow")

        override fun onAddToQueue() = record("addToQueue")

        override fun onAddToPlaylist(playlistId: Long) = record("addToPlaylist:$playlistId")

        override fun onCreatePlaylistAndAdd(name: String) = record("createAndAdd:$name")

        override fun onNewPlaylistNameChange() = Unit

        override fun onDismissLink() = record("dismiss")

        override fun onTogglePlay() = record("toggle")

        override fun onNext() = record("next")

        override fun onPrevious() = record("previous")

        override fun onSeek(positionMs: Long) = record("seek")

        override fun onCycleRepeat() = record("repeat")

        override fun onToggleShuffle() = record("shuffle")

        override fun onSkipTo(index: Int) = record("skipTo:$index")

        override fun onRemoveFromQueue(index: Int) = record("removeQueue:$index")

        override fun onSaveQueue() = record("saveQueue")

        override fun onSectionChange(section: PlayerSection) = record("section:$section")

        override fun onNewPlaylist() = record("newPlaylist")

        override fun onOpenPlaylist(playlistId: Long) = record("open:$playlistId")

        override fun onClosePlaylist() = record("close")

        override fun onPlayPlaylist(playlistId: Long, startIndex: Int, shuffle: Boolean) =
            record("playPlaylist:$playlistId@$startIndex shuffle=$shuffle")

        override fun onRemoveFromPlaylist(item: PlaylistItem) = record("removeItem:${item.id}")

        override fun onRenamePlaylist(playlist: Playlist) = record("rename:${playlist.id}")

        override fun onDeletePlaylist(playlist: Playlist) = record("delete:${playlist.id}")
    }

    private fun show(state: PlayerUiState = PlayerUiState(), link: VideoActionsUiState = VideoActionsUiState()) =
        compose.setContent { CompanionTheme { PlayerScreen(state = state, link = link, actions = actions) } }

    @Test
    fun `empty player invites sharing or pasting a link`() {
        show()

        compose.onNodeWithText("Open link").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Paste link").performClick()
        compose.onNodeWithText("Nothing in the queue", substring = true).performScrollTo().assertIsDisplayed()

        assertThat(calls).containsExactly("paste")
    }

    @Test
    fun `a typed link can be opened`() {
        show(link = VideoActionsUiState(link = "https://youtu.be/x"))

        compose.onNodeWithText("Open link").assertIsEnabled().performClick()

        assertThat(calls).containsExactly("submit")
    }

    @Test
    fun `a loaded link offers play now, queue and playlists`() {
        val playlists = listOf(Playlist(id = 4, name = "Focus", itemCount = 3))
        show(link = VideoActionsUiState(status = LinkStatus.Ready(a), playlists = playlists))

        compose.onNodeWithText("First song").assertIsDisplayed()
        compose.onNodeWithText("Play now").performClick()
        compose.onNodeWithText("Add to queue").performClick()
        compose.onNodeWithText("Add to playlist").performClick()
        compose.onNodeWithText("Focus").performClick()

        assertThat(calls).containsExactly("playNow", "addToQueue", "addToPlaylist:4").inOrder()
    }

    @Test
    fun `now playing shows controls that reach the player`() {
        val playback = PlaybackState(
            connected = true,
            queue = listOf(a, b),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 65_000,
            durationMs = 185_000,
            repeat = RepeatMode.ALL,
            shuffle = false,
        )
        show(PlayerUiState(playback = playback))

        compose.onNodeWithText("1:05").assertIsDisplayed()
        compose.onNodeWithText("3:05").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.onNodeWithContentDescription("Repeat all").performClick()
        compose.onNodeWithContentDescription("Shuffle off").performClick()

        assertThat(calls).containsExactly("toggle", "next", "previous", "repeat", "shuffle").inOrder()
    }

    @Test
    fun `up next lists the queue, jumps and removes`() {
        show(PlayerUiState(playback = PlaybackState(queue = listOf(a, b), currentIndex = 0)))

        compose.onNodeWithText("2 videos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Second song").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remove Second song from queue").performScrollTo().performClick()
        compose.onNodeWithText("Save as playlist").performScrollTo().performClick()

        assertThat(calls).containsExactly("skipTo:1", "removeQueue:1", "saveQueue").inOrder()
    }

    @Test
    fun `playlists section lists, plays and opens playlists`() {
        val focus = Playlist(id = 4, name = "Focus", itemCount = 2, totalSeconds = 400)
        show(PlayerUiState(section = PlayerSection.PLAYLISTS, playlists = listOf(focus)))

        compose.onNodeWithText("2 videos · 6 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Play Focus").performScrollTo().performClick()
        compose.onNodeWithText("Focus").performScrollTo().performClick()
        compose.onNodeWithText("New playlist").performScrollTo().performClick()
        compose.onNodeWithText("Up next").performClick()

        assertThat(calls).containsExactly(
            "playPlaylist:4@0 shuffle=false",
            "open:4",
            "newPlaylist",
            "section:QUEUE",
        ).inOrder()
    }

    @Test
    fun `an open playlist can be played, shuffled and edited`() {
        val focus = Playlist(id = 4, name = "Focus", itemCount = 2)
        val items = listOf(PlaylistItem(10, 4, a, 0), PlaylistItem(11, 4, b, 1))
        show(PlayerUiState(section = PlayerSection.PLAYLISTS, openPlaylist = OpenPlaylist(focus, items)))

        compose.onNodeWithText("Play all").performScrollTo().performClick()
        compose.onNodeWithText("Shuffle").performScrollTo().performClick()
        compose.onNodeWithText("Second song").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remove First song from playlist").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Playlist options").performScrollTo().performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithContentDescription("Back to playlists").performScrollTo().performClick()

        assertThat(calls).containsExactly(
            "playPlaylist:4@0 shuffle=false",
            "playPlaylist:4@0 shuffle=true",
            "playPlaylist:4@1 shuffle=false",
            "removeItem:10",
            "rename:4",
            "close",
        ).inOrder()
    }

    @Test
    fun `share sheet shows progress, errors and actions`() {
        var state by mutableStateOf(VideoActionsUiState(status = LinkStatus.Loading))
        compose.setContent {
            CompanionTheme {
                ShareSheet(
                    state = state,
                    onPlayNow = { calls += "playNow" },
                    onAddToQueue = { calls += "addToQueue" },
                    onAddToPlaylist = { calls += "addToPlaylist:$it" },
                    onCreatePlaylistAndAdd = { calls += "createAndAdd:$it" },
                    onNewPlaylistNameChange = {},
                    onRetry = { calls += "retry" },
                    onClose = { calls += "close" },
                )
            }
        }

        compose.onNodeWithText("Getting video info…").assertIsDisplayed()

        state = VideoActionsUiState(status = LinkStatus.Error("That's not a YouTube video link"))
        compose.onNodeWithText("That's not a YouTube video link").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        state = VideoActionsUiState(status = LinkStatus.Ready(a))
        compose.onNodeWithText("Play now").performClick()

        assertThat(calls).containsExactly("retry", "playNow").inOrder()
    }

    @Test
    fun `shared text comes from a share or an open-with link`() {
        val share = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "https://youtu.be/x")
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=x"))

        assertThat(ShareActivity.sharedText(share)).isEqualTo("https://youtu.be/x")
        assertThat(ShareActivity.sharedText(view)).isEqualTo("https://www.youtube.com/watch?v=x")
        assertThat(ShareActivity.sharedText(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(ShareActivity.sharedText(null)).isNull()
    }

    @Test
    fun `time and summary formatting`() {
        assertThat(formatSeconds(0)).isEmpty()
        assertThat(formatSeconds(65)).isEqualTo("1:05")
        assertThat(formatSeconds(3_725)).isEqualTo("1:02:05")
        assertThat(formatMillis(0)).isEqualTo("0:00")
        assertThat(playlistSummary(Playlist(name = "x"))).isEqualTo("Empty")
        assertThat(playlistSummary(Playlist(name = "x", itemCount = 1, totalSeconds = 30))).isEqualTo("1 video")
        assertThat(playlistSummary(Playlist(name = "x", itemCount = 20, totalSeconds = 4_500)))
            .isEqualTo("20 videos · 1 h 15 min")
    }
}
