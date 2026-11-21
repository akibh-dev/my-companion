package dev.akibh.mycompanion.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.library.domain.ReadingGoal
import dev.akibh.mycompanion.library.domain.SortOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class PageTheme(val label: String) { AUTO("Auto"), LIGHT("Light"), SEPIA("Sepia"), DARK("Dark") }

enum class BookFont(val label: String) { ORIGINAL("Book's own"), SERIF("Serif"), SANS("Sans") }

enum class Spacing(val label: String, val lineHeight: Double, val margins: Double) {
    COMPACT("Compact", 1.3, 0.6),
    NORMAL("Normal", 1.5, 1.0),
    RELAXED("Relaxed", 1.8, 1.4),
}

/** How books look: EPUB text, PDF pages and comic pages each keep their own settings. */
data class ReaderLook(
    val fontScale: Double = 1.0,
    val font: BookFont = BookFont.ORIGINAL,
    val theme: PageTheme = PageTheme.AUTO,
    val spacing: Spacing = Spacing.NORMAL,
    /** EPUB: one long scroll per chapter instead of pages. */
    val scroll: Boolean = false,
    /** PDF: fit the page width (zoomed in) instead of the whole page. */
    val pdfFitWidth: Boolean = false,
    /** PDF: scroll pages vertically instead of swiping across. */
    val pdfVertical: Boolean = true,
    /** Comics: right-to-left pages (manga). */
    val comicRtl: Boolean = false,
    val keepScreenOn: Boolean = true,
)

@Singleton
class LibraryPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<Preferences> = context.libraryStore

    val look: Flow<ReaderLook> = store.data.map { p ->
        ReaderLook(
            fontScale = p[FONT_SCALE] ?: 1.0,
            font = p[FONT].toEnum(BookFont.ORIGINAL),
            theme = p[THEME].toEnum(PageTheme.AUTO),
            spacing = p[SPACING].toEnum(Spacing.NORMAL),
            scroll = p[SCROLL] ?: false,
            pdfFitWidth = p[PDF_FIT_WIDTH] ?: false,
            pdfVertical = p[PDF_VERTICAL] ?: true,
            comicRtl = p[COMIC_RTL] ?: false,
            keepScreenOn = p[KEEP_ON] ?: true,
        )
    }

    suspend fun setLook(look: ReaderLook) {
        store.edit { p ->
            p[FONT_SCALE] = look.fontScale
            p[FONT] = look.font.name
            p[THEME] = look.theme.name
            p[SPACING] = look.spacing.name
            p[SCROLL] = look.scroll
            p[PDF_FIT_WIDTH] = look.pdfFitWidth
            p[PDF_VERTICAL] = look.pdfVertical
            p[COMIC_RTL] = look.comicRtl
            p[KEEP_ON] = look.keepScreenOn
        }
    }

    val goal: Flow<ReadingGoal> = store.data.map { p ->
        ReadingGoal(p[GOAL_MINUTES] ?: ReadingGoal().minutesPerDay, p[GOAL_BOOKS] ?: 0)
    }

    suspend fun setGoal(goal: ReadingGoal) {
        store.edit { p ->
            p[GOAL_MINUTES] = goal.minutesPerDay
            p[GOAL_BOOKS] = goal.booksPerYear
        }
    }

    val sort: Flow<SortOrder> = store.data.map { it[SORT].toEnum(SortOrder.RECENT) }

    suspend fun setSort(sort: SortOrder) {
        store.edit { it[SORT] = sort.name }
    }

    val grid: Flow<Boolean> = store.data.map { it[GRID] ?: true }

    suspend fun setGrid(grid: Boolean) {
        store.edit { it[GRID] = grid }
    }

    suspend fun currentGoal(): ReadingGoal = goal.first()

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    private companion object {
        val FONT_SCALE = doublePreferencesKey("font_scale")
        val FONT = stringPreferencesKey("font")
        val THEME = stringPreferencesKey("theme")
        val SPACING = stringPreferencesKey("spacing")
        val SCROLL = booleanPreferencesKey("scroll")
        val PDF_FIT_WIDTH = booleanPreferencesKey("pdf_fit_width")
        val PDF_VERTICAL = booleanPreferencesKey("pdf_vertical")
        val COMIC_RTL = booleanPreferencesKey("comic_rtl")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val GOAL_MINUTES = intPreferencesKey("goal_minutes")
        val GOAL_BOOKS = intPreferencesKey("goal_books")
        val SORT = stringPreferencesKey("sort")
        val GRID = booleanPreferencesKey("grid")
    }
}

private val Context.libraryStore: DataStore<Preferences> by preferencesDataStore("library")
