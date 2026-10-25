package com.akib.mycompanion.capture.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.capture.domain.Capture
import com.akib.mycompanion.capture.domain.CaptureType
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One capture (FR-4.3): play the voice note or see the photo, read the transcript (FR-4.4), keep a
 * summary when summaries are installed (FR-4.5, FR-4.6), edit the text, delete it, or move it to the
 * vault (FR-4.9). Needs the phone unlocked (FR-4.8).
 */
@Composable
fun CaptureDetailRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureDetailViewModel = hiltViewModel(),
) {
    if (rememberIsLocked()) {
        LockedNotice(modifier)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    LaunchedEffect(action) {
        if (action == DetailAction.Finished) onDone()
    }
    when (val current = state) {
        DetailState.Loading -> Unit
        DetailState.Missing -> Text("This capture isn't here any more.", modifier.padding(16.dp))
        is DetailState.Loaded -> CaptureDetail(current.capture, suggestion, action, viewModel, modifier)
    }
}

@Composable
private fun CaptureDetail(
    capture: Capture,
    suggestion: Summary?,
    action: DetailAction,
    viewModel: CaptureDetailViewModel,
    modifier: Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    var confirmDelete by remember { mutableStateOf(false) }
    var title by remember(capture.id) { mutableStateOf(capture.title) }
    var text by remember(capture.id) { mutableStateOf(capture.text) }
    val edited = capture.copy(title = title.trim(), text = text.trim())

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${capture.type.title} · ${formatDateTime(capture.created, zone)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (capture.movedToVault) {
            Text(capture.displayTitle, style = MaterialTheme.typography.titleLarge)
            Text(
                "Moved to the vault. Its content is only there now; this entry just marks where it went.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { confirmDelete = true }) { Text("Remove this entry") }
        } else {
            Media(capture, viewModel)
            if (capture.transcript.isNotBlank()) {
                Section("Transcript", capture.transcript)
            } else if (capture.type == CaptureType.VOICE) {
                Text(
                    "Audio only: speech to text isn't installed yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (capture.summary.isNotBlank()) {
                Section("Summary", capture.summary)
            }
            if (capture.actions.isNotEmpty()) {
                Section("To do", capture.actions.joinToString("\n") { "• $it" })
            }
            if (suggestion != null) {
                FilledTonalButton(onClick = viewModel::keepSummary) { Text("Summarise") }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text(capture.displayTitle) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (capture.type.hasFile) "Notes" else capture.type.title) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { viewModel.save(edited) }, enabled = edited != capture) { Text("Save changes") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.moveToVault(edited) },
                    enabled = action != DetailAction.Working,
                ) { Text(if (action == DetailAction.Working) "Moving…" else "Move to vault") }
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
            Text(
                "Move to vault encrypts it in your vault (even while the vault is locked) and removes it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action is DetailAction.Failed) {
            Text(action.reason, color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this capture?") },
            text = { Text("It's deleted from this phone for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(capture)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Media(capture: Capture, viewModel: CaptureDetailViewModel) {
    when (capture.type) {
        CaptureType.TEXT, CaptureType.CLIPBOARD -> Unit
        CaptureType.VOICE, CaptureType.PHOTO, CaptureType.SCREENSHOT -> {
            val file = remember(capture.id, capture.file) { viewModel.fileOf(capture) }
            when {
                file == null -> Text("The file is missing.", color = MaterialTheme.colorScheme.error)
                capture.type == CaptureType.VOICE -> VoicePlayer(file)
                else -> CaptureImage(file, capture.displayTitle)
            }
        }
    }
}

/** Plays the voice note with the phone's media player; leaving the screen stops it. */
@Composable
private fun VoicePlayer(file: File) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(file) {
        onDispose {
            player?.release()
            player = null
        }
    }
    Button(
        onClick = {
            val playing = player
            if (playing != null) {
                playing.release()
                player = null
            } else {
                player = runCatching {
                    MediaPlayer().apply {
                        setDataSource(file.path)
                        setOnCompletionListener { done ->
                            done.release()
                            player = null
                        }
                        prepare()
                        start()
                    }
                }.getOrNull()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (player != null) "Stop" else "Play voice note") }
}

/** A photo or screenshot, scaled down while decoding so a big camera photo doesn't use much memory. */
@Composable
private fun CaptureImage(file: File, description: String) {
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { decodeSampled(file) }.getOrNull() }
    }
    val image = bitmap
    if (image == null) {
        Text("Opening…")
    } else {
        Image(
            image.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun decodeSampled(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private const val MAX_IMAGE_SIDE = 2048
