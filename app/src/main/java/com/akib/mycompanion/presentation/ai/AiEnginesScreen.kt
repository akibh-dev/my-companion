package com.akib.mycompanion.presentation.ai

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.akib.mycompanion.ai.ModelLoader
import com.akib.mycompanion.ai.SpeechRecognizer
import com.akib.mycompanion.ai.SpeechSynth
import com.akib.mycompanion.ai.Summarizer
import com.akib.mycompanion.ai.Transliterator
import com.akib.mycompanion.core.settings.AiEngine
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Switch each model interface between its fallback and its model, and try it on sample input on the
 * phone (plan §7, "a debug screen that runs a model on sample input"). Models are dummies for now.
 */
@HiltViewModel
class AiEnginesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val loader: ModelLoader,
    private val transliterator: Transliterator,
    private val speech: SpeechSynth,
    private val recognizer: SpeechRecognizer,
    private val summarizer: Summarizer,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Settings()
    )

    private val mutableOutputs = MutableStateFlow<Map<AiEngine, String>>(emptyMap())
    val outputs: StateFlow<Map<AiEngine, String>> = mutableOutputs.asStateFlow()

    fun isInstalled(engine: AiEngine) = loader.isInstalled(engine)

    fun onUseModel(engine: AiEngine, useModel: Boolean) {
        viewModelScope.launch { settingsStore.setUseModel(engine, useModel) }
    }

    fun onRun(engine: AiEngine, input: String) {
        viewModelScope.launch {
            val output = when (engine) {
                AiEngine.TRANSLITERATOR -> transliterator.toBangla(input).describe { it }
                AiEngine.SPEECH_SYNTH -> speech.speak(input, Locale.getDefault()).describe { "Spoken" }
                AiEngine.SPEECH_RECOGNIZER -> {
                    val sample = File(context.cacheDir, "sample.m4a").apply { writeBytes(ByteArray(SAMPLE_BYTES)) }
                    recognizer.transcribe(sample).describe { it }
                }
                AiEngine.SUMMARIZER -> summarizer.summarize(input).describe { summary ->
                    buildString {
                        appendLine(summary.title)
                        appendLine(summary.summary)
                        summary.actionItems.forEach { appendLine("• $it") }
                    }.trim()
                }
            }
            mutableOutputs.update { it + (engine to output) }
        }
    }

    private fun <T> AiResult<T>.describe(ready: (T) -> String): String = when (this) {
        is AiResult.Ready -> ready(value)
        is AiResult.NotReady -> "Not ready: $reason"
    }

    private companion object {
        const val SAMPLE_BYTES = 48 * 1024
    }
}

private val SAMPLES = mapOf(
    AiEngine.TRANSLITERATOR to "ami bhalo achi, tumi kemon acho?",
    AiEngine.SPEECH_SYNTH to "Hello from My Companion.",
    AiEngine.SPEECH_RECOGNIZER to "",
    AiEngine.SUMMARIZER to "Meeting with the landlord. He wants the rent by the 5th.\n" +
        "- send photos of the leak\nNeed to call the plumber.",
)

@Composable
fun AiEnginesRoute(modifier: Modifier = Modifier, viewModel: AiEnginesViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val outputs by viewModel.outputs.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Each feature works with a built-in fallback. Switch an engine to its model to try it; " +
                    "today's models are dummies until the local models are ready.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(AiEngine.entries, key = { it.name }) { engine ->
            EngineCard(
                engine = engine,
                useModel = settings.usesModel(engine),
                installed = viewModel.isInstalled(engine),
                output = outputs[engine],
                onUseModel = { viewModel.onUseModel(engine, it) },
                onRun = { viewModel.onRun(engine, it) },
            )
        }
    }
}

@Composable
private fun EngineCard(
    engine: AiEngine,
    useModel: Boolean,
    installed: Boolean,
    output: String?,
    onUseModel: (Boolean) -> Unit,
    onRun: (String) -> Unit,
) {
    var input by remember(engine) { mutableStateOf(SAMPLES.getValue(engine)) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(engine.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !installed -> "No model installed: fallback only"
                            useModel -> "Using the model (dummy)"
                            else -> "Using the fallback"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useModel, onCheckedChange = onUseModel, enabled = installed)
            }
            if (engine != AiEngine.SPEECH_RECOGNIZER) {
                OutlinedTextField(value = input, onValueChange = {
                    input = it
                }, label = { Text("Sample input") }, modifier = Modifier.fillMaxWidth())
            }
            FilledTonalButton(onClick = { onRun(input) }) {
                Text(if (engine == AiEngine.SPEECH_RECOGNIZER) "Run on a sample recording" else "Run")
            }
            output?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
