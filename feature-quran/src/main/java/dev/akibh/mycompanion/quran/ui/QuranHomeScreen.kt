package dev.akibh.mycompanion.quran.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.quran.data.Bookmark
import dev.akibh.mycompanion.quran.data.BookmarkRepository
import dev.akibh.mycompanion.quran.data.KhatmRepository
import dev.akibh.mycompanion.quran.data.QuranFonts
import dev.akibh.mycompanion.quran.data.QuranPrefs
import dev.akibh.mycompanion.quran.data.QuranRepository
import dev.akibh.mycompanion.quran.domain.Juz
import dev.akibh.mycompanion.quran.domain.Khatm
import dev.akibh.mycompanion.quran.domain.KhatmPlan
import dev.akibh.mycompanion.quran.domain.KhatmStatus
import dev.akibh.mycompanion.quran.domain.QuranReminder
import dev.akibh.mycompanion.quran.domain.Surah
import dev.akibh.mycompanion.quran.domain.arabicDigits
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionCard
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SectionLabel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface QuranHomeState {
    data object Loading : QuranHomeState

    data class Ready(
        val surahs: List<Surah>,
        val juz: List<Juz>,
        val resume: Resume?,
        val bookmarks: List<Bookmark> = emptyList(),
        val khatm: Pair<KhatmPlan, KhatmStatus>? = null,
        val reminder: LocalTime? = null,
    ) : QuranHomeState
}

/** Where "Continue reading" goes. */
data class Resume(val ayahId: Int, val surahName: String, val ayah: Int, val page: Int)

@HiltViewModel
class QuranHomeViewModel @Inject constructor(
    private val repository: QuranRepository,
    prefs: QuranPrefs,
    private val bookmarkRepository: BookmarkRepository,
    private val khatmRepository: KhatmRepository,
    private val quranReminder: QuranReminder,
    val fonts: QuranFonts,
) : ViewModel() {
    fun setReminder(time: LocalTime?) {
        viewModelScope.launch { quranReminder.set(time) }
    }

    fun startKhatm(days: Int) {
        viewModelScope.launch { khatmRepository.start(days) }
    }

    fun stopKhatm() {
        viewModelScope.launch { khatmRepository.stop() }
    }

    private val index = flow { emit(repository.surahs() to repository.juzList()) }

    fun removeBookmark(ayahId: Int) {
        viewModelScope.launch { bookmarkRepository.remove(ayahId) }
    }

    private val extras = combine(bookmarkRepository.bookmarks, khatmRepository.status, quranReminder.time, ::Triple)

    val state: StateFlow<QuranHomeState> = combine(
        index,
        prefs.lastRead,
        extras,
    ) { (surahs, juz), last, (bookmarks, khatm, reminder) ->
        val resume = last?.let { read ->
            repository.ayah(read.ayahId)?.let { ayah ->
                Resume(read.ayahId, surahs[ayah.surah - 1].nameSimple, ayah.number, ayah.page)
            }
        }
        QuranHomeState.Ready(surahs, juz, resume, bookmarks, khatm, reminder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuranHomeState.Loading)
}

@Composable
fun QuranHomeRoute(
    onRead: (ayahId: Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuranHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuranHomeScreen(
        state = state,
        fonts = viewModel.fonts,
        onRead = onRead,
        onSearch = onSearch,
        onRemoveBookmark = viewModel::removeBookmark,
        onStartKhatm = viewModel::startKhatm,
        onStopKhatm = viewModel::stopKhatm,
        onReminder = viewModel::setReminder,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuranHomeScreen(
    state: QuranHomeState,
    fonts: QuranFonts?,
    onRead: (ayahId: Int) -> Unit,
    onSearch: () -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onStartKhatm: (Int) -> Unit,
    onStopKhatm: () -> Unit,
    onReminder: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QuranHomeState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is QuranHomeState.Ready -> {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            val section = Modifier.padding(horizontal = PageMargin)
            LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                item {
                    SecondaryButton(onClick = onSearch, modifier = section.fillMaxWidth()) {
                        DotIcon(DotSymbol.SEARCH, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search")
                    }
                }
                state.resume?.let { resume ->
                    item {
                        Spacer(Modifier.height(SectionGap))
                        ContinueReading(resume, onRead, section)
                    }
                }
                item {
                    Spacer(Modifier.height(SectionGap))
                    KhatmSection(state.khatm, onStartKhatm, onStopKhatm, section)
                }
                item {
                    Spacer(Modifier.height(SectionGap))
                    ReminderSection(state.reminder, onReminder, section)
                    Spacer(Modifier.height(SectionGap))
                }
                stickyHeader {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Surah") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Juz") })
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Bookmarks") })
                    }
                }
                when (tab) {
                    0 -> surahList(state.surahs, fonts, onRead)
                    1 -> juzList(state.juz, state.surahs, onRead)
                    else -> bookmarkList(state.bookmarks, state.surahs, onRead, onRemoveBookmark)
                }
            }
        }
    }
}

