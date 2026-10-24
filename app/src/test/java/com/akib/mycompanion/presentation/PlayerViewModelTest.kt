package com.akib.mycompanion.presentation

import app.cash.turbine.test
import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.usecase.CreatePlaylistUseCase
import com.akib.mycompanion.domain.usecase.RenamePlaylistUseCase
import com.akib.mycompanion.domain.usecase.SaveQueueAsPlaylistUseCase
import com.akib.mycompanion.fakes.FakePlayback
import com.akib.mycompanion.fakes.FakePlaylistRepository
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.presentation.player.PlayerEvent
import com.akib.mycompanion.presentation.player.PlayerSection
import com.akib.mycompanion.presentation.player.PlayerViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val a = video("aaaaaaaaaaa", "First")
    private val b = video("bbbbbbbbbbb", "Second")
    private val playlists = FakePlaylistRepository()
    private val playback = FakePlayback()
    private val create = CreatePlaylistUseCase(playlists)

    private val viewModel by lazy {
        PlayerViewModel(
            playback = playback,
            playlists = playlists,
            createPlaylist = create,
            renamePlaylist = RenamePlaylistUseCase(playlists),
            saveQueueAsPlaylist = SaveQueueAsPlaylistUseCase(create, playlists),
        )
    }

    @Test
    fun `connects to the player and mirrors its state`() = runTest {
        viewModel.uiState.test {
            playback.set(PlaybackState(connected = true, queue = listOf(a, b), currentIndex = 1, isPlaying = true))

            val state = expectMostRecentItem()
            assertThat(state.playback.current).isEqualTo(b)
            assertThat(state.section).isEqualTo(PlayerSection.QUEUE)
        }
        assertThat(playback.calls.first()).isEqualTo("connect")
    }

    @Test
    fun `transport buttons reach the player`() {
        viewModel.onTogglePlay()
        viewModel.onNext()
        viewModel.onPrevious()
        viewModel.onSeek(30_000)
        viewModel.onSkipTo(2)
        viewModel.onRemoveFromQueue(1)
        viewModel.onCycleRepeat()
        viewModel.onToggleShuffle()

        assertThat(playback.calls.drop(1))
            .containsExactly("toggle", "next", "previous", "seek:30000", "skipTo:2", "removeAt:1", "repeat", "shuffle")
            .inOrder()
    }

    @Test
    fun `opening a playlist shows its items and closing goes back`() = runTest {
        val mix = playlists.create("Mix")
        playlists.addItem(mix.id, a)
        playlists.addItem(mix.id, b)

        viewModel.uiState.test {
            viewModel.onOpenPlaylist(mix.id)
            val open = expectMostRecentItem()
            assertThat(open.section).isEqualTo(PlayerSection.PLAYLISTS)
            assertThat(open.openPlaylist!!.items.map { it.video }).containsExactly(a, b).inOrder()
            assertThat(open.openPlaylist!!.playlist.itemCount).isEqualTo(2)

            viewModel.onClosePlaylist()
            assertThat(expectMostRecentItem().openPlaylist).isNull()
        }
    }

    @Test
    fun `playing a playlist from an item, or shuffled`() = runTest {
        val mix = playlists.create("Mix")
        playlists.addItem(mix.id, a)
        playlists.addItem(mix.id, b)
        viewModel.onOpenPlaylist(mix.id)

        viewModel.onPlayPlaylist(mix.id, startIndex = 1)
        viewModel.onPlayPlaylist(mix.id, shuffle = true)

        assertThat(playback.calls).containsAtLeast(
            "playNow:${a.id},${b.id}@1 shuffle=false",
            "playNow:${a.id},${b.id}@0 shuffle=true",
        )
        assertThat(viewModel.uiState.value.section).isEqualTo(PlayerSection.QUEUE)
    }

    @Test
    fun `an empty playlist explains instead of playing`() = runTest {
        val empty = playlists.create("Empty")

        viewModel.events.test {
            viewModel.onPlayPlaylist(empty.id)
            assertThat((awaitItem() as PlayerEvent.Message).text).contains("empty")
        }
        assertThat(playback.calls).containsExactly("connect")
    }

    @Test
    fun `create, rename and delete playlists`() = runTest {
        viewModel.events.test {
            viewModel.onCreatePlaylist("Gym")
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("Created Gym"))

            viewModel.onCreatePlaylist("gym")
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("You already have \"Gym\""))

            val gym = playlists.findByName("Gym")!!
            viewModel.onRenamePlaylist(gym.id, "Workout")
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("Renamed to Workout"))

            viewModel.onDeletePlaylist(gym.copy(name = "Workout"))
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("Deleted Workout"))
        }
        assertThat(playlists.findByName("Workout")).isNull()
    }

    @Test
    fun `removing an item from a playlist`() = runTest {
        val mix = playlists.create("Mix")
        val item = playlists.addItem(mix.id, a)

        viewModel.events.test {
            viewModel.onRemoveFromPlaylist(item)
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("Removed First"))
        }
        assertThat(playlists.items(mix.id)).isEmpty()
    }

    @Test
    fun `the queue can be saved as a playlist`() = runTest {
        playback.set(PlaybackState(queue = listOf(a, b, a), currentIndex = 0))

        viewModel.events.test {
            viewModel.onSaveQueue("Tonight")
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("Saved 2 to Tonight"))
        }
        val saved = playlists.findByName("Tonight")!!
        assertThat(playlists.items(saved.id).map { it.video }).containsExactly(a, b).inOrder()
    }

    @Test
    fun `an empty queue can't be saved`() = runTest {
        viewModel.events.test {
            viewModel.onSaveQueue("Nothing")
            assertThat(awaitItem()).isEqualTo(PlayerEvent.Message("The queue is empty"))
        }
        assertThat(playlists.findByName("Nothing")).isNull()
    }

    @Test
    fun `switching to Up next closes an open playlist`() = runTest {
        val mix = playlists.create("Mix")

        viewModel.uiState.test {
            viewModel.onOpenPlaylist(mix.id)
            viewModel.onSectionChange(PlayerSection.QUEUE)

            val state = expectMostRecentItem()
            assertThat(state.section).isEqualTo(PlayerSection.QUEUE)
            assertThat(state.openPlaylist).isNull()
        }
    }
}
