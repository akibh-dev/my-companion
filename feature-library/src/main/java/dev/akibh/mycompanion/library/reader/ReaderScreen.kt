package dev.akibh.mycompanion.library.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.fileSize
import dev.akibh.mycompanion.library.ui.CardShape
import dev.akibh.mycompanion.library.ui.DotButton
import dev.akibh.mycompanion.library.ui.DotProgress
import dev.akibh.mycompanion.library.ui.missingText
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.DotText
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.roundToInt
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

internal enum class ReaderSheet { CONTENTS, SEARCH, LOOK }

/** Everything drawn over the Readium page: bars, sheets, the missing-file screen. */
@Composable
internal fun ReaderScreen(
    viewModel: BookReaderViewModel,
    onBack: () -> Unit,
    onGo: (Locator) -> Unit,
    onGoLink: (Link) -> Unit,
    onSearchHit: (Locator) -> Unit,
    onLocate: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val relink by viewModel.relinkCheck.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            ReaderState.Loading -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant) }
            is ReaderState.Unavailable -> Unavailable(
                book = current.book,
                availability = current.availability,
                onBack = onBack,
                onLocate = onLocate,
                onRetry = viewModel::retry,
                onRemove = { viewModel.remove(onBack) },
            )
            is ReaderState.Ready -> Chrome(viewModel, current.session.book, onBack, onGo, onGoLink, onSearchHit)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))
    }
    relink?.let { (uri, check) ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRelink,
            title = { Text("A different file?") },
            text = {
                Text(
                    "You picked \"${check.fileName}\" (${check.size.fileSize()}), which has another name or size. " +
                        "Link it to this book anyway? Your place may not match if it's another edition.",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.relink(uri, force = true) }) { Text("Link anyway") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRelink) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Chrome(
    viewModel: BookReaderViewModel,
    book: Book,
    onBack: () -> Unit,
    onGo: (Locator) -> Unit,
    onGoLink: (Link) -> Unit,
    onSearchHit: (Locator) -> Unit,
) {
    val visible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val locator by viewModel.locator.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val finishPrompt by viewModel.finishPrompt.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<ReaderSheet?>(null) }

    val here = locator
    val hereBookmark = here?.let { spot -> bookmarks.firstOrNull { sameSpot(it.locator, spot) } }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .97f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val chapter = here?.title?.takeIf { it.isNotBlank() }
                    if (chapter != null) {
                        Text(
                            chapter,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DotButton(DotSymbol.CONTENTS, "Contents and bookmarks", { sheet = ReaderSheet.CONTENTS })
                if (viewModel.searchable) DotButton(DotSymbol.SEARCH, "Search in book", { sheet = ReaderSheet.SEARCH })
                DotButton(
                    if (hereBookmark != null) DotSymbol.BOOKMARKED else DotSymbol.BOOKMARK,
                    if (hereBookmark != null) "Remove bookmark" else "Bookmark this page",
                    {
                        if (hereBookmark != null) {
                            viewModel.removeBookmark(hereBookmark.id)
                        } else {
                            viewModel.bookmarkHere(defaultBookmarkLabel(here, book.format))
                        }
                    },
                    active = hereBookmark != null,
                )
                DotButton(DotSymbol.TYPE, "Look", { sheet = ReaderSheet.LOOK })
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (finishPrompt) FinishPrompt(onAnswer = viewModel::markFinished)
            AnimatedVisibility(
                visible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                ProgressBar(
                    locator = here,
                    positions = positions,
                    progress = here?.let(viewModel::progress) ?: book.progress,
                    format = book.format,
                    onSeek = { index -> positions.getOrNull(index)?.let(onGo) },
                )
            }
        }
    }

    when (sheet) {
        ReaderSheet.CONTENTS -> ContentsSheet(
            toc = viewModel.toc,
            current = here,
            bookmarks = bookmarks,
            onGoLink = {
                sheet = null
                onGoLink(it)
            },
            onGoBookmark = { json ->
                sheet = null
                Locator.fromJSON(JSONObject(json))?.let(onGo)
            },
            onRename = viewModel::renameBookmark,
            onDelete = { viewModel.removeBookmark(it) },
            onDismiss = { sheet = null },
        )
        ReaderSheet.SEARCH -> SearchSheet(
            viewModel = viewModel,
            onGo = {
                sheet = null
                viewModel.showChrome(false)
                onSearchHit(it)
            },
            onDismiss = { sheet = null },
        )
        ReaderSheet.LOOK -> LookSheet(viewModel, book.format, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** The page counter and a slider through the book. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProgressBar(
    locator: Locator?,
    positions: List<Locator>,
    progress: Double,
    format: BookFormat,
    onSeek: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val total = positions.size
    val position = locator?.locations?.position
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .97f))
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shownPosition = dragging?.let { (it * (total - 1)).roundToInt() + 1 } ?: position
            val unit = if (format == BookFormat.EPUB) "" else "Page "
            Text(
                if (total > 0 && shownPosition != null) "$unit$shownPosition / $total" else " ",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).clip(CircleShape).background(CompanionRed))
            Text(
                "${((dragging?.toDouble() ?: progress) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (total > 1) {
            Slider(
                value = dragging ?: ((position ?: 1) - 1f) / (total - 1),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek((it * (total - 1)).roundToInt()) }
                    dragging = null
                },
                thumb = { Box(Modifier.size(18.dp).clip(CircleShape).background(CompanionRed)) },
                track = { slider ->
                    val range = slider.valueRange.endInclusive - slider.valueRange.start
                    DotProgress((slider.value - slider.valueRange.start) / range, Modifier.padding(horizontal = 2.dp))
                },
            )
        } else {
            Spacer(Modifier.heightIn(min = 12.dp))
        }
    }
}

