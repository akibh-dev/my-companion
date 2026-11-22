package dev.akibh.mycompanion.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.library.data.ImportReport
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import dev.akibh.mycompanion.library.domain.Label
import dev.akibh.mycompanion.library.domain.LabelKind
import dev.akibh.mycompanion.library.domain.LibraryView
import dev.akibh.mycompanion.library.domain.SortOrder
import dev.akibh.mycompanion.library.domain.fileSize
import dev.akibh.mycompanion.library.reader.BookReaderActivity
import dev.akibh.mycompanion.library.reader.TextDialog
import dev.akibh.mycompanion.ui.components.ChoiceChip
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.DotText
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.roundToInt

/** The Library screen, opened from Home. */
@Composable
fun LibraryRoute(onStats: () -> Unit, modifier: Modifier = Modifier, viewModel: LibraryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    val relinkCheck by viewModel.relinkCheck.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableLongStateOf(-1L) }
    var locateFor by rememberSaveable { mutableLongStateOf(-1L) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose { }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    val locate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && locateFor >= 0) viewModel.relink(locateFor, uri)
        locateFor = -1L
    }
    val openBook = { book: Book ->
        viewModel.open(book) { id -> context.startActivity(BookReaderActivity.intent(context, id)) }
    }
    val startLocate = { book: Book ->
        locateFor = book.id
        locate.launch(BookFormat.PICKER_TYPES)
    }

    Box(modifier.fillMaxSize()) {
        LibraryContent(
            state = state,
            onOpen = openBook,
            onDetails = { details = it.id },
            onStats = onStats,
            onAdd = { add.launch(BookFormat.PICKER_TYPES) },
            viewModel = viewModel,
        )
        if (state.all.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { add.launch(BookFormat.PICKER_TYPES) },
                icon = { DotIcon(DotSymbol.PLUS, Modifier.size(20.dp)) },
                text = { Text("Add books") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
    }

    if (importing > 0) ImportingDialog(importing)
    report?.let { ImportReportDialog(it, onDismiss = viewModel::reportShown) }
    problem?.let { (book, availability) ->
        val text = missingText(book, availability)
        AlertDialog(
            onDismissRequest = viewModel::dismissProblem,
            icon = { DotIcon(DotSymbol.BOOK, Modifier.size(40.dp), color = CompanionRed) },
            title = { Text(text.title) },
            text = { Text(text.body) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissProblem()
                    startLocate(book)
                }) { Text("Locate file") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.remove(book) }) { Text("Remove") }
                    TextButton(onClick = viewModel::dismissProblem) { Text("Cancel") }
                }
            },
        )
    }
    relinkCheck?.let { (bookId, uri, check) ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRelink,
            title = { Text("A different file?") },
            text = {
                Text(
                    "You picked \"${check.fileName}\" (${check.size.fileSize()}), which has another name or size. " +
                        "Link it to this book anyway? Your place may not match if it's another edition.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.relink(bookId, uri, force = true) }
                ) { Text("Link anyway") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRelink) { Text("Cancel") } },
        )
    }
    state.all.firstOrNull { it.id == details }?.let { book ->
        BookDetailsSheet(
            book = book,
            availability = state.availabilityOf(book),
            labels = state.labels,
            viewModel = viewModel,
            onOpen = {
                details = -1L
                openBook(book)
            },
            onLocate = { startLocate(book) },
            onDismiss = { details = -1L },
        )
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onOpen: (Book) -> Unit,
    onDetails: (Book) -> Unit,
    onStats: () -> Unit,
    onAdd: () -> Unit,
    viewModel: LibraryViewModel,
) {
    var labelMenu by remember { mutableStateOf<Label?>(null) }
    var newLabel by remember { mutableStateOf<LabelKind?>(null) }
    LazyVerticalGrid(
        columns = if (state.grid) GridCells.Adaptive(104.dp) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(if (state.grid) 18.dp else 10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!state.loading && state.all.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyLibrary(onAdd) }
            return@LazyVerticalGrid
        }
        state.continueBook?.let { book ->
            item(
                span = { GridItemSpan(maxLineSpan) }
            ) { ContinueCard(book, state.availabilityOf(book)) { onOpen(book) } }
        }
        state.stats?.let { stats ->
            item(span = { GridItemSpan(maxLineSpan) }) { TodayCard(stats, onStats) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchRow(
                text = state.filter.text,
                onText = viewModel::setText,
                sort = state.filter.sort,
                onSort = viewModel::setSort,
                format = state.filter.format,
                onFormat = viewModel::setFormat,
                grid = state.grid,
                onGrid = viewModel::setGrid,
            )
        }
        // A long filter bar at the top of the list: it scrolls sideways instead of wrapping.
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ChoiceChip(
                        "All · ${state.all.size}",
                        state.filter.view == LibraryView.All,
                        { viewModel.setView(LibraryView.All) }
                    )
                }
                items(BookStatus.entries.toList()) { status ->
                    val count = state.all.count { it.status == status }
                    ChoiceChip(
                        "${status.label} · $count",
                        state.filter.view == LibraryView.Status(status),
                        { viewModel.setView(LibraryView.Status(status)) },
                    )
                }
                items(state.shelves, key = { it.id }) { shelf ->
                    ChoiceChip(
                        shelf.name,
                        state.filter.view == LibraryView.Shelf(shelf.id),
                        { viewModel.setView(LibraryView.Shelf(shelf.id)) },
                        onLongClick = { labelMenu = shelf },
                        onLongClickLabel = "Edit shelf",
                    )
                }
                item { SecondaryButton(onClick = { newLabel = LabelKind.SHELF }) { Text("+ Shelf") } }
            }
        }
        if (state.tags.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags, key = { it.id }) { tag ->
                        ChoiceChip(
                            "#${tag.name}",
                            state.filter.tag == tag.id,
                            { viewModel.setTag(if (state.filter.tag == tag.id) null else tag.id) },
                            onLongClick = { labelMenu = tag },
                            onLongClickLabel = "Edit tag",
                        )
                    }
                }
            }
        }
        if (state.shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No books here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.shown, key = { it.id }) { book ->
            if (state.grid) {
                BookTile(book, state.availabilityOf(book), { onOpen(book) }, { onDetails(book) })
            } else {
                BookRow(book, state.availabilityOf(book), { onOpen(book) }, { onDetails(book) })
            }
        }
    }
    labelMenu?.let { label ->
        LabelDialog(
            label = label,
            onRename = { viewModel.renameLabel(label, it) },
            onDelete = { viewModel.deleteLabel(label) },
            onDismiss = { labelMenu = null },
        )
    }
    newLabel?.let { kind ->
        TextDialog(
            title = if (kind == LabelKind.SHELF) "New shelf" else "New tag",
            initial = "",
            onDone = {
                viewModel.newLabel(it, kind)
                newLabel = null
            },
            onDismiss = { newLabel = null },
        )
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotIcon(DotSymbol.SHELF, Modifier.size(72.dp))
        DotText("No books yet", Modifier.padding(top = 24.dp))
        Text(
            "Download PDF, EPUB or CBZ comic files with your browser, then add them here. " +
                "Books stay where they are: the library links to them and remembers your place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        androidx.compose.material3.Button(onClick = onAdd, Modifier.padding(top = 20.dp).fillMaxWidth()) {
            Text("Add books")
        }
    }
}

