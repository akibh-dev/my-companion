package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.LibraryFilter
import dev.akibh.mycompanion.library.domain.LibraryQuery
import dev.akibh.mycompanion.library.domain.LibraryView
import dev.akibh.mycompanion.library.domain.SortOrder
import org.junit.Test

class LibraryQueryTest {
    private fun book(
        id: Long,
        title: String,
        author: String = "",
        format: BookFormat = BookFormat.EPUB,
        status: BookStatus = BookStatus.TO_READ,
        added: Long = id,
        opened: Long? = null,
        progress: Double = 0.0,
        labels: Set<Long> = emptySet(),
    ) = Book(
        id = id, uri = "content://books/$id", format = format, title = title, author = author, coverPath = null,
        fileName = "$title.${format.extension}", size = 1000, addedAt = added, lastOpenedAt = opened, locator = null,
        progress = progress, positions = 10, status = status, finishedAt = null, labels = labels,
    )

    private val books = listOf(
        book(
            1,
            "Crime and Punishment",
            "Fyodor Dostoévsky",
            status = BookStatus.READING,
            opened = 50,
            progress = .4,
            labels = setOf(7)
        ),
        book(2, "Atomic Habits", "James Clear", format = BookFormat.PDF, opened = 90, progress = .9),
        book(3, "One Piece 1", format = BookFormat.CBZ, status = BookStatus.FINISHED, labels = setOf(8)),
        book(4, "anna Karenina", "Leo Tolstoy", added = 100),
    )

    private fun titles(filter: LibraryFilter) = LibraryQuery.apply(books, filter).map { it.title }

    @Test
    fun `recently read first, then never-opened books newest added first`() {
        assertThat(titles(LibraryFilter())).containsExactly(
            "Atomic Habits",
            "Crime and Punishment",
            "anna Karenina",
            "One Piece 1",
        ).inOrder()
    }

    @Test
    fun `title and author sorts ignore case, authorless books last`() {
        assertThat(titles(LibraryFilter(sort = SortOrder.TITLE)).first()).isEqualTo("anna Karenina")
        assertThat(titles(LibraryFilter(sort = SortOrder.AUTHOR)).last()).isEqualTo("One Piece 1")
        assertThat(titles(LibraryFilter(sort = SortOrder.PROGRESS)).first()).isEqualTo("Atomic Habits")
    }

    @Test
    fun `search matches every word in title, author or file name, without accents`() {
        assertThat(titles(LibraryFilter(text = "dostoevsky"))).containsExactly("Crime and Punishment")
        assertThat(titles(LibraryFilter(text = "  clear atomic "))).containsExactly("Atomic Habits")
        assertThat(titles(LibraryFilter(text = "cbz"))).containsExactly("One Piece 1")
        assertThat(titles(LibraryFilter(text = "nothing like it"))).isEmpty()
    }

    @Test
    fun `views by status, shelf, tag and format`() {
        assertThat(
            titles(LibraryFilter(view = LibraryView.Status(BookStatus.READING)))
        ).containsExactly("Crime and Punishment")
        assertThat(titles(LibraryFilter(view = LibraryView.Shelf(8)))).containsExactly("One Piece 1")
        assertThat(titles(LibraryFilter(tag = 7))).containsExactly("Crime and Punishment")
        assertThat(titles(LibraryFilter(format = BookFormat.PDF))).containsExactly("Atomic Habits")
    }
}
