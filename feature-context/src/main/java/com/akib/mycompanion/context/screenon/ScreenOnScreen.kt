package com.akib.mycompanion.context.screenon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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

data class ScreenOnUiState(
    val settings: Settings = Settings(),
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val status: ScreenOnState = ScreenOnState(),
) {
    val enabled: Boolean get() = Feature.KEEP_SCREEN_ON in settings.enabled

    /** The live status line under the switch. */
    fun statusLine(): String = when {
        settings.paused && enabled -> "Paused from Home"
        !enabled -> "Off"
        else -> status.summary()
    }
}

@HiltViewModel
class ScreenOnViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val preferences: ScreenOnPreferences,
    status: ScreenOnStatus,
) : ViewModel() {
    val state: StateFlow<ScreenOnUiState> =
        combine(settingsStore.settings, preferences.sensitivity, status.state) { settings, sensitivity, live ->
            ScreenOnUiState(settings, sensitivity, live)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenOnUiState())

    fun onFeatureChange(on: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(Feature.KEEP_SCREEN_ON, on) }
    }

    fun onSensitivityChange(sensitivity: Sensitivity) {
        viewModelScope.launch { preferences.setSensitivity(sensitivity) }
    }
}

/** Keep the screen on outdoors (FR-3.5, FR-3.10): the switch, live status and how upright counts as raised. */
@Composable
fun ScreenOnRoute(modifier: Modifier = Modifier, viewModel: ScreenOnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var overlayAllowed by remember { mutableStateOf(OverlayPermission.granted(context)) }
    // Coming back from the system screen: pick up a newly granted permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlayAllowed = OverlayPermission.granted(context) }
    ScreenOnScreen(
        state = state,
        overlayAllowed = overlayAllowed,
        onFeatureChange = viewModel::onFeatureChange,
        onSensitivityChange = viewModel::onSensitivityChange,
        onAllowOverlay = { runCatching { context.startActivity(OverlayPermission.settingsIntent(context)) } },
        modifier = modifier,
    )
}

@Composable
fun ScreenOnScreen(
    state: ScreenOnUiState,
    overlayAllowed: Boolean,
    onFeatureChange: (Boolean) -> Unit,
    onSensitivityChange: (Sensitivity) -> Unit,
    onAllowOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on outdoors", style = MaterialTheme.typography.titleMedium)
                    Text(state.statusLine(), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.enabled, onCheckedChange = onFeatureChange)
            }
        }
        if (!overlayAllowed) {
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Needs \"Display over other apps\"", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The screen is kept on by an invisible window. Without this access nothing happens.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onAllowOverlay) { Text("Allow") }
                }
            }
        }
        Text("Sensitivity", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            Sensitivity.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == state.sensitivity,
                            onClick = { onSensitivityChange(option) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == state.sensitivity, onClick = null)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(option.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text("How it works", style = MaterialTheme.typography.titleMedium)
        Text(
            "While the screen is on, the phone checks whether you're outside (not at a saved place and " +
                "no saved place's Wi-Fi nearby) and whether you're holding it up towards your face. " +
                "When both are true for a moment, the screen stays on; put the phone down or walk in and " +
                "it times out as usual a few seconds later. With the screen off the sensors are off, " +
                "so it uses no battery in your pocket.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
