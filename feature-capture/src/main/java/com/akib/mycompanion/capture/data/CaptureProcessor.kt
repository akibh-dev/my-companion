package com.akib.mycompanion.capture.data

import com.akib.mycompanion.ai.AiResult
import com.akib.mycompanion.ai.SpeechRecognizer
import com.akib.mycompanion.core.log.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Work done after a capture is saved (FR-4.4): a voice note is turned into text when speech to text
 * is ready. Until then (the model is a dummy today) the note simply stays audio-only.
 */
@Singleton
class CaptureProcessor @Inject constructor(
    private val repository: CaptureRepository,
    private val recognizer: SpeechRecognizer,
    private val log: AppLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Transcribes in the background, so the recording service and panel can close straight away. */
    fun transcribeLater(id: Long) {
        scope.launch { transcribe(id) }
    }

    /** @return true when a transcript was stored. */
    suspend fun transcribe(id: Long): Boolean {
        val capture = repository.get(id) ?: return false
        val file = repository.fileOf(capture) ?: return false
        return when (val result = recognizer.transcribe(file)) {
            is AiResult.Ready -> {
                if (result.value.isBlank()) return false
                repository.setTranscript(id, result.value)
                log.i(TAG, "transcribed #$id")
                true
            }
            is AiResult.NotReady -> {
                log.i(TAG, "keeping #$id as audio only: ${result.reason}")
                false
            }
        }
    }

    private companion object {
        const val TAG = "Capture"
    }
}
