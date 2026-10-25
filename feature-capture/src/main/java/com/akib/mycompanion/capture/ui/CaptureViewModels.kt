package com.akib.mycompanion.capture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.ai.Summarizer
import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.ai.valueOrNull
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.domain.Capture
import com.akib.mycompanion.capture.domain.CaptureFilter
import com.akib.mycompanion.capture.domain.CaptureSearch
import com.akib.mycompanion.capture.domain.DayGroup
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.core.sinks.VaultSink
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureSpaceState(
    val loaded: Boolean = false,
    val groups: List<DayGroup> = emptyList(),
    val filter: CaptureFilter = CaptureFilter(),
    val total: Int = 0,
    val today: LocalDate = LocalDate.MIN,
    val triggerOn: Boolean = false,
    val paused: Boolean = false,
)

/** The capture space (FR-4.3): a timeline grouped by day, with search and type filters. */
@HiltViewModel
class CaptureSpaceViewModel @Inject constructor(
    repository: CaptureRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    private val filter = MutableStateFlow(CaptureFilter())

    val state: StateFlow<CaptureSpaceState> =
        combine(repository.captures, filter, settingsStore.settings) { all, f, settings ->
            CaptureSpaceState(
                loaded = true,
                groups = CaptureSearch.groupByDay(CaptureSearch.apply(all, f), clock.zone),
                filter = f,
                total = all.size,
                today = Instant.ofEpochMilli(clock.nowMillis()).atZone(clock.zone).toLocalDate(),
                triggerOn = Feature.CAPTURE in settings.enabled,
                paused = settings.paused,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureSpaceState())

    fun onFilter(change: (CaptureFilter) -> CaptureFilter) = filter.update(change)

    fun setTrigger(on: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(Feature.CAPTURE, on) }
    }
}

sealed interface DetailState {
    data object Loading : DetailState

    data object Missing : DetailState

    data class Loaded(val capture: Capture) : DetailState
}

/** A long-running action on the detail screen. */
sealed interface DetailAction {
    data object Idle : DetailAction

    data object Working : DetailAction

    /** Done: the screen closes. */
    data object Finished : DetailAction

    data class Failed(val reason: String) : DetailAction
}

/** One capture: play or view it, edit its text, summarise, delete, move to the vault (FR-4.3–4.6, FR-4.9). */
@HiltViewModel
class CaptureDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: CaptureRepository,
    private val summarizer: Summarizer,
    private val vault: VaultSink,
) : ViewModel() {
    private val id: Long = savedState.get<String>("id")?.toLongOrNull() ?: -1

    val state: StateFlow<DetailState> = repository.capture(id)
        .map { capture -> capture?.let { DetailState.Loaded(it) } ?: DetailState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    private val mutableSuggestion = MutableStateFlow<Summary?>(null)

    /** A summary ready to keep; null hides the Summarise button (summaries not installed yet). */
    val suggestion: StateFlow<Summary?> = mutableSuggestion.asStateFlow()

    private val mutableAction = MutableStateFlow<DetailAction>(DetailAction.Idle)
    val action: StateFlow<DetailAction> = mutableAction.asStateFlow()

    init {
        // Tried once when the screen opens (FR-4.5): NotReady keeps the button hidden.
        viewModelScope.launch {
            val capture = repository.get(id) ?: return@launch
            if (capture.movedToVault || capture.summary.isNotBlank() || capture.summarySource.isBlank()) return@launch
            mutableSuggestion.value = runCatching { summarizer.summarize(capture.summarySource) }
                .getOrNull()
                ?.valueOrNull()
        }
    }

    fun fileOf(capture: Capture): File? = repository.fileOf(capture)

    fun save(capture: Capture) {
        viewModelScope.launch { repository.update(capture) }
    }

    fun keepSummary() {
        val summary = mutableSuggestion.value ?: return
        mutableSuggestion.value = null
        viewModelScope.launch { repository.applySummary(id, summary) }
    }

    fun delete(capture: Capture) {
        viewModelScope.launch {
            repository.delete(capture)
            mutableAction.value = DetailAction.Finished
        }
    }

    fun moveToVault(capture: Capture) {
        if (mutableAction.value == DetailAction.Working) return
        mutableAction.value = DetailAction.Working
        viewModelScope.launch {
            mutableAction.value = runCatching { repository.moveToVault(capture, vault) }
                .fold(
                    onSuccess = { DetailAction.Finished },
                    onFailure = { DetailAction.Failed(it.message ?: "Couldn't move it to the vault") },
                )
        }
    }
}
