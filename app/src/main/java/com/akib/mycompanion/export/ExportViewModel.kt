package com.akib.mycompanion.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportUiState(
    /** Something is being written or read; the buttons wait. */
    val busy: Boolean = false,
    /** What happened last, in a sentence. */
    val status: String? = null,
    /** A settings backup that was read and is waiting for "Replace" in the confirmation dialog. */
    val pendingRestore: SettingsBundle? = null,
)

/** Export & backup screen: writes and reads the files the user picks with the system file picker. */
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: DataExporter,
    private val backup: SettingsBackup,
    private val log: AppLog,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = mutableState.asStateFlow()

    fun onExportAll(uri: Uri) = work("Saving your data…") {
        val count = withContext(io) {
            context.contentResolver.openOutputStream(uri)?.use { exporter.writeZip(it) }
        } ?: error("Couldn't open that file")
        log.i(TAG, "data exported ($count files)")
        "Saved $count files. The vault isn't included: it has its own encrypted backup."
    }

    fun onExportSettings(uri: Uri) = work("Saving settings…") {
        val json = backup.exportJson()
        withContext(io) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        } ?: error("Couldn't open that file")
        log.i(TAG, "settings backup saved")
        "Settings saved"
    }

    /** A backup was picked: read it, then ask before overwriting anything. */
    fun onRestorePicked(uri: Uri) = work("Reading the backup…") {
        val text = withContext(io) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } ?: error("Couldn't open that file")
        val bundle = backup.parse(text)
        mutableState.update { it.copy(pendingRestore = bundle) }
        null
    }

    fun onConfirmRestore() {
        val bundle = mutableState.value.pendingRestore ?: return
        mutableState.update { it.copy(pendingRestore = null) }
        work("Restoring settings…") {
            backup.restore(bundle)
            log.i(TAG, "settings restored from a backup")
            "Settings restored"
        }
    }

    fun onCancelRestore() = mutableState.update { it.copy(pendingRestore = null, status = null) }

    /** Runs [block] with the screen marked busy; its result (or the error) becomes the status line. */
    private fun work(progress: String, block: suspend () -> String?) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, status = progress) }
        viewModelScope.launch {
            val status = runCatching { block() }.getOrElse { it.message ?: "Something went wrong" }
            mutableState.update { it.copy(busy = false, status = status) }
        }
    }

    private companion object {
        const val TAG = "Export"
    }
}
