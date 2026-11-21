package dev.akibh.mycompanion.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.library.data.BookEntity
import dev.akibh.mycompanion.library.data.FileAvailability
import dev.akibh.mycompanion.library.data.LibraryBackups
import dev.akibh.mycompanion.library.data.LibraryDb
import dev.akibh.mycompanion.library.data.LibraryPrefs
import dev.akibh.mycompanion.library.data.LibraryRepository
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.LabelKind
import dev.akibh.mycompanion.library.domain.ReadingGoal
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, LibraryDb::class.java).allowMainThreadQueries().build()
    private val dao = db.dao()
    private val clock = object : Clock {
        var now = 1_763_700_000_000L // 2025-11-21
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("UTC")
    }
    private val prefs = LibraryPrefs(context)
    private val repository = LibraryRepository(dao, prefs, clock)

    @After
    fun close() = db.close()

    private suspend fun addBook(title: String, uri: String = "content://downloads/$title"): Long = dao.insert(
        BookEntity(
            uri = uri, format = "EPUB", title = title, author = "", coverPath = null, fileName = "$title.epub",
            size = 1234, addedAt = clock.now, status = BookStatus.TO_READ.name,
        ),
    )

    @Test
    fun `opening moves a book to Reading, its place and progress are kept`() = runTest {
        val id = addBook("Dune")
        repository.opened(repository.bookNow(id)!!)
        repository.savePosition(id, """{"href":"ch1.xhtml","type":"text/html"}""", .42)

        val book = repository.books.first().single()
        assertThat(book.status).isEqualTo(BookStatus.READING)
        assertThat(book.progress).isEqualTo(.42)
        assertThat(book.locator).contains("ch1.xhtml")
        assertThat(book.lastOpenedAt).isEqualTo(clock.now)
    }

    @Test
    fun `shelves and tags attach to books, deleting one keeps the books`() = runTest {
        val id = addBook("Dune")
        val shelf = repository.label("Sci-fi", LabelKind.SHELF)!!
        assertThat(repository.label(" Sci-fi ", LabelKind.SHELF)).isEqualTo(shelf) // same name, same shelf
        repository.setLabel(id, shelf, true)
        assertThat(repository.books.first().single().labels).containsExactly(shelf)

        repository.deleteLabel(shelf)
        assertThat(repository.books.first().single().labels).isEmpty()
        assertThat(repository.books.first()).hasSize(1)
    }

    @Test
    fun `reading time adds up per day and survives removing the book`() = runTest {
        val id = addBook("Dune")
        repository.addReading(id, 90_000)
        repository.addReading(id, 30_000)
        assertThat(repository.secondsRead(id).first()).isEqualTo(120)

        dao.delete(id)
        assertThat(repository.stats.first().todaySeconds).isEqualTo(120)
    }

    @Test
    fun `a backup restores books, bookmarks, shelves, stats and the goal`() = runTest {
        val id = addBook("Dune")
        repository.savePosition(id, """{"href":"ch9.xhtml","type":"text/html"}""", .7)
        repository.addBookmark(id, """{"href":"ch2.xhtml","type":"text/html"}""", "The test", .1)
        repository.setLabel(id, repository.label("Sci-fi", LabelKind.SHELF)!!, true)
        repository.addReading(id, 600_000)
        prefs.setGoal(ReadingGoal(minutesPerDay = 45, booksPerYear = 12))
        val backups = LibraryBackups(dao, prefs)
        val saved = backups.backup()

        dao.clearBooks()
        dao.clearLabels()
        dao.clearDays()
        prefs.setGoal(ReadingGoal())
        backups.restore(saved)

        val book = repository.books.first().single()
        assertThat(book.title).isEqualTo("Dune")
        assertThat(book.progress).isEqualTo(.7)
        assertThat(book.labels).hasSize(1)
        assertThat(repository.bookmarks(id).first().single().label).isEqualTo("The test")
        assertThat(repository.secondsRead(id).first()).isEqualTo(600)
        assertThat(prefs.goal.first()).isEqualTo(ReadingGoal(45, 12))
        assertThat(backups.backup()).isEqualTo(saved)
    }

    @Test
    fun `a deleted file is missing, a file without a kept grant has no access`() = runTest {
        val files = FileAvailability(context)
        val gone = File(context.cacheDir, "gone.pdf")
        assertThat(files.check(Uri.fromFile(gone).toString())).isEqualTo(Availability.MISSING)
        assertThat(files.check("content://com.android.providers.downloads.documents/document/42"))
            .isEqualTo(Availability.NO_ACCESS)
    }
}
