package com.akib.mycompanion.capture.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.capture.system.RecorderState
import com.akib.mycompanion.capture.system.RecordingStatus
import com.akib.mycompanion.capture.system.ScreenshotBridge
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Something the panel shows once: a message, and whether the panel is done. */
sealed interface PanelEvent {
    val message: String

    data class Close(override val message: String) : PanelEvent

    data class Message(override val message: String) : PanelEvent
}

/** The see-through capture panel (FR-4.2, FR-4.7): new captures only, never the existing ones (FR-4.8). */
@HiltViewModel
class CapturePanelViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: CaptureRepository,
    private val files: CaptureFiles,
    private val clock: Clock,
    private val recording: RecordingStatus,
    private val screenshots: ScreenshotBridge,
) : ViewModel() {
    private val mutableEvents = Channel<PanelEvent>(Channel.BUFFERED)
    val events: Flow<PanelEvent> = mutableEvents.receiveAsFlow()

    val recorder: StateFlow<RecorderState> = recording.state
    val screenshotAvailable: StateFlow<Boolean> = screenshots.available

    init {
        recording.acknowledge()
        viewModelScope.launch {
            recording.state.collect { state ->
                when (state) {
                    is RecorderState.Saved -> {
                        recording.acknowledge()
                        mutableEvents.send(PanelEvent.Close("Voice note saved"))
                    }
                    is RecorderState.Failed -> {
                        recording.acknowledge()
                        mutableEvents.send(PanelEvent.Message(state.reason))
                    }
                    RecorderState.Idle, is RecorderState.Recording -> Unit
                }
            }
        }
    }

    fun saveNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addText(CaptureType.TEXT, text.trim())
            mutableEvents.send(PanelEvent.Close("Note saved"))
        }
    }

    /** [text] is read by the panel while it has focus (Android only lets the focused app read the clipboard). */
    fun saveClipboard(text: String?) {
        viewModelScope.launch {
            if (text.isNullOrBlank()) {
                mutableEvents.send(PanelEvent.Message("There's no text on the clipboard"))
            } else {
                repository.addText(CaptureType.CLIPBOARD, text)
                mutableEvents.send(PanelEvent.Close("Clipboard saved"))
            }
        }
    }

    /** The file the camera app writes to; remembered in case Android closes us while the camera is open. */
    fun newPhotoFile(): File {
        val file = files.newFile(CaptureType.PHOTO, clock.nowMillis())
        savedState[PENDING_PHOTO] = file.name
        return file
    }

    fun onPhotoTaken(taken: Boolean) {
        val name = savedState.get<String>(PENDING_PHOTO) ?: return
        savedState.remove<String>(PENDING_PHOTO)
        val file = files.file(name)
        if (!taken || file.length() == 0L) {
            file.delete()
            return
        }
        viewModelScope.launch {
            repository.addFile(CaptureType.PHOTO, file)
            mutableEvents.send(PanelEvent.Close("Photo saved"))
        }
    }

    /** @return true when the screenshot is on its way and the panel should get out of the picture. */
    fun requestScreenshot(): Boolean {
        val requested = screenshots.request()
        if (!requested) {
            viewModelScope.launch {
                mutableEvents.send(PanelEvent.Message("Screenshots need the quick capture accessibility service"))
            }
        }
        return requested
    }

    fun onMessage(text: String) {
        viewModelScope.launch { mutableEvents.send(PanelEvent.Message(text)) }
    }

    private companion object {
        const val PENDING_PHOTO = "pending_photo"
    }
}
