package com.akib.mycompanion.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.core.health.HeartbeatLog
import com.akib.mycompanion.core.host.ServiceHost
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: Settings = Settings(),
    /** Permissions a switched-on feature needs but doesn't have. */
    val missing: List<Requirement> = emptyList(),
    /** A feature that needs the background service is on, but the service isn't running (NFR-7). */
    val hostDown: Boolean = false,
    val lastHeartbeat: Long? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val requirements: Set<@JvmSuppressWildcards Requirement>,
    private val host: ServiceHost,
    private val heartbeats: HeartbeatLog,
) : ViewModel() {

    private val lastHeartbeat = MutableStateFlow<Long?>(null)

    /** Bumped on every resume: permissions are granted in system screens, outside the app. */
    private val checks = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> =
        combine(settingsStore.settings, host.running, lastHeartbeat, checks) { settings, running, beat, _ ->
            HomeUiState(
                settings = settings,
                missing = requirements.filter { req ->
                    (req.feature == null || req.feature in settings.enabled) && !req.isGranted(context)
                },
                hostDown = host.isNeeded(settings) && !running,
                lastHeartbeat = beat,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    fun onResume() {
        checks.value++
        viewModelScope.launch { lastHeartbeat.value = heartbeats.times().lastOrNull() }
    }

    fun onPauseChange(paused: Boolean) {
        viewModelScope.launch { settingsStore.setPaused(paused) }
    }

    fun onFeatureChange(feature: Feature, enabled: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(feature, enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
