package dev.akibh.mycompanion.context.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.context.data.ContextRepository
import dev.akibh.mycompanion.context.device.RulesEngine
import dev.akibh.mycompanion.context.domain.Place
import dev.akibh.mycompanion.context.domain.Rule
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.core.settings.Settings
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.ui.components.AddButton
import dev.akibh.mycompanion.ui.components.AddButtonClearance
import dev.akibh.mycompanion.ui.components.ChoiceRow
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.EmptyState
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.SectionCard
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SectionLabel
import dev.akibh.mycompanion.ui.components.SwitchRow
import dev.akibh.mycompanion.ui.theme.CompanionRed
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val places: Map<Long, Place> = emptyMap(),
    val active: Set<Long> = emptySet(),
    val settings: Settings = Settings(),
    /** False until the database answers, so "No rules yet" doesn't flash on open. */
    val loaded: Boolean = false,
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: ContextRepository,
    private val settingsStore: SettingsStore,
    engine: RulesEngine,
) : ViewModel() {
    val state: StateFlow<RulesUiState> =
        combine(
            repository.rules,
            repository.places,
            engine.active,
            settingsStore.settings
        ) { rules, places, active, settings ->
            RulesUiState(rules, places.associateBy { it.id }, active, settings, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    fun onFeatureChange(on: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(Feature.CONTEXT_RULES, on) }
    }

    fun onEnabledChange(rule: Rule, enabled: Boolean) {
        viewModelScope.launch { repository.save(rule.copy(enabled = enabled)) }
    }

    fun onMove(rule: Rule, up: Boolean) {
        viewModelScope.launch { repository.move(state.value.rules, rule, up) }
    }
}

/** Rule list (plan §11, "rules list"): order matters, the top rule wins a conflict. */
@Composable
fun RulesRoute(
    onOpenRule: (Long?) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RulesScreen(
        state = state,
        onFeatureChange = viewModel::onFeatureChange,
        onOpenRule = onOpenRule,
        onOpenPlaces = onOpenPlaces,
        onOpenHistory = onOpenHistory,
        onEnabledChange = viewModel::onEnabledChange,
        onMove = viewModel::onMove,
        modifier = modifier,
    )
}

@Composable
fun RulesScreen(
    state: RulesUiState,
    onFeatureChange: (Boolean) -> Unit,
    onOpenRule: (Long?) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenHistory: () -> Unit,
    onEnabledChange: (Rule, Boolean) -> Unit,
    onMove: (Rule, up: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = PageMargin,
                top = PageMargin,
                end = PageMargin,
                bottom = AddButtonClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    SwitchRow(
                        title = "Context rules",
                        checked = Feature.CONTEXT_RULES in state.settings.enabled,
                        onCheckedChange = onFeatureChange,
                        summary = when {
                            state.settings.paused -> "Paused from Home"
                            Feature.CONTEXT_RULES in state.settings.enabled -> "On: rules run in the background"
                            else -> "Off"
                        },
                    )
                }
            }
            item {
                ChoiceRow {
                    SecondaryButton(onClick = onOpenPlaces) { Text("Saved places") }
                    SecondaryButton(onClick = onOpenHistory) { Text("History") }
                }
            }
            if (state.loaded && state.rules.isEmpty()) {
                item {
                    EmptyState(
                        symbol = DotSymbol.RULES,
                        title = "No rules yet",
                        body = "Example: at the office, Sun–Thu 9:00–17:00 → vibrate. Tap Add rule to make one.",
                    )
                }
            } else {
                item {
                    Column(Modifier.padding(top = SectionGap - 12.dp)) {
                        SectionLabel("Rules, top one wins", Modifier.padding(start = 8.dp))
                    }
                }
            }
            itemsIndexed(state.rules, key = { _, rule -> rule.id }) { index, rule ->
                RuleCard(
                    rule = rule,
                    places = state.places,
                    active = rule.id in state.active,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.rules.lastIndex,
                    onOpen = { onOpenRule(rule.id) },
                    onEnabledChange = { onEnabledChange(rule, it) },
                    onMove = { up -> onMove(rule, up) },
                )
            }
        }
        AddButton("Add rule", onClick = { onOpenRule(null) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    places: Map<Long, Place>,
    active: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (up: Boolean) -> Unit,
) {
    SectionCard(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    if (active) ActiveNow()
                }
                Text(
                    "When " + rule.triggers.joinToString(" and ") { Describe.trigger(it, places) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rule.actions.joinToString(" · ") { Describe.action(it) },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "${rule.name} enabled" },
                )
                Row {
                    IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${rule.name} up")
                    }
                    IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${rule.name} down")
                    }
                }
            }
        }
    }
}

/** A quiet status: red signal dot and a small label, no border so it never reads as a button. */
@Composable
private fun ActiveNow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(CompanionRed))
        Text("Active now", style = MaterialTheme.typography.labelMedium)
    }
}