@Composable
private fun ContinueReading(resume: Resume, onRead: (Int) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Continue reading", Modifier.padding(start = 8.dp))
        SectionCard(
            Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = "Continue reading") { onRead(resume.ayahId) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${resume.surahName} ${resume.ayah}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Page ${resume.page}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DotIcon(DotSymbol.BOOK, Modifier.size(24.dp))
            }
        }
    }
}

private fun LazyListScope.surahList(surahs: List<Surah>, fonts: QuranFonts?, onRead: (Int) -> Unit) {
    items(surahs, key = { "surah-${it.number}" }) { surah ->
        Row(
            Modifier.fillMaxWidth().clickable {
                onRead(
                    surah.firstAyahId
                )
            }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${surah.number}", Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(surah.nameSimple, style = MaterialTheme.typography.titleMedium)
                val origin = if (surah.meccan) "Meccan" else "Medinan"
                Text(
                    "${surah.nameEnglish} · $origin · ${surah.ayahCount} ayahs · p. ${surah.firstPage}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                surah.nameArabic,
                fontFamily = fonts?.hafs,
                letterSpacing = 0.sp,
                fontSize = 22.sp,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider()
    }
}

private fun LazyListScope.juzList(juz: List<Juz>, surahs: List<Surah>, onRead: (Int) -> Unit) {
    items(juz, key = { "juz-${it.number}" }) { part ->
        val surah = surahs[part.firstAyah.surah - 1]
        Row(
            Modifier.fillMaxWidth().clickable {
                onRead(surah.firstAyahId + part.firstAyah.ayah - 1)
            }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Juz ${part.number}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                "${surah.nameSimple} ${part.firstAyah.ayah} · p. ${part.page}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("  ${part.number.arabicDigits()}", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
    }
}

private fun LazyListScope.bookmarkList(
    bookmarks: List<Bookmark>,
    surahs: List<Surah>,
    onRead: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    if (bookmarks.isEmpty()) {
        item(key = "bookmarks-empty") {
            EmptyState(
                symbol = DotSymbol.BOOKMARK,
                title = "No bookmarks yet",
                body = "Long-press an ayah while reading and choose Bookmark.",
            )
        }
        return
    }
    items(bookmarks, key = { "bookmark-${it.ayahId}" }) { bookmark ->
        Row(
            Modifier.fillMaxWidth().clickable {
                onRead(
                    bookmark.ayahId
                )
            }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${surahs[bookmark.ref.surah - 1].nameSimple} ${bookmark.ref}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (bookmark.note.isNotBlank()) Text(bookmark.note, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { onRemove(bookmark.ayahId) }) { Text("Remove") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun KhatmSection(
    khatm: Pair<KhatmPlan, KhatmStatus>?,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Section("Khatm goal", modifier) {
        if (khatm == null) {
            Text(
                "Finish the Quran in a set number of days; pages you read count by themselves.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton(onClick = { choosing = true }) { Text("Set a goal") }
        } else {
            val (plan, status) = khatm
            Text("Khatm in ${plan.days} days", style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(
                progress = { status.pagesRead / Khatm.PAGES.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    status.finished -> "Done: all ${Khatm.PAGES} pages read. Alhamdulillah."
                    status.overdue -> "${status.pagesLeft} pages left; the planned days are over."
                    status.todayTarget == 0 ->
                        "Today's pages are done (${status.todayRead}). ${status.pagesLeft} left."
                    else ->
                        "Today: ${status.todayTarget} more pages. " +
                            "${status.pagesLeft} left in ${status.daysLeft} days."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Note("${status.pagesRead}/${Khatm.PAGES} pages · streak ${status.streak} days")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(onClick = { choosing = true }) { Text("Change") }
                SecondaryButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Finish the Quran in") },
            text = {
                Column {
                    KHATM_DAYS.forEach { days ->
                        Text(
                            "$days days · about ${(Khatm.PAGES + days - 1) / days} pages a day",
                            Modifier.fillMaxWidth().clickable {
                                onStart(days)
                                choosing = false
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}

/** The usual choices: a week, ten days, a fortnight, a month (Ramadan), two months. */
private val KHATM_DAYS = listOf(7, 10, 15, 30, 60)

/** The daily reading reminder: a normal reminder (it shows in Reminders too) that opens the reader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(time: LocalTime?, onSet: (LocalTime?) -> Unit, modifier: Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Section("Daily reminder", modifier) {
        Text(
            time?.let { "Every day at %02d:%02d".format(it.hour, it.minute) } ?: "Off",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(onClick = { picking = true }) { Text(if (time == null) "Set" else "Change") }
            if (time != null) SecondaryButton(onClick = { onSet(null) }) { Text("Turn off") }
        }
    }
    if (picking) {
        val start = time ?: LocalTime.of(DEFAULT_REMINDER_HOUR, 0)
        val picker = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Remind me to read every day at") },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    onSet(LocalTime.of(picker.hour, picker.minute))
                    picking = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

private const val DEFAULT_REMINDER_HOUR = 21
