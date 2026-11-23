package dev.akibh.mycompanion.library.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.library.data.BookImporter
import dev.akibh.mycompanion.library.data.FileAvailability
import dev.akibh.mycompanion.library.data.ImportReport
import dev.akibh.mycompanion.library.data.LibraryPrefs
import dev.akibh.mycompanion.library.data.LibraryRepository
import dev.akibh.mycompanion.library.data.RelinkResult
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.Label
import dev.akibh.mycompanion.library.domain.LabelKind
import dev.akibh.mycompanion.library.domain.LibraryFilter
import dev.akibh.mycompanion.library.domain.LibraryQuery
import dev.akibh.mycompanion.library.domain.LibraryView
import dev.akibh.mycompanion.library.domain.ReadingStatus
import dev.akibh.mycompanion.library.domain.SortOrder
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val all: List<Book> = emptyList(),
    val shown: List<Book> = emptyList(),
    val labels: List<Label> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val grid: Boolean = true,
    val availability: Map<String, Availability> = emptyMap(),
    val stats: ReadingStatus? = null,
) {
    val shelves: List<Label> get() = labels.filter { it.kind == LabelKind.SHELF }
    val tags: List<Label> get() = labels.filter { it.kind == LabelKind.TAG }

    /** The book to continue: the last one opened that isn't finished. */
    val continueBook: Book?
        get() = all.filter { it.lastOpenedAt != null && it.status == BookStatus.READING }
            .maxByOrNull { it.lastOpenedAt!! }

    fun availabilityOf(book: Book): Availability = availability[book.uri] ?: Availability.OK

    /**
     * What the list shows. Nothing until the database has answered: showing the empty library (or
     * "All · 0") while it loads made them flash on every open.
     */
    val body: LibraryBody
        get() = when {
            loading -> LibraryBody.NOTHING_YET
            all.isEmpty() -> LibraryBody.EMPTY
            else -> LibraryBody.BOOKS
        }
}

enum class LibraryBody { NOTHING_YET, EMPTY, BOOKS }

/** A book that couldn't be opened from the library, and why. */
data class Problem(val book: Book, val availability: Availability)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val importer: BookImporter,
    private val files: FileAvailability,
    private val prefs: LibraryPrefs,
) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter())
    private val availability = MutableStateFlow<Map<String, Availability>>(emptyMap())

    private val view: Flow<Pair<LibraryFilter, Boolean>> =
        combine(filter, prefs.sort, prefs.grid) { f, sort, grid -> f.copy(sort = sort) to grid }

    val state: StateFlow<LibraryUiState> = combine(
        repository.books,
        repository.labels,
        view,
        availability,
        repository.stats,
    ) { books, labels, (f, grid), access, stats ->
        LibraryUiState(
            loading = false,
            all = books,
            shown = LibraryQuery.apply(books, f),
            labels = labels,
            filter = f,
            grid = grid,
            availability = access,
            stats = stats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val _importing = MutableStateFlow(0)

    /** How many files are being added right now (0: none). */
    val importing: StateFlow<Int> = _importing.asStateFlow()

    private val _report = MutableStateFlow<ImportReport?>(null)
    val report: StateFlow<ImportReport?> = _report.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    private val _relinkCheck = MutableStateFlow<Triple<Long, Uri, RelinkResult.LooksDifferent>?>(null)
    val relinkCheck: StateFlow<Triple<Long, Uri, RelinkResult.LooksDifferent>?> = _relinkCheck.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Checks every book's file; run when the library comes to the front (files change elsewhere). */
    fun refreshAvailability() {
        viewModelScope.launch {
            val books = repository.books.first()
            availability.value = files.checkAll(books.map { it.uri })
        }
    }

    fun setView(view: LibraryView) = filter.update { it.copy(view = view) }

    fun setTag(tag: Long?) = filter.update { it.copy(tag = tag) }

    fun setFormat(format: BookFormat?) = filter.update { it.copy(format = format) }

    fun setText(text: String) = filter.update { it.copy(text = text) }

    fun setSort(sort: SortOrder) = viewModelScope.launch { prefs.setSort(sort) }

    fun setGrid(grid: Boolean) = viewModelScope.launch { prefs.setGrid(grid) }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = uris.size
            val report = try {
                importer.import(uris)
            } finally {
                _importing.value = 0
            }
            val used = importer.grantsUsed()
            _report.value = report
            if (used > GRANT_WARNING) {
                _message.value = "The library links $used files; Android allows about 512. " +
                    "Remove finished books you don't need."
            }
            refreshAvailability()
        }
    }

    fun reportShown() {
        _report.value = null
    }

    /** Checks the file first: a missing one opens the problem dialog instead of the reader. */
    fun open(book: Book, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val access = files.check(book.uri)
            availability.update { it + (book.uri to access) }
            if (access == Availability.OK) onReady(book.id) else _problem.value = Problem(book, access)
        }
    }

    fun dismissProblem() {
        _problem.value = null
    }

    fun relink(bookId: Long, uri: Uri, force: Boolean = false) {
        _problem.value = null
        _relinkCheck.value = null
        viewModelScope.launch {
            when (val result = importer.relink(bookId, uri, force)) {
                RelinkResult.Done -> {
                    _message.value = "File linked again"
                    refreshAvailability()
                }
                is RelinkResult.LooksDifferent -> _relinkCheck.value = Triple(bookId, uri, result)
                is RelinkResult.Failed -> _message.value = result.reason
            }
        }
    }

    fun cancelRelink() {
        _relinkCheck.value = null
    }

    fun remove(book: Book) {
        _problem.value = null
        viewModelScope.launch {
            importer.remove(book.id)
            _message.value = "Removed \"${book.title}\""
        }
    }

    fun setStatus(book: Book, status: BookStatus) = viewModelScope.launch { repository.setStatus(book.id, status) }

    fun rename(book: Book, title: String, author: String) =
        viewModelScope.launch { repository.rename(book.id, title, author) }

    fun setLabel(book: Book, label: Label, on: Boolean) =
        viewModelScope.launch { repository.setLabel(book.id, label.id, on) }

    /** Makes a shelf or tag; with [book], also puts the book on it. */
    fun newLabel(name: String, kind: LabelKind, book: Book? = null) = viewModelScope.launch {
        val id = repository.label(name, kind) ?: return@launch
        if (book != null) repository.setLabel(book.id, id, true)
    }

    fun renameLabel(label: Label, name: String) = viewModelScope.launch { repository.renameLabel(label.id, name) }

    fun deleteLabel(label: Label) = viewModelScope.launch {
        repository.deleteLabel(label.id)
        filter.update { f ->
            f.copy(
                view = if (f.view == LibraryView.Shelf(label.id)) LibraryView.All else f.view,
                tag = if (f.tag == label.id) null else f.tag,
            )
        }
    }

    fun secondsRead(book: Book): Flow<Long> = repository.secondsRead(book.id)

    fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val GRANT_WARNING = 450
    }
}
