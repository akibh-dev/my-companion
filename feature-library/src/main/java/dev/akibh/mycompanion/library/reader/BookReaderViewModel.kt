package dev.akibh.mycompanion.library.reader

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.library.data.BookBookmark
import dev.akibh.mycompanion.library.data.BookImporter
import dev.akibh.mycompanion.library.data.FileAvailability
import dev.akibh.mycompanion.library.data.LibraryPrefs
import dev.akibh.mycompanion.library.data.LibraryRepository
import dev.akibh.mycompanion.library.data.OpenResult
import dev.akibh.mycompanion.library.data.ReaderLook
import dev.akibh.mycompanion.library.data.Readium
import dev.akibh.mycompanion.library.data.RelinkResult
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.ReadingTimer
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable

/** An open book, handed to the activity to build its Readium navigator. */
class ReaderSession(val book: Book, val publication: Publication, val initial: Locator?, val generation: Int)

sealed interface ReaderState {
    data object Loading : ReaderState

    /**
     * The book can't be opened. [availability] says why: the file is gone or access was lost; null
     * means the file is there but isn't readable (damaged, or locked with DRM).
     */
    data class Unavailable(val book: Book?, val availability: Availability?) : ReaderState

    data class Ready(val session: ReaderSession) : ReaderState
}

data class TocEntry(val title: String, val link: Link, val depth: Int)

