package dev.akibh.mycompanion.library.data

import dev.akibh.mycompanion.library.domain.Book
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The "Continue reading" widget: the last book read and today's minutes against the goal. */
data class LibrarySummary(
    val bookId: Long?,
    val title: String,
    val detail: String,
    val coverPath: String?,
)

@Singleton
class LibrarySummaries @Inject constructor(repository: LibraryRepository) {
    val summary: Flow<LibrarySummary> = combine(repository.books, repository.stats) { books, stats ->
        val last: Book? = books.filter { it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt!! }
        val minutes = stats.todaySeconds / 60
        val today = if (stats.goal.minutesPerDay > 0) {
            "Today $minutes / ${stats.goal.minutesPerDay} min"
        } else {
            "Today $minutes min"
        }
        if (last == null) {
            LibrarySummary(null, "Library", "Add a book to start · $today", null)
        } else {
            LibrarySummary(last.id, last.title, "${(last.progress * 100).toInt()}% · $today", last.coverPath)
        }
    }.distinctUntilChanged()
}
