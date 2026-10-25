package com.akib.mycompanion.vault.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.VaultItem
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One item: its content in an in-app viewer (FR-2.9), and its title, notes, tags and folder. */
@Composable
fun VaultItemRoute(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: VaultItemViewModel = hiltViewModel()) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val reminderStatus by viewModel.reminderStatus.collectAsStateWithLifecycle()
    if (!unlocked) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    SecureVaultScreen(viewModel.autoLock)
    val current = item ?: return
    var title by remember(current.id) { mutableStateOf(current.title) }
    var notes by remember(current.id) { mutableStateOf(current.notes) }
    var tags by remember(current.id) { mutableStateOf(current.tags.joinToString(", ")) }
    var folder by remember(current.id) { mutableStateOf(current.folder) }
    val edited = current.copy(
        title = title.trim(),
        notes = notes.trim(),
        tags = tags.split(',').map(String::trim).filter(String::isNotEmpty),
        folder = folder.trim(),
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                current.mime.ifBlank {
                    "application/octet-stream"
                }
            )
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch { context.contentResolver.openOutputStream(uri)?.use { viewModel.export(current, it) } }
        }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Content(current, viewModel) }
        if (current.type == ItemType.LINK) {
            item {
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.url))) }
                ) { Text("Open link") }
            }
        }
        if (current.type == ItemType.FILE || current.type == ItemType.IMAGE) {
            item {
                OutlinedButton(onClick = { export.launch(current.fileName) }) { Text("Export a copy…") }
                Text("An exported copy is no longer encrypted.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OutlinedTextField(title, {
                title = it
            }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()) }
        item {
            OutlinedTextField(tags, {
                tags = it
            }, label = { Text("Tags, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(folder, {
                folder = it
            }, label = { Text("Folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedButton(onClick = {
                pickDateTime(context) { at -> viewModel.remind(current, at, formatDateTime(context, at)) }
            }) { Text("Remind me…") }
            reminderStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    current.pinned,
                    { viewModel.save(edited.copy(pinned = !current.pinned)) },
                    { Text("Pinned") }
                )
                FilterChip(
                    current.favourite,
                    { viewModel.save(edited.copy(favourite = !current.favourite)) },
                    { Text("Favourite") }
                )
            }
        }
        item {
            Text("Added ${formatDate(current.created)}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.save(edited)
                    onDone()
                }, enabled = edited != current) { Text("Save") }
                OutlinedButton(onClick = {
                    viewModel.trash(current)
                    onDone()
                }) { Text("Move to trash") }
            }
        }
    }
}

@Composable
private fun Content(item: VaultItem, viewModel: VaultItemViewModel) {
    when (item.type) {
        ItemType.TEXT -> Text(item.text, style = MaterialTheme.typography.bodyLarge)
        ItemType.LINK -> Column {
            item.preview?.let {
                Text(it.title, style = MaterialTheme.typography.titleMedium)
                Text(it.description, style = MaterialTheme.typography.bodyMedium)
                Text(it.site, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        ItemType.IMAGE -> {
            var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(item.id) {
                bitmap = withContext(Dispatchers.Default) {
                    viewModel.content(item).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = item.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
                ?: Text("Opening…")
        }
        ItemType.FILE -> if (item.mime == "application/pdf" || item.fileName.endsWith(".pdf", ignoreCase = true)) {
            PdfPages(item, viewModel)
        } else {
            Text("${item.fileName} · ${item.size / 1024} KB", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * PDF pages, rendered in the app. Android's PDF renderer needs a file, so the PDF is decrypted into
 * the app's private cache (never shared storage) and deleted as soon as the viewer closes.
 */
@Composable
private fun PdfPages(item: VaultItem, viewModel: VaultItemViewModel) {
    val context = LocalContext.current
    var pages by remember(item.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    val file =
        remember(item.id) { File(File(context.cacheDir, "vault-view").apply { mkdirs() }, "${UUID.randomUUID()}.pdf") }
    DisposableEffect(file) { onDispose { file.delete() } }
    LaunchedEffect(item.id) {
        pages = withContext(Dispatchers.IO) {
            file.outputStream().use { viewModel.export(item, it) }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until minOf(renderer.pageCount, MAX_PAGES)).map { index ->
                        renderer.openPage(index).use { page ->
                            Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.also { file.delete() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pages.isEmpty()) Text("Opening…")
        pages.forEach {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val MAX_PAGES = 30

/** The platform date picker, then the time picker; [onPicked] gets the chosen moment in epoch millis. */
private fun pickDateTime(context: Context, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = Calendar.getInstance().apply {
                        clear()
                        set(year, month, day, hour, minute)
                    }
                    onPicked(picked.timeInMillis)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context),
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = now.timeInMillis - 1_000 }.show()
}

/** "3 Nov 2026, 9:30" in the phone's own date and 12/24-hour format. */
private fun formatDateTime(context: Context, millis: Long): String {
    val date = DateFormat.getMediumDateFormat(context).format(Date(millis))
    val time = DateFormat.getTimeFormat(context).format(Date(millis))
    return "$date, $time"
}

/** Trash (FR-2.14) and encrypted backup and restore with a passphrase (FR-2.15). */
@Composable
fun VaultTrashBackupRoute(modifier: Modifier = Modifier, viewModel: VaultBackupViewModel = hiltViewModel()) {
    SecureVaultScreen(viewModel.autoLock)
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var passphrase by remember { mutableStateOf("") }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { viewModel.export(it, passphrase) }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                viewModel.restore(it, passphrase)
            }
        }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Backup", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(passphrase, {
                passphrase = it
            }, label = {
                Text(
                    "Passphrase (at least 8 characters)"
                )
            }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                "Without the passphrase the backup can't be opened, by anyone. Write it down.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    exporter.launch("my-companion-vault.mcvault")
                }, enabled = passphrase.length >= 8) { Text("Save backup") }
                OutlinedButton(
                    onClick = { importer.launch(arrayOf("*/*")) },
                    enabled = passphrase.length >= 8
                ) { Text("Restore") }
            }
            status?.let { Text(it) }
        }
        item { Text("Trash (kept 30 days)", style = MaterialTheme.typography.titleMedium) }
        if (trash.isEmpty()) item { Text("Empty") }
        items(trash, key = { it.id }) { item ->
            Column {
                Text(item.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                Text("Deleted ${formatDate(item.deletedAt ?: 0)}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.restore(item) }) { Text("Restore") }
                    OutlinedButton(onClick = { viewModel.deleteForever(item) }) { Text("Delete forever") }
                }
            }
        }
    }
}