data class SearchState(
    val query: String = "",
    val running: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val searched: Boolean = false,
)

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    saved: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val readium: Readium,
    private val availability: FileAvailability,
    private val importer: BookImporter,
    private val prefs: LibraryPrefs,
) : ViewModel() {
    val bookId: Long = saved.get<Long>(BookReaderActivity.EXTRA_BOOK_ID) ?: -1L

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    val look: StateFlow<ReaderLook> = prefs.look.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderLook())
    val bookmarks: StateFlow<List<BookBookmark>> =
        repository.bookmarks(bookId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _locator = MutableStateFlow<Locator?>(null)
    val locator: StateFlow<Locator?> = _locator.asStateFlow()

    /** Every position in the book (pages for PDF and comics), for the slider and "12 / 340". */
    private val _positions = MutableStateFlow<List<Locator>>(emptyList())
    val positions: StateFlow<List<Locator>> = _positions.asStateFlow()

    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    /** "You reached the end: mark as finished?", once per visit. */
    private val _finishPrompt = MutableStateFlow(false)
    val finishPrompt: StateFlow<Boolean> = _finishPrompt.asStateFlow()
    private var finishAsked = false

    /** A relink that needs a yes: the picked file has another name or size. */
    private val _relinkCheck = MutableStateFlow<Pair<Uri, RelinkResult.LooksDifferent>?>(null)
    val relinkCheck: StateFlow<Pair<Uri, RelinkResult.LooksDifferent>?> = _relinkCheck.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var generation = 0
    private var publication: Publication? = null
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var tickJob: Job? = null
    private val timer = ReadingTimer()

    init {
        open(initial = null)
    }

    val toc: List<TocEntry>
        get() = publication?.tableOfContents?.flatten(0).orEmpty()

    val searchable: Boolean
        get() = when ((state.value as? ReaderState.Ready)?.session?.book?.format) {
            BookFormat.EPUB -> publication?.isSearchable == true
            BookFormat.PDF -> true
            else -> false
        }

    private fun List<Link>.flatten(depth: Int): List<TocEntry> = flatMap { link ->
        val title = link.title?.takeIf { it.isNotBlank() } ?: "Untitled"
        listOf(TocEntry(title, link, depth)) + link.children.flatten(depth + 1)
    }

    /** Opens (or reopens, after "Locate file" or a comic direction change) the book. */
    private fun open(initial: Locator?) {
        viewModelScope.launch {
            _state.value = ReaderState.Loading
            // The navigator may still be drawing it: closed once the activity has let go of it.
            publication?.let { retired += it }
            publication = null
            val book = repository.bookNow(bookId)
            if (book == null) {
                _state.value = ReaderState.Unavailable(null, Availability.MISSING)
                return@launch
            }
            val access = availability.check(book.uri)
            if (access != Availability.OK) {
                _state.value = ReaderState.Unavailable(book, access)
                return@launch
            }
            val rtl = prefs.look.first().comicRtl
            when (val result = readium.open(Uri.parse(book.uri), book.format, rightToLeft = rtl)) {
                is OpenResult.Failed -> {
                    val again = availability.check(book.uri)
                    _state.value = ReaderState.Unavailable(book, again.takeIf { it != Availability.OK })
                }
                is OpenResult.Opened -> {
                    publication = result.publication
                    val start = initial ?: book.locator?.let { Locator.fromJSON(JSONObject(it)) }
                    repository.opened(book)
                    _state.value = ReaderState.Ready(ReaderSession(book, result.publication, start, ++generation))
                    launch {
                        _positions.value = runCatching { result.publication.positions() }.getOrDefault(emptyList())
                    }
                }
            }
        }
    }

    fun retry() = open(_locator.value)

    /** The comic direction changed: the pages have to be laid out again. */
    fun reopen() = open(_locator.value)

    fun onLocator(locator: Locator) {
        _locator.value = locator
        activity()
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MILLIS)
            save(locator)
        }
        val book = (state.value as? ReaderState.Ready)?.session?.book ?: return
        if (!finishAsked && book.status != BookStatus.FINISHED && progress(locator) >= FINISHED_AT) {
            finishAsked = true
            _finishPrompt.value = true
        }
    }

    /** How far through the book [locator] is: the page or position counts, else Readium's estimate. */
    fun progress(locator: Locator): Double {
        val total = _positions.value.size
        val position = locator.locations.position
        return if (total > 0 && position != null) {
            position.toDouble() / total
        } else {
            locator.locations.totalProgression ?: 0.0
        }
    }

    private suspend fun save(locator: Locator) {
        repository.savePosition(bookId, locator.toJSON().toString(), progress(locator))
    }

    fun toggleChrome() {
        _chromeVisible.update { !it }
        activity()
    }

    fun showChrome(show: Boolean) {
        _chromeVisible.value = show
    }

    // ---- Reading time ----

    fun activity() {
        credit(timer.activity(SystemClock.elapsedRealtime()))
    }

    fun resume() {
        timer.resume(SystemClock.elapsedRealtime())
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                credit(timer.take(SystemClock.elapsedRealtime()))
            }
        }
    }

    fun pause() {
        tickJob?.cancel()
        credit(timer.pause(SystemClock.elapsedRealtime()))
        _locator.value?.let { locator ->
            saveJob?.cancel()
            viewModelScope.launch { save(locator) }
        }
    }

    private var pendingMillis = 0L

    private fun credit(millis: Long) {
        if ((state.value as? ReaderState.Ready) == null) return
        pendingMillis += millis
        if (pendingMillis < MIN_WRITE_MILLIS && tickJob?.isActive == true) return
        val write = pendingMillis
        pendingMillis = 0
        if (write > 0) viewModelScope.launch { repository.addReading(bookId, write) }
    }

    // ---- Look ----

    fun setLook(look: ReaderLook) {
        val reopen = look.comicRtl != this.look.value.comicRtl
        viewModelScope.launch {
            prefs.setLook(look)
            if (reopen && (state.value as? ReaderState.Ready)?.session?.book?.format == BookFormat.CBZ) reopen()
        }
    }

    // ---- Bookmarks ----

    fun bookmarkHere(label: String) {
        val locator = _locator.value ?: return
        viewModelScope.launch { repository.addBookmark(bookId, locator.toJSON().toString(), label, progress(locator)) }
    }

    fun removeBookmark(id: Long) = viewModelScope.launch { repository.deleteBookmark(id) }

    fun renameBookmark(id: Long, label: String) = viewModelScope.launch { repository.renameBookmark(id, label) }

    // ---- Finished ----

    fun markFinished(finished: Boolean) {
        _finishPrompt.value = false
        if (finished) {
            viewModelScope.launch {
                repository.setStatus(bookId, BookStatus.FINISHED)
                _message.value = "Marked as finished"
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    // ---- Search ----

    fun search(query: String) {
        val session = (state.value as? ReaderState.Ready)?.session ?: return
        searchJob?.cancel()
        val text = query.trim()
        if (text.length < 2) {
            _search.value = SearchState(query = query)
            return
        }
        _search.value = SearchState(query = query, running = true)
        searchJob = viewModelScope.launch {
            val publish = { hits: List<SearchHit> -> _search.update { it.copy(hits = hits) } }
            runCatching {
                when (session.book.format) {
                    BookFormat.EPUB -> BookSearch.epub(session.publication, text, publish)
                    BookFormat.PDF -> BookSearch.pdf(
                        context,
                        Uri.parse(session.book.uri),
                        _positions.value.ifEmpty { session.publication.positions() },
                        text,
                        publish,
                    )
                    BookFormat.CBZ -> Unit
                }
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            _search.update { it.copy(running = false, searched = true) }
        }
    }

    // ---- Missing file ----

    fun relink(uri: Uri, force: Boolean = false) {
        _relinkCheck.value = null
        viewModelScope.launch {
            when (val result = importer.relink(bookId, uri, force)) {
                RelinkResult.Done -> {
                    _message.value = "File linked again"
                    open(initial = null)
                }
                is RelinkResult.LooksDifferent -> _relinkCheck.value = uri to result
                is RelinkResult.Failed -> _message.value = result.reason
            }
        }
    }

    fun cancelRelink() {
        _relinkCheck.value = null
    }

    fun remove(onDone: () -> Unit) = viewModelScope.launch {
        importer.remove(bookId)
        onDone()
    }

    private val retired = mutableListOf<Publication>()

    /** The activity removed or replaced the navigator: the books it showed before can close. */
    fun releaseRetired() {
        retired.forEach { it.close() }
        retired.clear()
    }

    override fun onCleared() {
        releaseRetired()
        publication?.close()
        publication = null
    }

    private companion object {
        const val SAVE_DELAY_MILLIS = 1_000L
        const val TICK_MILLIS = 15_000L
        const val MIN_WRITE_MILLIS = 15_000L
        const val FINISHED_AT = 0.995
    }
}
