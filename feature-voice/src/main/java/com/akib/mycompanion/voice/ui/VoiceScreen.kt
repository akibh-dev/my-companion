package com.akib.mycompanion.voice.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.ReadingMode
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.system.VoicePlayback
import com.akib.mycompanion.voice.system.VoiceSettingsStore
import com.akib.mycompanion.voice.system.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val playback: VoicePlayback,
) : ViewModel() {
    val app: StateFlow<Settings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Settings()
    )
    val settings: StateFlow<VoiceSettings> =
        voiceSettings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettings())
    val playbackState = playback.state
    private var testJob: Job? = null

    fun onVoiceMode(on: Boolean) {
        viewModelScope.launch {
            if (on && settings.value.readingMode != ReadingMode.ORIGINAL) {
                settingsStore.setUseModel(AiEngine.TRANSLITERATOR, true)
            }
            settingsStore.setEnabled(Feature.VOICE_MODE, on)
            if (!on) playback.stop()
        }
    }

    fun update(change: (VoiceSettings) -> VoiceSettings) {
        viewModelScope.launch { voiceSettings.update(change) }
    }

    fun mode(mode: ReadingMode) {
        viewModelScope.launch {
            if (mode != ReadingMode.ORIGINAL) {
                settingsStore.setUseModel(AiEngine.TRANSLITERATOR, true)
            }
            voiceSettings.update { it.copy(readingMode = mode) }
        }
    }

    fun onTest() {
        if (testJob?.isActive == true) return
        testJob = viewModelScope.launch { playback.sample() }
    }

    fun stop() {
        testJob?.cancel()
        playback.stop()
    }

    override fun onCleared() {
        testJob?.cancel()
        super.onCleared()
    }
}

/** Voice settings (plan §11): apps, senders, privacy, speed, and a test. */
@Composable
fun VoiceRoute(modifier: Modifier = Modifier, viewModel: VoiceViewModel = hiltViewModel()) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.playbackState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var senders by remember(s.senders) { mutableStateOf(s.senders.joinToString(", ")) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Read messages aloud",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = Feature.VOICE_MODE in app.enabled, onCheckedChange = viewModel::onVoiceMode)
            }
            if (app.paused) Text("Paused from Home", color = MaterialTheme.colorScheme.error)
            Text("Also a Quick Settings tile. Silent during calls.", style = MaterialTheme.typography.bodySmall)
            Text(
                "One Bengali voice reads the whole message, including English words.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text("Where", style = MaterialTheme.typography.titleSmall)
            Chips(Privacy.entries, { it.title }, { it == s.privacy }) { p -> viewModel.update { it.copy(privacy = p) } }
        }
        item {
            Text("Apps", style = MaterialTheme.typography.titleSmall)
            Chips(ChatApp.entries, { it.title }, { it in s.apps }) { a ->
                viewModel.update { it.copy(apps = if (a in it.apps) it.apps - a else it.apps + a) }
            }
        }
        item {
            OutlinedTextField(
                value = senders,
                onValueChange = { text ->
                    senders = text
                },
                label = { Text("Only these people (empty = everyone)") },
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(onClick = {
                viewModel.update { it.copy(senders = senders.split(',').map(String::trim).filter(String::isNotEmpty)) }
            }) { Text("Save people") }
        }
        item {
            Text("Reading mode", style = MaterialTheme.typography.titleSmall)
            Chips(
                ReadingMode.entries,
                { it.title },
                { it == s.readingMode },
                viewModel::mode
            )
            Text(
                "Auto converts common Banglish words and preserves other English words. " +
                    "Banglish text converts every Latin word.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Text("Speech speed: ${s.speechRate}")
            Slider(value = s.speechRate, onValueChange = { rate ->
                viewModel.update {
                    it.copy(speechRate = rate)
                }
            }, valueRange = 0.5f..2f)
        }
        item {
            FilledTonalButton(onClick = {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }) { Text("Notification access settings") }
        }
        item {
            Text("Cut messages after ${s.maxChars} characters", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = s.maxChars.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(maxChars = (v / 20).roundToInt() * 20) } },
                valueRange = 60f..600f,
            )
        }
        item {
            FilledTonalButton(onClick = viewModel::onTest) { Text("Read a test message") }
            FilledTonalButton(onClick = viewModel::stop) { Text("Stop reading") }
            Text(
                when (val current = state) {
                    VoiceState.Idle -> "Ready"
                    VoiceState.Preparing -> "Preparing message…"
                    VoiceState.Speaking -> "Reading…"
                    is VoiceState.Blocked -> current.reason
                    is VoiceState.Failed -> current.reason
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun <T> Chips(items: List<T>, label: (T) -> String, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            FilterChip(selected = selected(item), onClick = {
                onClick(item)
            }, label = { Text(label(item)) })
        }
    }
}
