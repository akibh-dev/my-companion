package dev.akibh.mycompanion.library.data

import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.Label
import dev.akibh.mycompanion.library.domain.LabelKind
import dev.akibh.mycompanion.library.domain.ReadingStats
import dev.akibh.mycompanion.library.domain.ReadingStatus
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class BookBookmark(val id: Long, val locator: String, val label: String, val progress: Double)

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val prefs: LibraryPrefs,
    private val clock: Clock,
) {
    val books: Flow<List<Book>> = combine(dao.books(), dao.bookLabels()) { books, links ->
        val byBook = links.groupBy({ it.bookId }, { it.labelId })
        books.map { it.toBook(byBook[it.id].orEmpty().toSet()) }
    }

    fun book(id: Long): Flow<Book?> = combine(dao.bookFlow(id), dao.bookLabels()) { book, links ->
        book?.toBook(links.filter { it.bookId == id }.map { it.labelId }.toSet())
    }

    suspend fun bookNow(id: Long): Book? = dao.book(id)?.toBook(emptySet())

    val labels: Flow<List<Label>> = dao.labels().map { list ->
        list.map { Label(it.id, it.name, LabelKind.valueOf(it.kind)) }
    }

    /** Today's minutes, streak, the 30-day chart and books finished this year. */
    val stats: Flow<ReadingStatus> = combine(dao.dayTotals(), dao.books(), prefs.goal) { days, books, goal ->
        ReadingStats.status(
            days = days.associate { it.day to it.seconds },
            finished = books.mapNotNull { book -> book.finishedAt?.let { day(it) } },
            goal = goal,
            today = today(),
        )
    }

    fun secondsRead(bookId: Long): Flow<Long> = dao.bookSeconds(bookId)

    /** Opening a book: it moves to the front of "Recently read" and out of "To read". */
    suspend fun opened(book: Book) {
        val now = clock.nowMillis()
        dao.book(book.id)?.let { entity ->
            dao.update(
                entity.copy(
                    lastOpenedAt = now,
                    status = if (entity.status == BookStatus.TO_READ.name) BookStatus.READING.name else entity.status,
                ),
            )
        }
    }

    suspend fun savePosition(bookId: Long, locator: String, progress: Double) =
        dao.savePosition(bookId, locator, progress.coerceIn(0.0, 1.0), clock.nowMillis())

    suspend fun setStatus(bookId: Long, status: BookStatus) =
        dao.setStatus(bookId, status.name, if (status == BookStatus.FINISHED) clock.nowMillis() else null)

    suspend fun rename(bookId: Long, title: String, author: String) {
        dao.book(bookId)?.let { dao.update(it.copy(title = title.trim().ifBlank { it.title }, author = author.trim())) }
    }

    suspend fun addReading(bookId: Long, millis: Long) {
        if (millis <= 0) return
        dao.addReading(bookId, today().toEpochDay(), (millis + 500) / 1000)
    }

    // ---- Shelves and tags ----

    /** Creates the label (or finds the one with that name) and returns its id. */
    suspend fun label(name: String, kind: LabelKind): Long? {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return null
        dao.label(kind.name, clean)?.let { return it.id }
        val id = dao.insertLabel(LabelEntity(name = clean, kind = kind.name))
        return if (id > 0) id else dao.label(kind.name, clean)?.id
    }

    suspend fun renameLabel(id: Long, name: String) {
        name.trim().takeIf { it.isNotEmpty() }?.let { dao.renameLabel(id, it) }
    }

    suspend fun deleteLabel(id: Long) = dao.deleteLabel(id)

    suspend fun setLabel(bookId: Long, labelId: Long, on: Boolean) =
        if (on) dao.addBookLabel(BookLabelEntity(bookId, labelId)) else dao.removeBookLabel(bookId, labelId)

    // ---- Bookmarks ----

    fun bookmarks(bookId: Long): Flow<List<BookBookmark>> = dao.bookmarks(bookId).map { list ->
        list.map { BookBookmark(it.id, it.locator, it.label, it.progress) }
    }

    suspend fun addBookmark(bookId: Long, locator: String, label: String, progress: Double) {
        dao.insertBookmark(
            BookmarkEntity(
                bookId = bookId,
                locator = locator,
                label = label,
                progress = progress,
                createdAt = clock.nowMillis(),
            ),
        )
    }

    suspend fun renameBookmark(id: Long, label: String) = dao.renameBookmark(id, label.trim())

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    private fun today(): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), clock.zone)

    private fun day(millis: Long): LocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(millis), clock.zone)
}

internal fun BookEntity.toBook(labels: Set<Long>) = Book(
    id = id,
    uri = uri,
    format = BookFormat.valueOf(format),
    title = title,
    author = author,
    coverPath = coverPath,
    fileName = fileName,
    size = size,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    locator = locator,
    progress = progress,
    positions = positions,
    status = BookStatus.entries.firstOrNull { it.name == status } ?: BookStatus.TO_READ,
    finishedAt = finishedAt,
    labels = labels,
)
