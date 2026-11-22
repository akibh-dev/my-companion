package dev.akibh.mycompanion.library.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.library.domain.Availability
import dev.akibh.mycompanion.library.domain.Book
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.fileSize
import dev.akibh.mycompanion.library.ui.CardShape
import dev.akibh.mycompanion.library.ui.DotButton
import dev.akibh.mycompanion.library.ui.missingText
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.DotText
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.SectionLabel
import dev.akibh.mycompanion.ui.theme.CompanionRed
import kotlin.math.roundToInt
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

internal enum class ReaderSheet { CONTENTS, SEARCH, LOOK }

/**
 * Everything drawn over the Readium page: bars, sheets, the missing-file screen. [pageColor] is the
 * page's own background (black, white or sepia); the bars fade into it so they never sit on text.
 */
@Composable
internal fun ReaderScreen(
    viewModel: BookReaderViewModel,
    pageColor: Color,
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
    // This view has no Surface behind it, so text and icons would default to black and vanish on a
    // dark page. Give them the page's ink instead.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
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
                is ReaderState.Ready ->
                    Chrome(viewModel, current.session.book, pageColor, onBack, onGo, onGoLink, onSearchHit)
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 132.dp),
            )
        }
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

/**
 * The reading controls, shown by a tap in the middle of the page:
 * - top: a round back button, chapter (small caps) over the title, and one pill of actions;
 * - bottom: a pill with the position, the red-dot percentage and a dotted slider through the book.
 * A fade from the page colour sits behind both, so the controls are on a calm background in any
 * theme, portrait or landscape, paged or scrolled. Both stay no wider than [MAX_BAR_WIDTH].
 */
@Composable
private fun Chrome(
    viewModel: BookReaderViewModel,
    book: Book,
    pageColor: Color,
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
        // Fades behind the bars. Plain backgrounds don't take touches, so the page still gets its taps.
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(SCRIM_TOP)
                        .background(Brush.verticalGradient(listOf(pageColor, pageColor.copy(alpha = 0f)))),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(SCRIM_BOTTOM)
                        .background(Brush.verticalGradient(listOf(pageColor.copy(alpha = 0f), pageColor))),
                )
            }
        }

        AnimatedVisibility(
            visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(
                chapter = here?.title?.takeIf { it.isNotBlank() } ?: book.title,
                bookmarked = hereBookmark != null,
                searchable = viewModel.searchable,
                onBack = onBack,
                onContents = { sheet = ReaderSheet.CONTENTS },
                onSearch = { sheet = ReaderSheet.SEARCH },
                onBookmark = {
                    if (hereBookmark != null) {
                        viewModel.removeBookmark(hereBookmark.id)
                    } else {
                        viewModel.bookmarkHere(defaultBookmarkLabel(here, book.format))
                    }
                },
                onLook = { sheet = ReaderSheet.LOOK },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = MAX_BAR_WIDTH)
                .fillMaxWidth(),
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

@Composable
private fun TopBar(
    chapter: String,
    bookmarked: Boolean,
    searchable: Boolean,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onSearch: () -> Unit,
    onBookmark: () -> Unit,
    onLook: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = MAX_BAR_WIDTH)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Both pills are 56 dp with 4 dp around each 48 dp button, so a pressed button's circle is
            // concentric with the pill's rounded end instead of cutting across it.
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                IconButton(onClick = onBack, Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            // Where you are: the chapter (or the title when there is none), one line of small caps.
            SectionLabel(chapter, Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1)
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(4.dp)) {
                    DotButton(DotSymbol.CONTENTS, "Contents and bookmarks", onContents)
                    if (searchable) DotButton(DotSymbol.SEARCH, "Search in book", onSearch)
                    DotButton(
                        if (bookmarked) DotSymbol.BOOKMARKED else DotSymbol.BOOKMARK,
                        if (bookmarked) "Remove bookmark" else "Bookmark this page",
                        onBookmark,
                        active = bookmarked,
                    )
                    DotButton(DotSymbol.TYPE, "Look", onLook)
                }
            }
        }
    }
}

/**
 * One compact floating pill, like the Quran reader's zoom pill: position · dotted strip · red-dot
 * percentage. Tap the position to type a page. Press the strip to grab the red dot: the strip grows,
 * a bubble shows where you'd land, and sliding the finger up slows the dot for exact pages.
 */
@Composable
private fun ProgressBar(
    locator: Locator?,
    positions: List<Locator>,
    progress: Double,
    format: BookFormat,
    onSeek: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var askPage by remember { mutableStateOf(false) }
    val total = positions.size
    val position = locator?.locations?.position
    val unit = if (format == BookFormat.EPUB) "" else "p. "
    fun indexOf(value: Float) = (value * (total - 1)).roundToInt()
    val shownPosition = dragging?.let { indexOf(it) + 1 } ?: position

    // Not a clipping Surface: the scrubber's curve rises out of the pill while it's pushed up.
    Row(
        Modifier
            .shadow(4.dp, CircleShape, clip = false)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .heightIn(min = 56.dp)
            .padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (total > 0 && shownPosition != null) "$unit$shownPosition / $total" else " ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = total > 1, onClickLabel = "Go to page") { askPage = true }
                .heightIn(min = 48.dp)
                .wrapContentHeight()
                .padding(horizontal = 12.dp),
        )
        if (total > 1) {
            Scrubber(
                value = dragging ?: ((position ?: 1) - 1f) / (total - 1),
                steps = total - 1,
                expanded = dragging != null,
                label = "$unit${shownPosition ?: ""}",
                onGrab = {},
                onChange = { dragging = it },
                onRelease = {
                    dragging?.let { onSeek(indexOf(it)) }
                    dragging = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.size(6.dp).clip(CircleShape).background(CompanionRed))
        Text(
            "${((dragging?.toDouble() ?: progress) * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
        )
    }
    if (askPage) {
        GoToPageDialog(
            total = total,
            current = position,
            label = if (format == BookFormat.EPUB) "Position" else "Page",
            onGo = { page ->
                askPage = false
                onSeek(page - 1)
            },
            onDismiss = { askPage = false },
        )
    }
}

/** Type a page (or EPUB position) and jump there. */
@Composable
private fun GoToPageDialog(total: Int, current: Int?, label: String, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..total }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // the number pad opens straight away
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to ${label.lowercase()}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(6) },
                label = { Text("$label (1–$total)") },
                placeholder = current?.let { { Text("$it") } },
                singleLine = true,
                isError = text.isNotEmpty() && page == null,
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { page?.let(onGo) }),
            )
        },
        confirmButton = { TextButton(onClick = { page?.let(onGo) }, enabled = page != null) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val MAX_BAR_WIDTH = 640.dp
private val SCRIM_TOP = 140.dp
private val SCRIM_BOTTOM = 130.dp

@Composable
private fun FinishPrompt(onAnswer: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
