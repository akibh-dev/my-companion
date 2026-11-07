package com.akib.mycompanion.presentation.reminders

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.data.sound.AndroidReminderSounds
import com.akib.mycompanion.data.sound.ReminderSoundChannels
import com.akib.mycompanion.data.sound.ReminderSoundPreview
import com.akib.mycompanion.domain.model.ReminderSound
import com.akib.mycompanion.domain.model.SoundPreset
import dagger.hilt.android.lifecycle.HiltViewModel
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sound", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected == ReminderSound.Default,
                { choose(ReminderSound.Default) },
                label = { Text("System default") }
            )
            FilterChip(selected == ReminderSound.Silent, { choose(ReminderSound.Silent) }, label = { Text("Silent") })
            SoundPreset.entries.forEach { preset ->
                val sound = ReminderSound.Preset(preset.id)
                FilterChip(selected == sound, { choose(sound) }, label = { Text(preset.title) })
            }
        }
        sounds.forEach { imported ->
            val sound = ReminderSound.Imported(imported.id)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected == sound, { choose(sound) }, label = {
                    Text(imported.name + if (imported.available) "" else " (missing — uses default)")
                })
                if (selected != sound) FilledTonalButton(onClick = { viewModel.remove(imported.id) }) { Text("Remove") }
            }
        }
        if (selected is ReminderSound.Imported && sounds.none { it.id == selected.id }) {
            Text("Selected audio is missing. The default notification sound will be used.")
        }
        Text("MP3, OGG, WAV or AAC/M4A · up to 10 MB and 30 seconds", style = MaterialTheme.typography.bodySmall)
        Text(
            "Imported audio is copied to Notifications/My Companion and may remain after uninstall.",
            style = MaterialTheme.typography.bodySmall
        )
        FilledTonalButton(onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) }, enabled = !busy) {
            Text(if (busy) "Importing…" else "Choose audio file")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                viewModel.play(selected)
            }, enabled = selected != ReminderSound.Silent && !busy) { Text("Preview sound") }
            FilledTonalButton(onClick = viewModel::stop) { Text("Stop sound") }
        }
        FilledTonalButton(onClick = {
            viewModel.openSettings(context, selected)
        }) { Text("Sound notification settings") }
        Text("Your phone's notification sound settings take priority.", style = MaterialTheme.typography.bodySmall)
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
