package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.usecase.AddToPlaylistResult
import com.akib.mycompanion.domain.usecase.AddToPlaylistUseCase
import com.akib.mycompanion.domain.usecase.CreatePlaylistUseCase
import com.akib.mycompanion.domain.usecase.LoadVideoResult
import com.akib.mycompanion.domain.usecase.LoadVideoUseCase
import com.akib.mycompanion.domain.usecase.PlaylistNameResult
import com.akib.mycompanion.domain.usecase.RenamePlaylistUseCase
import com.akib.mycompanion.domain.usecase.SaveQueueAsPlaylistUseCase
import com.akib.mycompanion.fakes.FakePlaylistRepository
import com.akib.mycompanion.fakes.FakeVideoSource
import com.akib.mycompanion.fakes.video
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VideoUseCasesTest {

    private val song = video("dQw4w9WgXcQ", "Never Gonna Give You Up")
    private val source = FakeVideoSource(mutableMapOf(song.id to song))
    private val playlists = FakePlaylistRepository()
    private val load = LoadVideoUseCase(source)
    private val create = CreatePlaylistUseCase(playlists)
    private val rename = RenamePlaylistUseCase(playlists)
    private val add = AddToPlaylistUseCase(playlists)
    private val saveQueue = SaveQueueAsPlaylistUseCase(create, playlists)

    // ---- Loading a link ----

    @Test
    fun `a shared YouTube link loads the video`() = runTest {
        assertThat(load("Listen https://youtu.be/dQw4w9WgXcQ?si=x")).isEqualTo(LoadVideoResult.Loaded(song))
    }

    @Test
    fun `other text is not YouTube`() = runTest {
        assertThat(load("https://example.com/song.mp3")).isEqualTo(LoadVideoResult.NotYouTube)
    }

    @Test
    fun `network problems are reported with the reason`() = runTest {
        source.failure = IOException("Unable to resolve host")

        assertThat(load("https://youtu.be/dQw4w9WgXcQ"))
            .isEqualTo(LoadVideoResult.Failed("dQw4w9WgXcQ", "Unable to resolve host"))
    }

    // ---- Playlists ----

    @Test
    fun `creating a playlist cleans the name`() = runTest {
        val result = create("  Morning   walk ")

        assertThat((result as PlaylistNameResult.Ok).playlist.name).isEqualTo("Morning walk")
    }

    @Test
    fun `playlist names must be present and unique ignoring case`() = runTest {
        val first = (create("Focus") as PlaylistNameResult.Ok).playlist

        assertThat(create("   ")).isEqualTo(PlaylistNameResult.BlankName)
        assertThat(create("focus")).isEqualTo(PlaylistNameResult.NameTaken(first))
    }

    @Test
    fun `renaming checks the new name but allows keeping your own`() = runTest {
        val focus = (create("Focus") as PlaylistNameResult.Ok).playlist
        val sleep = (create("Sleep") as PlaylistNameResult.Ok).playlist

        assertThat(rename(focus.id, "Sleep")).isEqualTo(PlaylistNameResult.NameTaken(sleep))
        assertThat(rename(focus.id, " ")).isEqualTo(PlaylistNameResult.BlankName)
        assertThat((rename(focus.id, "FOCUS") as PlaylistNameResult.Ok).playlist.name).isEqualTo("FOCUS")
    }

    @Test
    fun `adding a video appends it once`() = runTest {
        val focus = (create("Focus") as PlaylistNameResult.Ok).playlist

        assertThat(add(focus.id, song)).isInstanceOf(AddToPlaylistResult.Added::class.java)
        assertThat(add(focus.id, song)).isInstanceOf(AddToPlaylistResult.AlreadyThere::class.java)
        assertThat(playlists.items(focus.id).map { it.video }).containsExactly(song)
        assertThat(add(999, song)).isEqualTo(AddToPlaylistResult.NoSuchPlaylist)
    }

    @Test
    fun `saving the queue keeps its order and drops repeats`() = runTest {
        val a = video("aaaaaaaaaaa")
        val b = video("bbbbbbbbbbb")

        val result = saveQueue("Road trip", listOf(a, b, a))

        val playlist = (result as PlaylistNameResult.Ok).playlist
        assertThat(playlists.items(playlist.id).map { it.video.id }).containsExactly(a.id, b.id).inOrder()
    }

    @Test
    fun `saving the queue under a taken name adds nothing`() = runTest {
        create("Road trip")

        assertThat(saveQueue("road trip", listOf(song))).isInstanceOf(PlaylistNameResult.NameTaken::class.java)
        assertThat(playlists.allItems).isEmpty()
    }
}
