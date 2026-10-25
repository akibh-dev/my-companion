package com.akib.mycompanion.voice.ui

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
import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.SpeechSynth
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import com.akib.mycompanion.messaging.ChatApp
import com.akib.mycompanion.messaging.IncomingMessage
import com.akib.mycompanion.voice.domain.Privacy
import com.akib.mycompanion.voice.domain.VoiceSettings
import com.akib.mycompanion.voice.domain.VoiceText
import com.akib.mycompanion.voice.system.AudioWatcher
import com.akib.mycompanion.voice.system.VoiceSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val speech: SpeechSynth,
    private val audio: AudioWatcher,
) : ViewModel() {
    val app: StateFlow<Settings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Settings()
    )
    val settings: StateFlow<VoiceSettings> =
        voiceSettings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettings())
    private val mutableTest = MutableStateFlow<String?>(null)
    val test: StateFlow<String?> = mutableTest.asStateFlow()

    fun onVoiceMode(on: Boolean) {
        viewModelScope.launch { settingsStore.setEnabled(Feature.VOICE_MODE, on) }
    }

    fun update(change: (VoiceSettings) -> VoiceSettings) {
        viewModelScope.launch { voiceSettings.update(change) }
    }

    /** Voice test (plan §11): reads a sample like a real message, ignoring the privacy setting. */
    fun onTest() {
        viewModelScope.launch {
            val sample =
                IncomingMessage(
                    "test",
                    ChatApp.WHATSAPP,
                    "Rahim",
                    "Are you coming? 😊 https://example.com",
                    "Rahim",
                    false,
                    0,
                    false
                )
            val text = VoiceText.sentence(sample, settings.value.maxChars)
            mutableTest.value = text
            val result = audio.ducking { speech.speak(text, VoiceText.language(text)) }
            if (result is AiResult.NotReady) mutableTest.value = "$text\n(${result.reason})"
        }
    }
}

/** Voice settings (plan §11): apps, senders, privacy, speed, and a test. */
@Composable
fun VoiceRoute(modifier: Modifier = Modifier, viewModel: VoiceViewModel = hiltViewModel()) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
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
                    viewModel.update { it.copy(senders = text.split(',').map(String::trim).filter(String::isNotEmpty)) }
                },
                label = { Text("Only these people (empty = everyone)") },
                modifier = Modifier.fillMaxWidth(),
            )
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
            test?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
