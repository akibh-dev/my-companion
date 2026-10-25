package com.akib.mycompanion.capture.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.capture.data.CAPTURE_AUTHORITY_SUFFIX
import com.akib.mycompanion.capture.system.CaptureRecordingService
import com.akib.mycompanion.capture.system.RecorderState
import kotlinx.coroutines.delay

private enum class PanelMode { BUTTONS, NOTE }

/**
 * A card at the bottom of a dimmed, see-through screen: Note, Voice and Photo as big buttons, then
 * Clipboard and Screenshot (FR-4.2, FR-4.7). Tapping outside the card closes it.
 */
@Composable
internal fun CapturePanel(
    onClose: () -> Unit,
    onOpenSpace: () -> Unit,
    viewModel: CapturePanelViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val close by rememberUpdatedState(onClose)
    val recorder by viewModel.recorder.collectAsStateWithLifecycle()
    val screenshotAvailable by viewModel.screenshotAvailable.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(PanelMode.BUTTONS) }
    var note by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PanelEvent.Close -> {
                    Toast.makeText(context.applicationContext, event.message, Toast.LENGTH_SHORT).show()
                    close()
                }
                is PanelEvent.Message -> message = event.message
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(TakePictureWithGrant()) { taken ->
        viewModel.onPhotoTaken(taken)
    }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording(context) else viewModel.onMessage("Voice notes need the microphone permission")
    }

    Box(Modifier.fillMaxSize()) {
        // The see-through part: a tap here closes the panel (a recording keeps going in its notification).
        Spacer(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Quick capture",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSpace) { Text("Capture space") }
                }
                val recording = recorder as? RecorderState.Recording
                when {
                    recording != null -> RecordingControls(recording.startedAt) {
                        context.startService(CaptureRecordingService.stopIntent(context))
                    }
                    mode == PanelMode.NOTE -> NoteEditor(
                        text = note,
                        onText = { note = it },
                        onCancel = { mode = PanelMode.BUTTONS },
                        onSave = { viewModel.saveNote(note) },
                    )
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BigButton("📝", "Note") {
                                message = null
                                mode = PanelMode.NOTE
                            }
                            BigButton("🎙", "Voice") {
                                message = null
                                if (hasMicrophone(context)) {
                                    startRecording(context)
                                } else {
                                    askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            BigButton("📷", "Photo") {
                                message = null
                                val file = viewModel.newPhotoFile()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    context.packageName + CAPTURE_AUTHORITY_SUFFIX,
                                    file,
                                )
                                runCatching { takePicture.launch(uri) }.onFailure {
                                    viewModel.onPhotoTaken(false)
                                    viewModel.onMessage("No camera app found")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    message = null
                                    viewModel.saveClipboard(readClipboard(context))
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Clipboard") }
                            OutlinedButton(
                                onClick = {
                                    message = null
                                    if (viewModel.requestScreenshot()) onClose()
                                },
                                enabled = screenshotAvailable,
                                modifier = Modifier.weight(1f),
                            ) { Text("Screenshot") }
                        }
                        if (!screenshotAvailable) {
                            Text(
                                "Screenshots work when the quick capture accessibility service is on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun RowScope.BigButton(symbol: String, label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(BIG_BUTTON_HEIGHT.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NoteEditor(text: String, onText: (String) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text("Note") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel) { Text("Back") }
        Button(onClick = onSave, enabled = text.isNotBlank()) { Text("Save") }
    }
}

@Composable
private fun RecordingControls(startedAt: Long, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    val seconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text(
        "Recording  ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error,
    )
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(BIG_BUTTON_HEIGHT.dp),
        shape = RoundedCornerShape(20.dp),
    ) { Text("Stop and save", style = MaterialTheme.typography.titleMedium) }
    Text(
        "You can close this; recording goes on until you tap Stop here or in the notification.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun hasMicrophone(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun startRecording(context: Context) {
    ContextCompat.startForegroundService(context, CaptureRecordingService.startIntent(context))
}

/** Works only while the panel has focus: Android hides the clipboard from apps in the background. */
private fun readClipboard(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private const val BIG_BUTTON_HEIGHT = 96
private const val TICK_MILLIS = 500L

/**
 * The camera app writes the photo into our file through [MediaStore.EXTRA_OUTPUT]. Android used to
 * grant it access to that URI implicitly; Android 17 warns that this ends with Android 18, so the
 * grant is set explicitly here (ClipData plus read/write flags).
 */
internal class TakePictureWithGrant : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        clipData = ClipData.newRawUri("photo", input)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
