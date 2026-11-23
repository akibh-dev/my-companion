package dev.akibh.mycompanion.presentation.player

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.domain.model.PlaybackHistoryEntry
import dev.akibh.mycompanion.domain.model.PlaybackState
import dev.akibh.mycompanion.domain.model.Playlist
import dev.akibh.mycompanion.domain.model.PlaylistItem
import dev.akibh.mycompanion.domain.port.Playback
import dev.akibh.mycompanion.domain.repository.NoPlaybackHistory
import dev.akibh.mycompanion.domain.repository.PlaybackHistoryRepository
import dev.akibh.mycompanion.domain.repository.PlaylistRepository
import dev.akibh.mycompanion.domain.usecase.CreatePlaylistUseCase
import dev.akibh.mycompanion.domain.usecase.PlaylistNameResult
import dev.akibh.mycompanion.domain.usecase.RenamePlaylistUseCase
import dev.akibh.mycompanion.domain.usecase.SaveQueueAsPlaylistUseCase
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlayerSection { QUEUE, PLAYLISTS, HISTORY }

/** A playlist opened from the Playlists section. */
data class OpenPlaylist(val playlist: Playlist, val items: List<PlaylistItem>)

data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val section: PlayerSection = PlayerSection.QUEUE,
    val playlists: List<Playlist> = emptyList(),
    val openPlaylist: OpenPlaylist? = null,
    val history: List<PlaybackHistoryEntry> = emptyList(),
)

sealed interface PlayerEvent {
    data class Message(val text: String) : PlayerEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: Playback,
    private val playlists: PlaylistRepository,
    private val createPlaylist: CreatePlaylistUseCase,
    private val renamePlaylist: RenamePlaylistUseCase,
    private val saveQueueAsPlaylist: SaveQueueAsPlaylistUseCase,
    private val history: PlaybackHistoryRepository = NoPlaybackHistory,
) : ViewModel() {

    /** Picks where a shuffled playlist starts; tests swap in a seeded one. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    private val section = MutableStateFlow(PlayerSection.QUEUE)
    private val openPlaylistId = MutableStateFlow<Long?>(null)
    private val eventChannel = Channel<PlayerEvent>(Channel.BUFFERED)
    val events: Flow<PlayerEvent> = eventChannel.receiveAsFlow()

    private val openPlaylist: Flow<OpenPlaylist?> = openPlaylistId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(playlists.observePlaylists(), playlists.observeItems(id)) { all, items ->
                all.firstOrNull { it.id == id }?.let { OpenPlaylist(it, items) }
            }
        }
    }

    val uiState: StateFlow<PlayerUiState> =
        combine(
            playback.state,
            section,
            playlists.observePlaylists(),
            openPlaylist,
            history.observe()
        ) { state, section, all, open, recent ->
            PlayerUiState(playback = state, section = section, playlists = all, openPlaylist = open, history = recent)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlayerUiState())

    init {
        playback.connect()
    }

    // ---- Transport controls ----

    fun onTogglePlay() = playback.togglePlayPause()

    fun onNext() = playback.next()

    fun onPrevious() = playback.previous()

    fun onSeek(positionMs: Long) = playback.seekTo(positionMs)

    fun onSkipTo(index: Int) = playback.skipTo(index)

    fun onRemoveFromQueue(index: Int) = playback.removeAt(index)

    fun onCycleRepeat() = playback.cycleRepeat()

    fun onToggleShuffle() = playback.toggleShuffle()

    // ---- Navigation inside the tab ----

    fun onSectionChange(newSection: PlayerSection) {
        section.value = newSection
        if (newSection != PlayerSection.PLAYLISTS) openPlaylistId.value = null
    }

    fun onOpenPlaylist(playlistId: Long) {
        section.value = PlayerSection.PLAYLISTS
        openPlaylistId.value = playlistId
    }

    fun onClosePlaylist() {
        openPlaylistId.value = null
    }

    // ---- Playlists ----

    fun onPlayPlaylist(playlistId: Long, startIndex: Int = 0, shuffle: Boolean = false) {
        viewModelScope.launch {
            val items = playlists.items(playlistId)
            if (items.isEmpty()) {
                message("This playlist is empty. Share a video to it from YouTube.")
                return@launch
            }
            // The player shuffles the items after the start one, so "Shuffle" picks a random start.
            val start = if (shuffle) random.nextInt(items.size) else startIndex
            playback.playNow(items.map { it.video }, start, shuffle)
            section.value = PlayerSection.QUEUE
            openPlaylistId.value = null
        }
    }

    fun onRemoveFromPlaylist(item: PlaylistItem) {
        viewModelScope.launch {
            playlists.removeItem(item.id)
            message("Removed ${item.video.title}")
        }
    }

    fun onCreatePlaylist(name: String) {
        viewModelScope.launch {
            when (val result = createPlaylist(name)) {
                is PlaylistNameResult.Ok -> message("Created ${result.playlist.name}")
                else -> message(nameProblem(result))
            }
        }
    }

    fun onRenamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            when (val result = renamePlaylist(playlistId, name)) {
                is PlaylistNameResult.Ok -> message("Renamed to ${result.playlist.name}")
                else -> message(nameProblem(result))
            }
        }
    }

    fun onDeletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlists.delete(playlist.id)
            openPlaylistId.value = null
            message("Deleted ${playlist.name}")
        }
    }

    fun onSaveQueue(name: String) {
        val queue = playback.state.value.queue
        if (queue.isEmpty()) {
            viewModelScope.launch { message("The queue is empty") }
            return
        }
        viewModelScope.launch {
            when (val result = saveQueueAsPlaylist(name, queue)) {
                is PlaylistNameResult.Ok -> {
                    val count = queue.distinctBy { it.id }.size
                    message("Saved $count to ${result.playlist.name}")
                }
                else -> message(nameProblem(result))
            }
        }
    }

    fun onReplay(entry: PlaybackHistoryEntry, resume: Boolean) {
        playback.playNow(listOf(entry.video))
        if (resume && entry.positionMs > 0 && (entry.durationMs > 0L && entry.positionMs < entry.durationMs - 1_000)) {
            playback.seekTo(entry.positionMs)
        }
    }

    fun onHistoryQueue(entry: PlaybackHistoryEntry) {
        playback.addToQueue(entry.video)
    }

    fun onHistoryPlaylist(entry: PlaybackHistoryEntry, playlistId: Long) {
        viewModelScope.launch {
            if (!playlists.contains(playlistId, entry.video.id)) playlists.addItem(playlistId, entry.video)
            message("Added to playlist")
        }
    }

    fun onRemoveHistory(entry: PlaybackHistoryEntry) {
        viewModelScope.launch { history.remove(entry.video.id) }
    }

    fun onClearHistory() {
        viewModelScope.launch { history.clear() }
    }

    private fun nameProblem(result: PlaylistNameResult): String = when (result) {
        PlaylistNameResult.BlankName -> "Give the playlist a name"
        is PlaylistNameResult.NameTaken -> "You already have \"${result.existing.name}\""
        is PlaylistNameResult.Ok -> ""
    }

    private suspend fun message(text: String) = eventChannel.send(PlayerEvent.Message(text))

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
