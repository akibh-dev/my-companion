package com.akib.mycompanion.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.ai.models.IndicXlitPrototype
import com.akib.mycompanion.ai.models.TransliterationPreview
import dagger.hilt.android.lifecycle.HiltViewModel
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
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Banglish preview (experimental)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Try offline Banglish conversion. This preview treats every English-letter word as Banglish. " +
                    "Automatic English detection is still being evaluated.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Banglish message") },
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(onClick = { viewModel.preview(input) }, enabled = !running && input.isNotBlank()) {
                Text(if (running) "Converting…" else "Preview Bangla")
            }
            val result = (state as? BanglishPreviewState.Finished)?.result
            when (result) {
                is TransliterationPreview.Ready -> {
                    Text(result.text, style = MaterialTheme.typography.bodyLarge)
                    Text("Model load: ${result.loadMillis} ms · Conversion: ${result.inferenceMillis} ms")
                    result.words.distinctBy { it.source.lowercase() }.forEach { word ->
                        Text("${word.source}: ${word.candidates.joinToString(" / ")}")
                    }
                }
                is TransliterationPreview.Unavailable -> Text(result.reason, color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
        }
    }
}
