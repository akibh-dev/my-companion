package dev.akibh.mycompanion.presentation.reminders

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.data.sound.AndroidReminderSounds
import dev.akibh.mycompanion.data.sound.ReminderSoundChannels
import dev.akibh.mycompanion.data.sound.ReminderSoundPreview
import dev.akibh.mycompanion.domain.model.ReminderSound
import dev.akibh.mycompanion.domain.model.SoundPreset
import dev.akibh.mycompanion.ui.components.SecondaryButton
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReminderSoundViewModel @Inject constructor(
    private val library: AndroidReminderSounds,
    private val preview: ReminderSoundPreview,
    private val channels: ReminderSoundChannels,
) : ViewModel() {
    val sounds = library.sounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private var previewJob: Job? = null

    fun import(uri: Uri, onSelected: (ReminderSound) -> Unit) {
        if (mutableBusy.value) return
        stop()
        viewModelScope.launch {
            mutableBusy.value = true
            mutableStatus.value = "Importing audio…"
            try {
                val imported = library.import(uri)
                onSelected(ReminderSound.Imported(imported.id))
                mutableStatus.value = "Imported ${imported.name}"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableStatus.value = failure.message ?: "Audio import failed"
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun play(sound: ReminderSound) {
        stop()
        previewJob = viewModelScope.launch {
            try {
                preview.play(sound)
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                _: Exception
            ) {
                mutableStatus.value = "Couldn't preview this sound. Check the audio file and volume."
            }
        }
    }

    fun stop() {
        previewJob?.cancel()
        preview.stop()
    }

    fun refresh() = library.refresh()

    fun remove(id: String) {
        stop()
        viewModelScope.launch {
            try {
                library.remove(id)
                mutableStatus.value = "Removed sound"
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                failure: Exception
            ) {
                mutableStatus.value = failure.message ?: "Couldn't remove this sound"
            }
        }
    }

    fun openSettings(context: android.content.Context, sound: ReminderSound) {
        val id = channels.channel(sound)
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, id)
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}

/** Self-contained form section; the parent keeps the selected sound with its reminder draft. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSoundPicker(
    selected: ReminderSound,
    onSelected: (ReminderSound) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReminderSoundViewModel = hiltViewModel(),
) {
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(it, onSelected) }
    }
    DisposableEffect(viewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stop()
            if (event == Lifecycle.Event.ON_START) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }
    val choose: (ReminderSound) -> Unit = {
        viewModel.stop()
        onSelected(it)
    }
    // One row to pick and hear the sound; file import and Android's channel settings are small links below.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var open by remember { mutableStateOf(false) }
        var playing by remember { mutableStateOf(false) }
        val soundLabel = when (selected) {
            ReminderSound.Default -> "System default"
            ReminderSound.Silent -> "Silent"
            is ReminderSound.Preset -> SoundPreset.entries.firstOrNull { it.id == selected.id }?.title ?: "Preset"
            is ReminderSound.Imported -> sounds.firstOrNull { it.id == selected.id }?.name ?: "Missing audio"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                SecondaryButton(
                    onClick = { open = true },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Sound: $soundLabel" },
                ) {
                    Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(soundLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("System default") }, onClick = {
                        choose(ReminderSound.Default)
                        open = false
                    })
                    DropdownMenuItem(text = { Text("Silent") }, onClick = {
                        choose(ReminderSound.Silent)
                        open = false
                    })
                    SoundPreset.entries.forEach { preset ->
                        DropdownMenuItem(text = { Text(preset.title) }, onClick = {
                            choose(ReminderSound.Preset(preset.id))
                            open = false
                        })
                    }
                    sounds.forEach { imported ->
                        DropdownMenuItem(
                            text = { Text(imported.name + if (imported.available) "" else " (missing)") },
                            onClick = {
                                choose(ReminderSound.Imported(imported.id))
                                open = false
                            },
                        )
                    }
                }
            }
            FilledTonalIconButton(
                onClick = {
                    if (playing) viewModel.stop() else viewModel.play(selected)
                    playing = !playing
                },
                enabled = playing || (selected != ReminderSound.Silent && !busy),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                if (playing) {
                    Icon(painterResource(R.drawable.ic_stop), contentDescription = "Stop sound")
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Preview sound")
                }
            }
        }
        // A new choice stops any preview, so the button goes back to "play".
        LaunchedEffect(selected) { playing = false }
        if (selected is ReminderSound.Imported && sounds.none { it.id == selected.id }) {
            Text(
                "Selected audio is missing. The default notification sound will be used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FlowRow {
            TextButton(onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) }, enabled = !busy) {
                Text(if (busy) "Importing…" else "Import audio")
            }
            if (selected is ReminderSound.Imported) {
                TextButton(onClick = {
                    viewModel.remove(selected.id)
                    choose(ReminderSound.Default)
                }) { Text("Remove") }
            }
            TextButton(onClick = { viewModel.openSettings(context, selected) }) { Text("Sound settings") }
        }
        Text(
            "MP3, OGG, WAV or M4A, up to 10 MB and 30 seconds. Imported sounds are kept in your Notifications " +
                "folder. Your phone's notification settings take priority.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
