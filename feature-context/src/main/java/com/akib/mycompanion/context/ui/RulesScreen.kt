package com.akib.mycompanion.context.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.RulesEngine
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
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
            RulesUiState(rules, places.associateBy { it.id }, active, settings)
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
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Context rules", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    state.settings.paused -> "Paused from Home"
                                    Feature.CONTEXT_RULES in state.settings.enabled -> "On: rules run in the background"
                                    else -> "Off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = Feature.CONTEXT_RULES in state.settings.enabled,
                            onCheckedChange = viewModel::onFeatureChange
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenPlaces) { Text("Saved places") }
                    OutlinedButton(onClick = onOpenHistory) { Text("History") }
                }
            }
            if (state.rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Example: at the office, Sun–Thu 9:00–17:00 → vibrate.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    onEnabledChange = { viewModel.onEnabledChange(rule, it) },
                    onMove = { up -> viewModel.onMove(rule, up) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onOpenRule(null) },
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add rule") },
            text = { Text("Add rule") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
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
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                    if (active) AssistChip(onClick = onOpen, label = { Text("Active now") })
                }
                Text(
                    "When " + rule.triggers.joinToString(" and ") { Describe.trigger(it, places) },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    rule.actions.joinToString(" · ") { Describe.action(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
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
