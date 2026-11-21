package dev.akibh.mycompanion.library.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.library.data.BookBookmark
import dev.akibh.mycompanion.library.data.BookFont
import dev.akibh.mycompanion.library.data.PageTheme
import dev.akibh.mycompanion.library.data.ReaderLook
import dev.akibh.mycompanion.library.data.Spacing
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.reader.BookSearch.MAX_HITS
import dev.akibh.mycompanion.library.ui.Pill
import dev.akibh.mycompanion.library.ui.SectionLabel
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentsSheet(
    toc: List<TocEntry>,
    current: Locator?,
    bookmarks: List<BookBookmark>,
    onGoLink: (Link) -> Unit,
    onGoBookmark: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<BookBookmark?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxHeight(.85f)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Contents") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks · ${bookmarks.size}") })
            }
            if (tab == 0) {
                // The chapter being read: the last entry whose file is the current one.
                val currentIndex = current?.let { here ->
                    toc.indexOfLast { it.link.url().removeFragment() == here.href.removeFragment() }
                } ?: -1
                val list = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
                if (toc.isEmpty()) {
                    Empty("This book has no table of contents.")
                }
                LazyColumn(
                    state = list,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    items(toc.size) { index ->
                        val entry = toc[index]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onGoLink(entry.link) }
                                .heightIn(min = 48.dp)
                                .padding(start = 24.dp + 16.dp * entry.depth, end = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.title,
                                style = if (entry.depth == 0) {
                                    MaterialTheme.typography.bodyLarge
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (index == currentIndex) {
                                Box(
                                    Modifier.size(6.dp).clip(CircleShape).background(CompanionRed)
                                )
                            }
                        }
                    }
                }
            } else {
                if (bookmarks.isEmpty()) {
                    Empty("No bookmarks yet. Tap the ribbon at the top to mark a page.")
                }
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    items(bookmarks, key = { it.id }) { mark ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onGoBookmark(mark.locator) }
                                .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    mark.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${(mark.progress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { renaming = mark }
                            ) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
                            IconButton(
                                onClick = { onDelete(mark.id) }
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }
    renaming?.let { mark ->
        TextDialog(
            title = "Rename bookmark",
            initial = mark.label,
            onDone = {
                onRename(mark.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(viewModel: BookReaderViewModel, onGo: (Locator) -> Unit, onDismiss: () -> Unit) {
    val state by viewModel.search.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(state.query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (state.query.isEmpty()) focus.requestFocus() }
    // Search once typing pauses; Enter searches at once.
    LaunchedEffect(query) {
        if (query == state.query) return@LaunchedEffect
        delay(450)
        viewModel.search(query)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxHeight(.85f)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search in this book") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            if (state.running) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            } else {
                Spacer(Modifier.heightIn(min = 12.dp))
            }
            val count = state.hits.size
            if (count > 0 || state.searched) {
                SectionLabel(
                    when {
                        count == 0 -> "No matches"
                        count >= MAX_HITS -> "$MAX_HITS+ matches"
                        count == 1 -> "1 match"
                        else -> "$count matches"
                    },
                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(state.hits.size) { index ->
                    val hit = state.hits[index]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onGo(hit.locator) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        if (hit.where.isNotBlank()) {
                            Text(
                                hit.where,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val highlight = MaterialTheme.colorScheme.primary
                        Text(
                            buildAnnotatedString {
                                append(if (hit.before.isNotEmpty()) "…${hit.before}" else "")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = highlight)) {
                                    append(hit.match)
                                }
                                append(if (hit.after.isNotEmpty()) "${hit.after}…" else "")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LookSheet(viewModel: BookReaderViewModel, format: BookFormat, onDismiss: () -> Unit) {
    val look by viewModel.look.collectAsStateWithLifecycle()
    val set = { change: ReaderLook -> viewModel.setLook(change) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (format) {
                BookFormat.EPUB -> {
                    SectionLabel("Text size")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = {
                                set(
                                    look.copy(fontScale = (look.fontScale - FONT_STEP).coerceAtLeast(MIN_FONT))
                                )
                            },
                            enabled = look.fontScale > MIN_FONT,
                        ) { Text("A−", style = MaterialTheme.typography.labelLarge) }
                        Text(
                            "${(look.fontScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        FilledTonalIconButton(
                            onClick = {
                                set(
                                    look.copy(fontScale = (look.fontScale + FONT_STEP).coerceAtMost(MAX_FONT))
                                )
                            },
                            enabled = look.fontScale < MAX_FONT,
                        ) { Text("A+", style = MaterialTheme.typography.titleMedium) }
                    }
                    SectionLabel("Page")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageTheme.entries.forEach { theme ->
                            ThemeSwatch(theme, look.theme == theme) { set(look.copy(theme = theme)) }
                        }
                    }
                    SectionLabel("Font")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BookFont.entries.forEach { font ->
                            Pill(font.label, look.font == font, { set(look.copy(font = font)) })
                        }
                    }
                    SectionLabel("Spacing")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacing.entries.forEach { spacing ->
                            Pill(spacing.label, look.spacing == spacing, { set(look.copy(spacing = spacing)) })
                        }
                    }
                    SectionLabel("Layout")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Pages", !look.scroll, { set(look.copy(scroll = false)) })
                        Pill("Scroll", look.scroll, { set(look.copy(scroll = true)) })
                    }
                }
                BookFormat.PDF -> {
                    SectionLabel("Fit")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Whole page", !look.pdfFitWidth, { set(look.copy(pdfFitWidth = false)) })
                        Pill("Page width", look.pdfFitWidth, { set(look.copy(pdfFitWidth = true)) })
                    }
                    SectionLabel("Turn pages")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Scroll down", look.pdfVertical, { set(look.copy(pdfVertical = true)) })
                        Pill("Swipe across", !look.pdfVertical, { set(look.copy(pdfVertical = false)) })
                    }
                    ControlsTheme(look, set)
                }
                BookFormat.CBZ -> {
                    SectionLabel("Direction")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Left to right", !look.comicRtl, { set(look.copy(comicRtl = false)) })
                        Pill("Right to left (manga)", look.comicRtl, { set(look.copy(comicRtl = true)) })
                    }
                    ControlsTheme(look, set)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Keep the screen on", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = look.keepScreenOn,
                    onCheckedChange = { set(look.copy(keepScreenOn = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** PDF and comic pages keep their own colours; this only sets the controls around them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsTheme(look: ReaderLook, set: (ReaderLook) -> Unit) {
    SectionLabel("Controls")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PageTheme.AUTO, PageTheme.LIGHT, PageTheme.DARK).forEach { theme ->
            Pill(theme.label, look.theme == theme, { set(look.copy(theme = theme)) })
        }
    }
}

@Composable
private fun ThemeSwatch(theme: PageTheme, selected: Boolean, onClick: () -> Unit) {
    val (paper, ink) = when (theme) {
        PageTheme.AUTO -> MaterialTheme.colorScheme.background to MaterialTheme.colorScheme.onBackground
        PageTheme.LIGHT -> Color(0xFFFFFFFF) to Color(0xFF121212)
        PageTheme.SEPIA -> Color(0xFFFAF4E8) to Color(0xFF121212)
        PageTheme.DARK -> Color(0xFF000000) to Color(0xFFFEFEFE)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(paper)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) CompanionRed else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text("Aa", color = ink, style = MaterialTheme.typography.titleSmall) }
        Text(theme.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
internal fun TextDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val FONT_STEP = 0.125
private const val MIN_FONT = 0.75
private const val MAX_FONT = 2.5
