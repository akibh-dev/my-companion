package dev.akibh.mycompanion.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.PrimaryButton
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.Section
import dev.akibh.mycompanion.ui.components.SectionGap
import java.time.LocalDate

/** Settings → "Export & backup": connects [ExportScreen] to the system file picker. */
@Composable
fun ExportRoute(modifier: Modifier = Modifier, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP)) { uri ->
        uri?.let(viewModel::onExportAll)
    }
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let(viewModel::onExportSettings)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onRestorePicked)
    }
    ExportScreen(
        state = state,
        onExportAll = { exportAll.launch("my-companion-export-${LocalDate.now()}.zip") },
        onExportSettings = { exportSettings.launch("my-companion-settings-${LocalDate.now()}.json") },
        // Some file managers label .json as text or as unknown binary.
        onRestore = { restore.launch(arrayOf(JSON, "text/plain", "application/octet-stream")) },
        onConfirmRestore = viewModel::onConfirmRestore,
        onCancelRestore = viewModel::onCancelRestore,
        modifier = modifier,
    )
}

@Composable
fun ExportScreen(
    state: ExportUiState,
    onExportAll: () -> Unit,
    onExportSettings: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = PageMargin, top = 8.dp, end = PageMargin, bottom = SectionGap),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        Section("Export my data", note = "The exported file itself is not encrypted.") {
            Text(
                "Saves one ZIP file with your people, reminders, playlists, rules, places, auto-reply " +
                    "templates and settings as readable JSON, wherever you choose.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton(onClick = onExportAll, enabled = !state.busy) {
                Text("Export my data…")
            }
        }
        Section(
            "Settings backup",
            note = "Restoring replaces your current settings.",
        ) {
            Text(
                "The feature switches, voice mode and auto-reply settings in one small file.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton(onClick = onExportSettings, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Save settings…")
            }
            SecondaryButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Restore settings…")
            }
        }
        state.status?.let { Note(it, Modifier.padding(horizontal = 8.dp)) }
    }

    state.pendingRestore?.let { bundle ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Replace your settings?") },
            text = { Text(restoreSummary(bundle)) },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("Replace") } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel") } },
        )
    }
}

/** What the backup would switch on, so "Replace" isn't a blind choice. */
fun restoreSummary(bundle: SettingsBundle): String {
    val features = bundle.app.enabled.sortedBy { it.ordinal }.joinToString(", ") { it.title }.ifEmpty { "none" }
    val paused = if (bundle.app.paused) "Paused." else "Not paused."
    val bookmarks = bundle.quran?.let { " ${it.bookmarks.size} Quran bookmarks replace yours." }.orEmpty()
    val books = bundle.library?.let {
        " ${it.bookCount} library books replace yours; locate each file again to read it."
    }.orEmpty()
    return "The backup's settings will replace your current ones, including voice mode and auto-reply.\n\n" +
        "Features on: $features. $paused$bookmarks$books"
}

private const val ZIP = "application/zip"
private const val JSON = "application/json"
