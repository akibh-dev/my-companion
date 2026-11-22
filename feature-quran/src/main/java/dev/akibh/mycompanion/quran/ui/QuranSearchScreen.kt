package dev.akibh.mycompanion.quran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.quran.data.QuranFonts
import dev.akibh.mycompanion.quran.data.QuranRepository
import dev.akibh.mycompanion.quran.domain.ArabicNormalizer
import dev.akibh.mycompanion.quran.domain.Ayah
import dev.akibh.mycompanion.quran.domain.GoTo
import dev.akibh.mycompanion.quran.domain.GoToParser
import dev.akibh.mycompanion.quran.domain.MushafScript
import dev.akibh.mycompanion.quran.domain.Surah
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** A jump target shown above the text results, with the ayah id it opens. */
data class Jump(val label: String, val ayahId: Int)

sealed interface SearchResults {
    data object Idle : SearchResults

    data object Searching : SearchResults

    data class Found(val jumps: List<Jump>, val ayahs: List<Ayah>, val total: Int) : SearchResults
}

@OptIn(FlowPreview::class)
@HiltViewModel
class QuranSearchViewModel @Inject constructor(
    private val repository: QuranRepository,
    val fonts: QuranFonts,
) : ViewModel() {
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()
    private val mutableResults = MutableStateFlow<SearchResults>(SearchResults.Idle)
    val results: StateFlow<SearchResults> = mutableResults.asStateFlow()
    private val mutableSurahs = MutableStateFlow<List<Surah>>(emptyList())
    val surahs: StateFlow<List<Surah>> = mutableSurahs.asStateFlow()

    init {
        viewModelScope.launch {
            mutableSurahs.value = repository.surahs()
            mutableQuery.debounce(DELAY_MILLIS).distinctUntilChanged().collectLatest(::run)
        }
    }

    fun onQuery(text: String) {
        mutableQuery.value = text
    }

    private suspend fun run(text: String) {
        if (text.isBlank()) {
            mutableResults.value = SearchResults.Idle
            return
        }
        mutableResults.value = SearchResults.Searching
        val surahs = mutableSurahs.value
        val jumps = GoToParser.parse(text, surahs.map { it.ayahCount }, surahs.map { it.nameSimple to it.nameArabic })
            .mapNotNull { jump(it, surahs) }
        val match = matchQuery(text)
        val (ayahs, total) = if (match == null) {
            emptyList<Ayah>() to 0
        } else {
            try {
                repository.search(match, LIMIT) to repository.countMatches(match)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList<Ayah>() to 0 // a query FTS can't parse finds nothing
            }
        }
        mutableResults.value = SearchResults.Found(jumps, ayahs, total)
    }

    private suspend fun jump(goTo: GoTo, surahs: List<Surah>): Jump? = when (goTo) {
        is GoTo.ToAyah -> repository.ayah(goTo.ref)?.let {
            Jump("${surahs[goTo.ref.surah - 1].nameSimple} ${goTo.ref}", it.id)
        }

        is GoTo.ToSurah -> surahs[goTo.number - 1].let { Jump("Surah ${it.number} · ${it.nameSimple}", it.firstAyahId) }
        is GoTo.ToPage -> repository.firstAyahOn(MushafScript.MADANI, goTo.page)
            ?.let { Jump("Page ${goTo.page}", it) }

        is GoTo.ToJuz -> repository.juzList().getOrNull(goTo.juz - 1)?.let { juz ->
            repository.ayah(juz.firstAyah)?.let { Jump("Juz ${goTo.juz}", it.id) }
        }
    }

    companion object {
        private const val DELAY_MILLIS = 300L
        private const val LIMIT = 200

        /** Arabic words, each as a prefix (`الرحم` finds `الرحمن`), all required. Null without Arabic. */
        fun matchQuery(text: String): String? {
            val words = ArabicNormalizer.normalize(
                text
            ).split(' ').filter { word -> word.any { it.code in 0x0600..0x06FF } }
            if (words.isEmpty()) return null
            return words.joinToString(" ") { word -> "${word.filter { it.code in 0x0600..0x06FF }}*" }
        }
    }
}

@Composable
fun QuranSearchRoute(
    onRead: (ayahId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuranSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val surahs by viewModel.surahs.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQuery,
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
            singleLine = true,
            label = { Text("Arabic words, 2:255, a surah, page 50, juz 30") },
        )
        when (val r = results) {
            SearchResults.Idle -> Text(
                "Type Arabic with or without harakat (الرحمن finds ٱلرَّحۡمَٰنِ), or where to go.",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            SearchResults.Searching -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            is SearchResults.Found -> LazyColumn(Modifier.fillMaxSize()) {
                items(r.jumps, key = { "jump-${it.label}" }) { jump ->
                    Text(
                        "Go to ${jump.label}",
                        Modifier.fillMaxWidth().clickable { onRead(jump.ayahId) }.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider()
                }
                if (r.jumps.isEmpty() || r.total > 0) {
                    item(key = "count") {
                        Text(
                            when {
                                r.total == 0 -> "No ayah has these words"
                                r.total > r.ayahs.size -> "${r.total} ayahs, showing the first ${r.ayahs.size}"
                                else -> "${r.total} ayahs"
                            },
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                items(r.ayahs, key = { it.id }) { ayah ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            onRead(
                                ayah.id
                            )
                        }.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val name = surahs.getOrNull(ayah.surah - 1)?.nameSimple.orEmpty()
                        Text(
                            "$name ${ayah.surah}:${ayah.number} · page ${ayah.page}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                ayah.text,
                                Modifier.fillMaxWidth(),
                                fontFamily = viewModel.fonts.hafs,
                                letterSpacing = 0.sp,
                                fontSize = 22.sp,
                                lineHeight = 40.sp
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
