package com.akib.mycompanion.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** Home → "Export & backup": connects [ExportScreen] to the system file picker. */
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(
            title = "Export my data",
            body = "Saves one ZIP file with your people, reminders, playlists, rules, places, auto-reply " +
                "templates and settings as readable JSON, wherever you choose.",
        ) {
            Button(onClick = onExportAll, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Export my data…")
            }
            Text(
                "The vault isn't included: it has its own encrypted backup under Vault → Trash & backup. " +
                    "The exported file itself is not encrypted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Section(
            title = "Settings backup",
            body = "The feature switches, voice mode and auto-reply settings in one small file. Restoring " +
                "replaces your current settings.",
        ) {
            OutlinedButton(onClick = onExportSettings, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Save settings…")
            }
            OutlinedButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Restore settings…")
            }
        }
        state.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
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

@Composable
private fun Section(title: String, body: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            content()
        }
    }
}

/** What the backup would switch on, so "Replace" isn't a blind choice. */
fun restoreSummary(bundle: SettingsBundle): String {
    val features = bundle.app.enabled.sortedBy { it.ordinal }.joinToString(", ") { it.title }.ifEmpty { "none" }
    val paused = if (bundle.app.paused) "Paused." else "Not paused."
    return "The backup's settings will replace your current ones, including voice mode and auto-reply.\n\n" +
        "Features on: $features. $paused"
}

private const val ZIP = "application/zip"
private const val JSON = "application/json"
