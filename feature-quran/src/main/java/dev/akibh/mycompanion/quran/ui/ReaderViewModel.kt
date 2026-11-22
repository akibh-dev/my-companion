package dev.akibh.mycompanion.quran.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.quran.data.BookmarkRepository
import dev.akibh.mycompanion.quran.data.KhatmRepository
import dev.akibh.mycompanion.quran.data.LastRead
import dev.akibh.mycompanion.quran.data.QuranFonts
import dev.akibh.mycompanion.quran.data.QuranPrefs
import dev.akibh.mycompanion.quran.data.QuranRepository
import dev.akibh.mycompanion.quran.data.ReaderPrefs
import dev.akibh.mycompanion.quran.domain.Ayah
import dev.akibh.mycompanion.quran.domain.MushafLine
import dev.akibh.mycompanion.quran.domain.MushafScript
import dev.akibh.mycompanion.quran.domain.ReadingMode
import dev.akibh.mycompanion.quran.domain.Surah
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the reader needs to draw, once the starting point is known. */
sealed interface ReaderState {
    data object Loading : ReaderState

    data class Ready(
        val prefs: ReaderPrefs,
        val surahs: List<Surah>,
        /** Script has page layouts (Indo-Pak pages arrive with the QUL layout). */
        val hasPages: Boolean,
        /** Page to open (mushaf) and ayah to open (list); both follow [position] on a switch. */
        val startPage: Int,
        val startAyahId: Int,
        /** Bumped on every switch, so the pager and list re-position. */
        val generation: Int,
    ) : ReaderState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repository: QuranRepository,
    private val prefs: QuranPrefs,
    private val bookmarks: BookmarkRepository,
    private val khatm: KhatmRepository,
    val fonts: QuranFonts,
) : ViewModel() {
    /** Ayahs on screen now; after [READ_AFTER_MILLIS] on them they count as read (khatm goal). */
    private val onScreen = MutableStateFlow<IntRange?>(null)

    val bookmarked: StateFlow<Set<Int>> =
        bookmarks.bookmarkedIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun bookmark(ayahId: Int, note: String) {
        viewModelScope.launch { bookmarks.save(ayahId, note) }
    }

    fun removeBookmark(ayahId: Int) {
        viewModelScope.launch { bookmarks.remove(ayahId) }
    }

    private val mutableState = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = mutableState.asStateFlow()

    /** The ayah being read: first ayah of the page shown, or the top of the list. */
    private val position = MutableStateFlow<Int?>(null)
    private val lineCache = HashMap<Pair<MushafScript, Int>, List<MushafLine>>()

    init {
        viewModelScope.launch {
            val asked = saved.get<String>("ayah")?.toIntOrNull()?.takeIf { it in 1..AYAHS }
            val last = prefs.lastRead.first()
            val reader = prefs.currentReader().let { p ->
                // Opening "continue reading" brings back the view it was read in.
                if (asked == null && last != null) p.copy(script = last.script, mode = last.mode) else p
            }
            position.value = asked ?: last?.ayahId ?: 1
            publish(reader, generation = 0)
        }
        viewModelScope.launch {
            onScreen.filterNotNull().collectLatest { range ->
                delay(READ_AFTER_MILLIS)
                khatm.markRead(range.first, range.last)
            }
        }
        viewModelScope.launch {
            position.filterNotNull().debounce(SAVE_DELAY_MILLIS).collect { id ->
                val ready = state.value as? ReaderState.Ready ?: return@collect
                prefs.saveLastRead(LastRead(id, ready.prefs.script, ready.prefs.mode, System.currentTimeMillis()))
            }
        }
    }

    private suspend fun publish(reader: ReaderPrefs, generation: Int) {
        val id = position.value ?: 1
        val hasPages = repository.hasPages(reader.script)
        val prefsShown = if (!hasPages && reader.mode == ReadingMode.MUSHAF) {
            reader.copy(
                mode = ReadingMode.AYAH_LIST
            )
        } else {
            reader
        }
        mutableState.value = ReaderState.Ready(
            prefs = prefsShown,
            surahs = repository.surahs(),
            hasPages = hasPages,
            startPage = repository.pageOf(id, reader.script) ?: 1,
            startAyahId = id,
            generation = generation,
        )
    }

    private fun switch(change: (ReaderPrefs) -> ReaderPrefs) {
        val ready = state.value as? ReaderState.Ready ?: return
        viewModelScope.launch {
            val next = change(ready.prefs)
            prefs.update { next }
            publish(next, ready.generation + 1)
        }
    }

    fun setMode(mode: ReadingMode) = switch { it.copy(mode = mode) }

    fun setScript(script: MushafScript) = switch { it.copy(script = script) }

    /** Zoom and text size change in place, without re-positioning. */
    fun setTextScale(scale: Float) = tweak { it.copy(textScale = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)) }

    fun setPageZoom(zoom: Float) = tweak { it.copy(pageZoom = zoom.coerceIn(1f, MAX_PAGE_ZOOM)) }

    private fun tweak(change: (ReaderPrefs) -> ReaderPrefs) {
        val ready = state.value as? ReaderState.Ready ?: return
        mutableState.value = ready.copy(prefs = change(ready.prefs))
        viewModelScope.launch { prefs.update(change) }
    }

    suspend fun lines(script: MushafScript, page: Int): List<MushafLine> =
        lineCache.getOrPut(script to page) { repository.page(script, page) }

    suspend fun pageFont(script: MushafScript, page: Int): FontFamily = when (script) {
        MushafScript.MADANI -> fonts.page(page)
        MushafScript.INDOPAK -> fonts.nastaleeq
    }

    suspend fun ayahs(script: MushafScript): List<Ayah> = repository.all(script)

    suspend fun ayahs(from: Int, to: Int, script: MushafScript): List<Ayah> = repository.ayahs(from, to, script)

    fun onPageShown(script: MushafScript, page: Int) {
        viewModelScope.launch {
            val ayahs = lines(script, page).filter { it.lastAyahId > 0 }
            val first = ayahs.minOfOrNull { it.firstAyahId } ?: return@launch
            position.value = first
            onScreen.value = first..ayahs.maxOf { it.lastAyahId }
        }
    }

    fun onAyahShown(id: Int) {
        position.value = id
        onScreen.value = id..id
    }

    private companion object {
        const val AYAHS = 6236
        const val SAVE_DELAY_MILLIS = 1_500L
        const val READ_AFTER_MILLIS = 20_000L
    }
}

/** Ayah list text size, as a multiple of the default. */
internal const val MIN_TEXT_SCALE = 0.6f
internal const val MAX_TEXT_SCALE = 2.5f

/** Page zoom: 1 fits the width; up to three times larger. */
internal const val MAX_PAGE_ZOOM = 3f
