package dev.akibh.mycompanion.library.domain

import java.text.Normalizer

/** Which books the library shows: the shelf strip at the top. */
sealed interface LibraryView {
    data object All : LibraryView

    data class Status(val status: BookStatus) : LibraryView

    data class Shelf(val id: Long) : LibraryView
}

enum class SortOrder(val label: String) {
    RECENT("Recently read"),
    ADDED("Recently added"),
    TITLE("Title"),
    AUTHOR("Author"),
    PROGRESS("Progress"),
}

data class LibraryFilter(
    val view: LibraryView = LibraryView.All,
    val tag: Long? = null,
    val format: BookFormat? = null,
    val text: String = "",
    val sort: SortOrder = SortOrder.RECENT,
)

object LibraryQuery {
    fun apply(books: List<Book>, filter: LibraryFilter): List<Book> {
        val words = fold(filter.text).split(' ').filter { it.isNotBlank() }
        val shown = books.filter { book ->
            val inView = when (val view = filter.view) {
                LibraryView.All -> true
                is LibraryView.Status -> book.status == view.status
                is LibraryView.Shelf -> view.id in book.labels
            }
            inView &&
                (filter.tag == null || filter.tag in book.labels) &&
                (filter.format == null || book.format == filter.format) &&
                (words.isEmpty() || fold("${book.title} ${book.author} ${book.fileName}").containsAll(words))
        }
        return when (filter.sort) {
            // Never-opened books follow the read ones, newest added first.
            SortOrder.RECENT -> shown.sortedWith(
                compareByDescending<Book> { it.lastOpenedAt ?: Long.MIN_VALUE }.thenByDescending { it.addedAt },
            )
            SortOrder.ADDED -> shown.sortedByDescending { it.addedAt }
            SortOrder.TITLE -> shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOrder.AUTHOR -> shown.sortedWith(
                compareBy<Book, String>(String.CASE_INSENSITIVE_ORDER) { it.author.ifBlank { "￿" } }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
            )
            SortOrder.PROGRESS -> shown.sortedByDescending { it.progress }
        }
    }

    private fun String.containsAll(words: List<String>) = words.all { it in this }

    /** Lower case without accents, so "Dostoevsky" finds "Dostoévsky". */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

    private val MARKS = Regex("\\p{Mn}+")
}
