package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.ui.LibraryBody
import dev.akibh.mycompanion.library.ui.LibraryUiState
import org.junit.Test

class LibraryBodyTest {
    private val book = Book(
        id = 1, uri = "content://books/1", format = BookFormat.EPUB, title = "Emma", author = "", coverPath = null,
        fileName = "Emma.epub", size = 1000, addedAt = 1, lastOpenedAt = null, locator = null,
        progress = 0.0, positions = 10, status = BookStatus.TO_READ, finishedAt = null, labels = emptySet(),
    )

    @Test
    fun `while the books load the list shows nothing, not the empty library`() {
        // Regression: "All · 0" and "No books here" flashed on every open before the database answered.
        assertThat(LibraryUiState(loading = true).body).isEqualTo(LibraryBody.NOTHING_YET)
    }

    @Test
    fun `once loaded an empty library shows the empty state and a full one shows its books`() {
        assertThat(LibraryUiState(loading = false).body).isEqualTo(LibraryBody.EMPTY)
        assertThat(LibraryUiState(loading = false, all = listOf(book)).body).isEqualTo(LibraryBody.BOOKS)
    }
}
