package com.akib.mycompanion.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.domain.port.Playback
import com.akib.mycompanion.domain.repository.PlaylistRepository
import com.akib.mycompanion.domain.usecase.AddToPlaylistResult
import com.akib.mycompanion.domain.usecase.AddToPlaylistUseCase
import com.akib.mycompanion.domain.usecase.CreatePlaylistUseCase
import com.akib.mycompanion.domain.usecase.LoadVideoResult
import com.akib.mycompanion.domain.usecase.LoadVideoUseCase
import com.akib.mycompanion.domain.usecase.PlaylistNameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LinkStatus {
    data object Idle : LinkStatus

    data object Loading : LinkStatus

    /** Video found: offer Play now / Add to queue / Add to playlist. */
    data class Ready(val video: Video) : LinkStatus

    data class Error(val message: String) : LinkStatus
}

data class VideoActionsUiState(
    val link: String = "",
    val status: LinkStatus = LinkStatus.Idle,
    val playlists: List<Playlist> = emptyList(),
    val newPlaylistError: String? = null,
)

sealed interface VideoActionsEvent {
    data class Message(val text: String) : VideoActionsEvent

    /** The chosen action is done. [openPlayer] is true after "Play now". */
    data class Done(val openPlayer: Boolean) : VideoActionsEvent
}

/**
 * A YouTube link → what to do with it. Used by the share sheet (link shared from the YouTube app)
 * and by the link box on the Player tab (pasted link).
 */
@HiltViewModel
class VideoActionsViewModel @Inject constructor(
    playlists: PlaylistRepository,
    private val loadVideo: LoadVideoUseCase,
    private val playback: Playback,
    private val addToPlaylist: AddToPlaylistUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
) : ViewModel() {

    private val link = MutableStateFlow("")
    private val status = MutableStateFlow<LinkStatus>(LinkStatus.Idle)
    private val newPlaylistError = MutableStateFlow<String?>(null)
    private val eventChannel = Channel<VideoActionsEvent>(Channel.BUFFERED)
    val events: Flow<VideoActionsEvent> = eventChannel.receiveAsFlow()
    private var loading: Job? = null

    val uiState: StateFlow<VideoActionsUiState> =
        combine(link, status, playlists.observePlaylists(), newPlaylistError) { link, status, lists, error ->
            VideoActionsUiState(link = link, status = status, playlists = lists, newPlaylistError = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VideoActionsUiState())

    fun onLinkChange(text: String) {
        link.value = text
        if (status.value is LinkStatus.Error) status.value = LinkStatus.Idle
    }

    /** Looks the link up. Called with the shared text, or with the link box contents. */
    fun onSubmit(text: String = link.value) {
        link.value = text
        loading?.cancel()
        status.value = LinkStatus.Loading
        loading = viewModelScope.launch {
            status.value = when (val result = loadVideo(text)) {
                is LoadVideoResult.Loaded -> LinkStatus.Ready(result.video)
                LoadVideoResult.NotYouTube -> LinkStatus.Error("That's not a YouTube video link")
                // Already a sentence ("This video is private"), worded by the data layer.
                is LoadVideoResult.Failed -> LinkStatus.Error(result.reason)
            }
        }
    }

    fun onDismiss() {
        loading?.cancel()
        status.value = LinkStatus.Idle
        newPlaylistError.value = null
    }

    fun onPlayNow() {
        val video = readyVideo() ?: return
        playback.playNow(listOf(video))
        finish(openPlayer = true)
    }

    fun onAddToQueue() {
        val video = readyVideo() ?: return
        playback.addToQueue(video)
        viewModelScope.launch { eventChannel.send(VideoActionsEvent.Message("Added to queue")) }
        finish(openPlayer = false)
    }

    fun onAddToPlaylist(playlistId: Long) {
        val video = readyVideo() ?: return
        viewModelScope.launch {
            val message = when (val result = addToPlaylist(playlistId, video)) {
                is AddToPlaylistResult.Added -> "Added to ${result.playlist.name}"
                is AddToPlaylistResult.AlreadyThere -> "Already in ${result.playlist.name}"
                AddToPlaylistResult.NoSuchPlaylist -> "That playlist no longer exists"
            }
            eventChannel.send(VideoActionsEvent.Message(message))
            finish(openPlayer = false)
        }
    }

    fun onCreatePlaylistAndAdd(name: String) {
        if (readyVideo() == null) return
        viewModelScope.launch {
            when (val result = createPlaylist(name)) {
                is PlaylistNameResult.Ok -> {
                    newPlaylistError.value = null
                    onAddToPlaylist(result.playlist.id)
                }
                PlaylistNameResult.BlankName -> newPlaylistError.value = "Give the playlist a name"
                is PlaylistNameResult.NameTaken ->
                    newPlaylistError.value = "You already have \"${result.existing.name}\""
            }
        }
    }

    fun onNewPlaylistNameChange() = newPlaylistError.update { null }

    private fun readyVideo(): Video? = (status.value as? LinkStatus.Ready)?.video

    private fun finish(openPlayer: Boolean) {
        link.value = ""
        status.value = LinkStatus.Idle
        viewModelScope.launch { eventChannel.send(VideoActionsEvent.Done(openPlayer)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
