package com.akib.mycompanion.domain.usecase

import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.domain.port.VideoInfoSource
import com.akib.mycompanion.domain.repository.PlaylistRepository
import com.akib.mycompanion.domain.youtube.YouTubeLinks
import java.io.IOException
import javax.inject.Inject

sealed interface LoadVideoResult {
    data class Loaded(val video: Video) : LoadVideoResult

    data object NotYouTube : LoadVideoResult

    /** The link is fine but YouTube couldn't be reached or refused (private, removed, no network…). */
    data class Failed(val videoId: String, val reason: String) : LoadVideoResult
}

/** Shared text or a pasted link → the video it points to. */
class LoadVideoUseCase @Inject constructor(private val source: VideoInfoSource) {
    suspend operator fun invoke(text: String): LoadVideoResult {
        val id = YouTubeLinks.videoId(text) ?: return LoadVideoResult.NotYouTube
        return try {
            LoadVideoResult.Loaded(source.fetch(id))
        } catch (e: IOException) {
            LoadVideoResult.Failed(id, e.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach YouTube")
        }
    }
}

sealed interface PlaylistNameResult {
    data class Ok(val playlist: Playlist) : PlaylistNameResult

    data object BlankName : PlaylistNameResult

    data class NameTaken(val existing: Playlist) : PlaylistNameResult
}

class CreatePlaylistUseCase @Inject constructor(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(name: String): PlaylistNameResult {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return PlaylistNameResult.BlankName
        playlists.findByName(clean)?.let { return PlaylistNameResult.NameTaken(it) }
        return PlaylistNameResult.Ok(playlists.create(clean))
    }
}

class RenamePlaylistUseCase @Inject constructor(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long, name: String): PlaylistNameResult {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return PlaylistNameResult.BlankName
        playlists.findByName(clean)?.takeIf { it.id != playlistId }?.let { return PlaylistNameResult.NameTaken(it) }
        playlists.rename(playlistId, clean)
        return PlaylistNameResult.Ok(playlists.get(playlistId) ?: Playlist(playlistId, clean))
    }
}

sealed interface AddToPlaylistResult {
    data class Added(val playlist: Playlist) : AddToPlaylistResult

    data class AlreadyThere(val playlist: Playlist) : AddToPlaylistResult

    data object NoSuchPlaylist : AddToPlaylistResult
}

class AddToPlaylistUseCase @Inject constructor(private val playlists: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long, video: Video): AddToPlaylistResult {
        val playlist = playlists.get(playlistId) ?: return AddToPlaylistResult.NoSuchPlaylist
        if (playlists.contains(playlistId, video.id)) return AddToPlaylistResult.AlreadyThere(playlist)
        playlists.addItem(playlistId, video)
        return AddToPlaylistResult.Added(playlist)
    }
}

/** "Save queue as playlist": creates the playlist and fills it in queue order, skipping repeats. */
class SaveQueueAsPlaylistUseCase @Inject constructor(
    private val create: CreatePlaylistUseCase,
    private val playlists: PlaylistRepository,
) {
    suspend operator fun invoke(name: String, queue: List<Video>): PlaylistNameResult {
        val result = create(name)
        if (result is PlaylistNameResult.Ok) {
            queue.distinctBy { it.id }.forEach { playlists.addItem(result.playlist.id, it) }
        }
        return result
    }
}
