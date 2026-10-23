package com.akib.mycompanion.presentation

import app.cash.turbine.test
import com.akib.mycompanion.domain.usecase.AddToPlaylistUseCase
import com.akib.mycompanion.domain.usecase.CreatePlaylistUseCase
import com.akib.mycompanion.domain.usecase.LoadVideoUseCase
import com.akib.mycompanion.fakes.FakePlayback
import com.akib.mycompanion.fakes.FakePlaylistRepository
import com.akib.mycompanion.fakes.FakeVideoSource
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.fakes.video
import com.akib.mycompanion.presentation.player.LinkStatus
import com.akib.mycompanion.presentation.player.VideoActionsEvent
import com.akib.mycompanion.presentation.player.VideoActionsViewModel
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VideoActionsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val song = video("dQw4w9WgXcQ", "Never Gonna Give You Up")
    private val link = "https://youtu.be/dQw4w9WgXcQ?si=abc"
    private val source = FakeVideoSource(mutableMapOf(song.id to song))
    private val playlists = FakePlaylistRepository()
    private val playback = FakePlayback()

    private val viewModel by lazy {
        VideoActionsViewModel(
            playlists = playlists,
            loadVideo = LoadVideoUseCase(source),
            playback = playback,
            addToPlaylist = AddToPlaylistUseCase(playlists),
            createPlaylist = CreatePlaylistUseCase(playlists),
        )
    }

    @Test
    fun `a shared link loads the video and offers actions`() = runTest {
        viewModel.uiState.test {
            viewModel.onSubmit(link)

            assertThat(expectMostRecentItem().status).isEqualTo(LinkStatus.Ready(song))
        }
    }

    @Test
    fun `non-YouTube text and failures show an error`() = runTest {
        viewModel.uiState.test {
            viewModel.onSubmit("just some text")
            assertThat((expectMostRecentItem().status as LinkStatus.Error).message).contains("not a YouTube")

            source.failure = IOException("No internet")
            viewModel.onSubmit(link)
            assertThat((expectMostRecentItem().status as LinkStatus.Error).message).contains("No internet")

            viewModel.onLinkChange("https://youtu.be/")
            assertThat(expectMostRecentItem().status).isEqualTo(LinkStatus.Idle)
        }
    }

    @Test
    fun `play now starts playback and asks to open the player`() = runTest {
        viewModel.onSubmit(link)

        viewModel.events.test {
            viewModel.onPlayNow()
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Done(openPlayer = true))
        }
        assertThat(playback.calls).containsExactly("playNow:${song.id}@0 shuffle=false")
        assertThat(viewModel.uiState.value.status).isEqualTo(LinkStatus.Idle)
    }

    @Test
    fun `add to queue queues it and closes`() = runTest {
        viewModel.onSubmit(link)

        viewModel.events.test {
            viewModel.onAddToQueue()
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Message("Added to queue"))
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Done(openPlayer = false))
        }
        assertThat(playback.calls).containsExactly("addToQueue:${song.id}")
    }

    @Test
    fun `add to an existing playlist, then again`() = runTest {
        val focus = playlists.create("Focus")
        viewModel.onSubmit(link)

        viewModel.events.test {
            viewModel.onAddToPlaylist(focus.id)
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Message("Added to Focus"))
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Done(openPlayer = false))

            viewModel.onSubmit(link)
            viewModel.onAddToPlaylist(focus.id)
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Message("Already in Focus"))
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Done(openPlayer = false))
        }
        assertThat(playlists.items(focus.id).map { it.video }).containsExactly(song)
    }

    @Test
    fun `create a new playlist and add in one go`() = runTest {
        viewModel.onSubmit(link)

        viewModel.events.test {
            viewModel.onCreatePlaylistAndAdd("  Night drive ")
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Message("Added to Night drive"))
            assertThat(awaitItem()).isEqualTo(VideoActionsEvent.Done(openPlayer = false))
        }
        val created = playlists.findByName("Night drive")!!
        assertThat(playlists.items(created.id).map { it.video }).containsExactly(song)
    }

    @Test
    fun `a taken playlist name is explained and nothing is added`() = runTest {
        playlists.create("Focus")
        viewModel.onSubmit(link)

        viewModel.uiState.test {
            viewModel.onCreatePlaylistAndAdd("focus")
            val state = expectMostRecentItem()
            assertThat(state.newPlaylistError).isEqualTo("You already have \"Focus\"")
            assertThat(state.status).isEqualTo(LinkStatus.Ready(song))

            viewModel.onNewPlaylistNameChange()
            assertThat(expectMostRecentItem().newPlaylistError).isNull()
        }
        assertThat(playlists.allItems).isEmpty()
    }

    @Test
    fun `actions do nothing until a video is ready`() = runTest {
        viewModel.onPlayNow()
        viewModel.onAddToQueue()

        assertThat(playback.calls).isEmpty()
    }

    @Test
    fun `dismiss goes back to the link box`() = runTest {
        viewModel.onSubmit(link)
        viewModel.onDismiss()

        assertThat(viewModel.uiState.value.status).isEqualTo(LinkStatus.Idle)
    }
}
