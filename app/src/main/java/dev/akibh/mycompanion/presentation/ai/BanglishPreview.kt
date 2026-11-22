package dev.akibh.mycompanion.presentation.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.ai.models.IndicXlitPrototype
import dev.akibh.mycompanion.ai.models.TransliterationPreview
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BanglishPreviewState {
    data object Idle : BanglishPreviewState

    data object Running : BanglishPreviewState

    data class Finished(val result: TransliterationPreview) : BanglishPreviewState
}

@HiltViewModel
class BanglishPreviewViewModel @Inject constructor(private val prototype: IndicXlitPrototype) : ViewModel() {
    private val mutableState = MutableStateFlow<BanglishPreviewState>(BanglishPreviewState.Idle)
    val state = mutableState.asStateFlow()

    fun preview(text: String) {
        if (state.value == BanglishPreviewState.Running) return
        mutableState.value = BanglishPreviewState.Running
        viewModelScope.launch {
            mutableState.value = BanglishPreviewState.Finished(prototype.preview(text))
        }
    }
}

@Composable
fun BanglishPreviewCard(viewModel: BanglishPreviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("ami bhalo achi, tumi kemon acho?") }
    val running = state == BanglishPreviewState.Running
    Section(
        "Banglish preview (experimental)",
        note = "Try offline Banglish conversion. This preview treats every English-letter word as Banglish. " +
            "Automatic English detection is still being evaluated.",
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Banglish message") },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(onClick = { viewModel.preview(input) }, enabled = !running && input.isNotBlank()) {
            Text(if (running) "Converting…" else "Preview Bangla")
        }
        val result = (state as? BanglishPreviewState.Finished)?.result
        when (result) {
            is TransliterationPreview.Ready -> {
                Text(result.text, style = MaterialTheme.typography.bodyLarge)
                Note("Model load: ${result.loadMillis} ms · Conversion: ${result.inferenceMillis} ms")
                result.words.distinctBy { it.source.lowercase() }.forEach { word ->
                    Text(
                        "${word.source}: ${word.candidates.joinToString(" / ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is TransliterationPreview.Unavailable -> Text(result.reason, color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
    }
}
