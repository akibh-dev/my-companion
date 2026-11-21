package dev.akibh.mycompanion.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.Label
import dev.akibh.mycompanion.library.domain.LabelKind
import dev.akibh.mycompanion.library.domain.fileSize
import dev.akibh.mycompanion.library.reader.TextDialog
import dev.akibh.mycompanion.ui.theme.CompanionRed
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Everything about one book: status, shelves, tags, the file, and the ways to fix or remove it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BookDetailsSheet(
    book: Book,
    availability: Availability,
    labels: List<Label>,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    onLocate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val seconds by remember(book.id) { viewModel.secondsRead(book) }.collectAsStateWithLifecycle(0L)
    var editing by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf<LabelKind?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Cover(book, Modifier.width(96.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge)
                    if (book.author.isNotBlank()) {
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${(book.progress * 100).roundToInt()}% read · ${readTime(seconds)}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    DotProgress(book.progress.toFloat(), Modifier.padding(top = 6.dp))
                }
                IconButton(
                    onClick = { editing = true }
                ) { Icon(Icons.Filled.Edit, contentDescription = "Edit title and author") }
            }
            if (availability != Availability.OK) {
                val text = missingText(book, availability)
                Text(text.title, style = MaterialTheme.typography.titleSmall, color = CompanionRed)
                Text(
                    text.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onLocate, Modifier.fillMaxWidth()) { Text("Locate file") }
            } else {
                Button(onClick = onOpen, Modifier.fillMaxWidth()) {
                    Text(if (book.lastOpenedAt == null) "Start reading" else "Continue reading")
                }
            }

            SectionLabel("Status", Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookStatus.entries.forEach { status ->
                    Pill(status.label, book.status == status, { viewModel.setStatus(book, status) })
                }
            }
            LabelGroup("Shelves", LabelKind.SHELF, labels, book, viewModel) { newLabel = LabelKind.SHELF }
            LabelGroup("Tags", LabelKind.TAG, labels, book, viewModel) { newLabel = LabelKind.TAG }

            SectionLabel("File", Modifier.padding(top = 8.dp))
            Text(book.fileName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${book.format.label} · ${book.size.fileSize()} · added ${DateFormat.getDateInstance().format(
                    Date(book.addedAt)
                )}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLocate) { Text("Link another file") }
                OutlinedButton(onClick = { confirmRemove = true }) { Text("Remove", color = CompanionRed) }
            }
        }
    }
    if (editing) {
        var title by rememberSaveable { mutableStateOf(book.title) }
        var author by rememberSaveable { mutableStateOf(book.author) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Title and author") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(book, title, author)
                    editing = false
                }, enabled = title.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
    newLabel?.let { kind ->
        TextDialog(
            title = if (kind == LabelKind.SHELF) "New shelf" else "New tag",
            initial = "",
            onDone = {
                viewModel.newLabel(it, kind, book)
                newLabel = null
            },
            onDismiss = { newLabel = null },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove \"${book.title}\"?") },
            text = {
                Text(
                    "Its place, bookmarks, shelves and tags are removed. The file itself isn't touched; " +
                        "reading time stays in your stats."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onDismiss()
                    viewModel.remove(book)
                }) { Text("Remove", color = CompanionRed) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelGroup(
    title: String,
    kind: LabelKind,
    labels: List<Label>,
    book: Book,
    viewModel: LibraryViewModel,
    onNew: () -> Unit,
) {
    SectionLabel(title, Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.filter { it.kind == kind }.forEach { label ->
            val on = label.id in book.labels
            Pill(
                if (kind == LabelKind.TAG) "#${label.name}" else label.name,
                on,
                { viewModel.setLabel(book, label, !on) }
            )
        }
        Pill(if (kind == LabelKind.SHELF) "+ Shelf" else "+ Tag", false, onNew)
    }
}

/** "3 h 20 min", "12 min", "Not read yet". */
internal fun readTime(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        seconds < 60 -> "not read yet"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
