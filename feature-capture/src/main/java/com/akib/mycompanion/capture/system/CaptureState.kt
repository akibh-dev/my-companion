package com.akib.mycompanion.capture.system

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where the voice recorder is, shared by [CaptureRecordingService] and the panel. */
sealed interface RecorderState {
    data object Idle : RecorderState

    data class Recording(val startedAt: Long) : RecorderState

    data class Saved(val id: Long) : RecorderState

    data class Failed(val reason: String) : RecorderState
}

@Singleton
class RecordingStatus @Inject constructor() {
    private val mutableState = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = mutableState.asStateFlow()

    val isRecording: Boolean get() = state.value is RecorderState.Recording

    internal fun set(value: RecorderState) {
        mutableState.value = value
    }

    /** The panel has shown the last result ("Saved", "Failed"); a running recording is kept. */
    fun acknowledge() {
        mutableState.update { if (it is RecorderState.Recording) it else RecorderState.Idle }
    }
}

/**
 * Lets the panel ask the accessibility service for a screenshot (FR-4.7). Only the service can take
 * one, and only while it's switched on in the phone's accessibility settings.
 */
@Singleton
class ScreenshotBridge @Inject constructor() {
    private val mutableAvailable = MutableStateFlow(false)
    val available: StateFlow<Boolean> = mutableAvailable.asStateFlow()

    @Volatile private var onRequest: (() -> Unit)? = null

    internal fun attach(request: () -> Unit) {
        onRequest = request
        mutableAvailable.value = true
    }

    internal fun detach() {
        onRequest = null
        mutableAvailable.value = false
    }

    /** Takes a screenshot shortly, once the panel has gone. @return false when the service isn't on. */
    fun request(): Boolean {
        val request = onRequest ?: return false
        request()
        return true
    }
}