@Composable
private fun FinishPrompt(onAnswer: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("The end. Finished?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { onAnswer(false) }) { Text("Not yet") }
        Button(onClick = { onAnswer(true) }) { Text("Finished") }
    }
}

/** The file is gone, unreadable or no longer linked: say so plainly and offer the ways out. */
@Composable
private fun Unavailable(
    book: Book?,
    availability: Availability?,
    onBack: () -> Unit,
    onLocate: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            DotIcon(DotSymbol.BOOK, Modifier.size(84.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(10.dp).clip(CircleShape).background(CompanionRed))
        }
        if (book == null) {
            DotText("Not found", Modifier.padding(top = 24.dp))
            Text(
                "This book isn't in the library any more.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Button(onClick = onBack, Modifier.padding(top = 24.dp)) { Text("Back") }
            return@Column
        }
        val text = missingText(book, availability)
        DotText(text.title, Modifier.padding(top = 24.dp))
        Text(book.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            text.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 420.dp),
        )
        Button(onClick = onLocate, Modifier.padding(top = 28.dp).fillMaxWidth()) {
            Text(if (availability == null) "Link another copy" else "Locate file")
        }
        if (availability == null) {
            SecondaryButton(onClick = onRetry, Modifier.padding(top = 8.dp).fillMaxWidth()) { Text("Try again") }
        }
        SecondaryButton(
            onClick = { confirmRemove = true },
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            destructive = true,
        ) {
            Text("Remove from library")
        }
        TextButton(onClick = onBack, Modifier.padding(top = 8.dp)) { Text("Back") }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove \"${book?.title}\"?") },
            text = {
                Text(
                    "Its place, bookmarks and shelves are removed. The file isn't touched; " +
                        "reading time stays in your stats."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

/** Two locators are the same page/screen when they share the resource and position. */
internal fun sameSpot(json: String, here: Locator): Boolean {
    val saved = runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull() ?: return false
    if (saved.href != here.href) return false
    val a = saved.locations.position
    val b = here.locations.position
    return if (a != null && b != null) a == b else saved.locations.progression == here.locations.progression
}

private fun defaultBookmarkLabel(locator: Locator?, format: BookFormat): String {
    val position = locator?.locations?.position
    val title = locator?.title?.takeIf { it.isNotBlank() }
    return when {
        format != BookFormat.EPUB && position != null -> listOfNotNull("Page $position", title).joinToString(" · ")
        title != null -> title
        position != null -> "Position $position"
        else -> "Bookmark"
    }
}
