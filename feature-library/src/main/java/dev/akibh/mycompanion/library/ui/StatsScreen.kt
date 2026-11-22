package dev.akibh.mycompanion.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.library.data.LibraryPrefs
import dev.akibh.mycompanion.library.data.LibraryRepository
import dev.akibh.mycompanion.library.domain.ReadingGoal
import dev.akibh.mycompanion.library.domain.ReadingStats
import dev.akibh.mycompanion.library.domain.ReadingStatus
import dev.akibh.mycompanion.ui.components.ChoiceChip
import dev.akibh.mycompanion.ui.components.ChoiceRow
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionGap
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReadingStatsViewModel @Inject constructor(
    repository: LibraryRepository,
    private val prefs: LibraryPrefs,
) : ViewModel() {
    val stats: StateFlow<ReadingStatus?> =
        repository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGoal(goal: ReadingGoal) = viewModelScope.launch { prefs.setGoal(goal) }
}

/** Reading time, streak, the 30-day dot chart and the goals. */
@Composable
fun ReadingStatsRoute(modifier: Modifier = Modifier, viewModel: ReadingStatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val current = stats ?: return
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageMargin, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        Section("Today") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    DotRing(
                        if (current.goalSeconds > 0) current.todaySeconds.toFloat() / current.goalSeconds else 0f,
                        Modifier.size(120.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${current.todaySeconds / 60}", style = MaterialTheme.typography.displaySmall)
                        Text("min today", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Figure(
                        if (current.goal.minutesPerDay > 0) "${current.goal.minutesPerDay} min" else "Off",
                        "Daily goal",
                    )
                    Figure(if (current.streak == 1) "1 day" else "${current.streak} days", "Streak")
                }
            }
        }
        Section("Last 30 days") {
            Column {
                DotBars(
                    current.recent.map { it / 60 },
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .semantics { contentDescription = "Minutes read on each of the last 30 days" },
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        "${ReadingStats.CHART_DAYS} days ago",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Today", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Figure(readTime(current.recent.sum()).replace("not read yet", "0 min"), "In 30 days")
                Figure(readTime(current.totalSeconds).replace("not read yet", "0 min"), "All time")
            }
        }
        val books = current.goal.booksPerYear
        Section("This year") {
            Figure(
                if (books > 0) "${current.finishedThisYear} of $books" else "${current.finishedThisYear}",
                if (current.finishedThisYear == 1) "book finished" else "books finished",
            )
            if (books > 0) DotProgress(current.finishedThisYear.toFloat() / books)
        }
        Section("Daily goal") {
            ChoiceRow {
                listOf(0, 10, 20, 30, 45, 60).forEach { minutes ->
                    ChoiceChip(
                        if (minutes == 0) "Off" else "$minutes min",
                        current.goal.minutesPerDay == minutes,
                        { viewModel.setGoal(current.goal.copy(minutesPerDay = minutes)) },
                    )
                }
            }
        }
        Section("Books this year", note = "How many books to finish this year. Off hides the goal.") {
            Stepper(
                value = if (books == 0) "Off" else "$books",
                onMinus = { viewModel.setGoal(current.goal.copy(booksPerYear = (books - 1).coerceAtLeast(0))) },
                onPlus = { viewModel.setGoal(current.goal.copy(booksPerYear = books + 1)) },
                minusLabel = "Fewer books",
                plusLabel = "More books",
                minusEnabled = books > 0,
            )
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