@Composable
private fun ContinueCard(book: Book, availability: Availability, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, Modifier.width(72.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(CompanionRed))
                SectionLabel("Continue reading", Modifier.padding(start = 8.dp))
            }
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DotProgress(book.progress.toFloat(), Modifier.weight(1f))
                Text(
                    if (availability == Availability.OK) "${(book.progress * 100).roundToInt()}%" else "Missing",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (availability == Availability.OK) MaterialTheme.colorScheme.onSurface else CompanionRed,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayCard(stats: dev.akibh.mycompanion.library.domain.ReadingStatus, onStats: () -> Unit) {
    val minutes = stats.todaySeconds / 60
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onStats)
            .semantics(mergeDescendants = true) { contentDescription = "Reading stats" }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            DotRing(
                if (stats.goalSeconds > 0) stats.todaySeconds.toFloat() / stats.goalSeconds else 0f,
                Modifier.size(56.dp),
                dots = 28,
            )
            Text("$minutes", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                if (stats.goal.minutesPerDay > 0) {
                    "$minutes of ${stats.goal.minutesPerDay} min today"
                } else {
                    "$minutes min today"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                when (stats.streak) {
                    0 -> "Read a minute to start a streak"
                    1 -> "1 day streak"
                    else -> "${stats.streak} day streak"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DotIcon(DotSymbol.CHART, Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchRow(
    text: String,
    onText: (String) -> Unit,
    sort: SortOrder,
    onSort: (SortOrder) -> Unit,
    format: BookFormat?,
    onFormat: (BookFormat?) -> Unit,
    grid: Boolean,
    onGrid: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotIcon(DotSymbol.SEARCH, Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(start = 10.dp)) {
                if (text.isEmpty()) {
                    Text(
                        "Title or author",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(CompanionRed),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (text.isNotEmpty()) {
                IconButton(onClick = { onText("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", Modifier.size(18.dp))
                }
            }
        }
        Box {
            DotButton(DotSymbol.CONTENTS, "Sort and filter", { menu = true }, active = format != null)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SectionLabel("Sort", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                SortOrder.entries.forEach { order ->
                    MenuChoice(order.label, order == sort) {
                        onSort(order)
                        menu = false
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SectionLabel("Show", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                MenuChoice("Every format", format == null) {
                    onFormat(null)
                    menu = false
                }
                BookFormat.entries.forEach { choice ->
                    MenuChoice(choice.label, format == choice) {
                        onFormat(choice)
                        menu = false
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuChoice("Covers", grid) {
                    onGrid(true)
                    menu = false
                }
                MenuChoice("List", !grid) {
                    onGrid(false)
                    menu = false
                }
            }
        }
    }
}

@Composable
private fun MenuChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected") },
    )
}

@Composable
private fun BookTile(book: Book, availability: Availability, onOpen: () -> Unit, onDetails: () -> Unit) {
    Column(
        Modifier
            .clip(CoverShape)
            .combinedClickable(onClick = onOpen, onLongClick = onDetails, onLongClickLabel = "Details"),
    ) {
        Box {
            Cover(book, Modifier.fillMaxWidth())
            if (availability != Availability.OK) {
                CoverBadge(
                    "Missing",
                    alert = true,
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            } else if (book.status == BookStatus.FINISHED) {
                CoverBadge("Done", alert = false, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Text(
            book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val reading = book.progress > 0 && book.status != BookStatus.FINISHED
        Text(
            if (reading) "${book.format.label} · ${(book.progress * 100).roundToInt()}%" else book.format.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (book.progress > 0 && book.status != BookStatus.FINISHED) {
            DotProgress(book.progress.toFloat(), Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun BookRow(book: Book, availability: Availability, onOpen: () -> Unit, onDetails: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onOpen, onLongClick = onDetails, onLongClickLabel = "Details")
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, Modifier.width(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(
                    book.author,
                    book.format.label,
                    book.size.fileSize()
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.progress > 0) DotProgress(book.progress.toFloat(), Modifier.padding(top = 8.dp))
        }
        Text(
            when {
                availability != Availability.OK -> "Missing"
                book.status == BookStatus.FINISHED -> "Done"
                else -> "${(book.progress * 100).roundToInt()}%"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (availability != Availability.OK) CompanionRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}

@Composable
private fun LabelDialog(label: Label, onRename: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(label.name) }
    val kind = if (label.kind == LabelKind.SHELF) "shelf" else "tag"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $kind") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true)
                Text(
                    "Deleting the $kind keeps its books.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(name)
                onDismiss()
            }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDelete()
                    onDismiss()
                }) { Text("Delete", color = CompanionRed) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ImportingDialog(count: Int) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(if (count == 1) "Adding the book" else "Adding $count books") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurface)
                Text("Reading titles and covers…", Modifier.padding(start = 16.dp))
            }
        },
    )
}

@Composable
private fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    // Everything went in: no dialog to dismiss, the new covers say it.
    if (report.failed.isEmpty() && report.duplicates.isEmpty()) {
        LaunchedEffect(report) { onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    report.added.isEmpty() && report.failed.isEmpty() -> "Already in the library"
                    report.added.isEmpty() -> "Nothing added"
                    else -> "Added ${report.added.size}"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (report.duplicates.isNotEmpty()) {
                    Text(
                        "Already in the library: ${report.duplicates.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                report.failed.forEach { (name, reason) ->
                    Column {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
