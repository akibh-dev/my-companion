package dev.akibh.mycompanion.quran.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akibh.mycompanion.quran.domain.Ayah
import dev.akibh.mycompanion.quran.domain.LineKind
import dev.akibh.mycompanion.quran.domain.MushafLine
import dev.akibh.mycompanion.quran.domain.MushafScript
import dev.akibh.mycompanion.quran.domain.ReadingMode
import dev.akibh.mycompanion.quran.domain.Surah
import dev.akibh.mycompanion.quran.domain.arabicDigits
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ReaderRoute(modifier: Modifier = Modifier, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        ReaderState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is ReaderState.Ready -> ReaderScreen(s, viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(state: ReaderState.Ready, viewModel: ReaderViewModel, modifier: Modifier) {
    val view = LocalView.current
    DisposableEffect(state.prefs.keepScreenOn) {
        view.keepScreenOn = state.prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    var sheet by remember { mutableStateOf<IntRange?>(null) }
    Column(modifier.fillMaxSize()) {
        ReaderControls(state, viewModel)
        Box(Modifier.weight(1f)) {
            key(state.generation, state.prefs.script, state.prefs.mode) {
                when (state.prefs.mode) {
                    ReadingMode.MUSHAF -> MushafPager(state, viewModel, onLongPress = { sheet = it })
                    ReadingMode.AYAH_LIST -> AyahList(state, viewModel, onLongPress = { sheet = it })
                }
            }
        }
        ZoomBar(state, viewModel)
    }
    sheet?.let { range ->
        AyahSheet(range, state, viewModel, onDismiss = { sheet = null })
    }
}

@Composable
private fun ReaderControls(state: ReaderState.Ready, viewModel: ReaderViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            listOf(ReadingMode.MUSHAF to "Page", ReadingMode.AYAH_LIST to "Ayahs").forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = state.prefs.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    enabled = mode != ReadingMode.MUSHAF || state.hasPages,
                ) { Text(label) }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            listOf(
                MushafScript.MADANI to "Madani",
                MushafScript.INDOPAK to "Indo-Pak"
            ).forEachIndexed { i, (script, label) ->
                SegmentedButton(
                    selected = state.prefs.script == script,
                    onClick = { viewModel.setScript(script) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
    }
}

// ---------------------------------------------------------------- mushaf pages

@Composable
private fun MushafPager(state: ReaderState.Ready, viewModel: ReaderViewModel, onLongPress: (IntRange) -> Unit) {
    val script = state.prefs.script
    val pager = rememberPagerState(initialPage = state.startPage - 1) { script.pages }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { viewModel.onPageShown(script, it + 1) }
    }
    var zoom by remember { mutableFloatStateOf(state.prefs.pageZoom) }
    LaunchedEffect(state.prefs.pageZoom) { zoom = state.prefs.pageZoom } // the + and − buttons
    // Two fingers zoom the text itself (the fonts are outlines, so it stays sharp); one finger turns pages.
    val pinch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoomed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val change = event.calculateZoom()
                    if (change != 1f) {
                        zoom = (zoom * change).coerceIn(1f, MAX_PAGE_ZOOM)
                        zoomed = true
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (zoomed) viewModel.setPageZoom(zoom)
        }
    }
    // A mushaf opens right to left: page 2 is to the left of page 1.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().then(pinch),
            beyondViewportPageCount = 1,
            key = { it },
        ) { index ->
            MushafPage(script, index + 1, zoom, state.surahs, viewModel, onLongPress)
        }
    }
}

/**
 * The line's words as drawn. A token that is only marks (a waqf sign the layout lists as its own word)
 * joins the word before it, so it doesn't take a word's share of the line's spacing.
 */
private fun MushafLine.words(): List<String> = text.split(' ').fold(mutableListOf()) { words, token ->
    val markOnly = token.isNotEmpty() && token.all { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    if (markOnly && words.isNotEmpty()) words[words.lastIndex] = words.last() + token else words += token
    words
}

/** How one ayah line of a page is drawn: its font size and whether it fills the width. */
private data class LineFit(val size: Float, val justify: Boolean)

/**
 * The page's text size and each line's fit, from the words' natural widths at [REFERENCE_SIZE].
 * The size follows a typical full line (not the widest), so words don't spread apart; a line wider
 * than the page shrinks a little instead of running off it, and a short line (a surah's end) is
 * centred with normal spacing instead of being stretched.
 */
private class PageFit(val base: Float, val contentWidthPx: Float, val lines: Map<Int, LineFit>)

private fun fitPage(
    lines: List<MushafLine>,
    natural: Map<Int, Float>,
    widthPx: Float,
    heightLimitedSize: Float,
): PageFit {
    val full = lines.filter { it.kind == LineKind.AYAH && !it.centered }.mapNotNull { natural[it.line] }.sorted()
    val typical = full.getOrNull((full.size * TYPICAL_LINE).toInt().coerceAtMost(full.lastIndex))
        ?: natural.values.maxOrNull() ?: return PageFit(FALLBACK_SIZE, widthPx, emptyMap())
    val base = minOf(REFERENCE_SIZE * widthPx / typical, heightLimitedSize)
    val contentWidth = minOf(widthPx, typical * base / REFERENCE_SIZE)
    val fits = natural.mapValues { (_, width) ->
        val drawn = width * base / REFERENCE_SIZE
        val shrink = minOf(1f, contentWidth / drawn)
        LineFit(base * shrink, justify = drawn * shrink >= contentWidth * JUSTIFY_FROM)
    }
    return PageFit(base, contentWidth, fits)
}

@Composable
private fun MushafPage(
    script: MushafScript,
    page: Int,
    zoom: Float,
    surahs: List<Surah>,
    viewModel: ReaderViewModel,
    onLongPress: (IntRange) -> Unit,
) {
    val content by produceState<Pair<List<MushafLine>, FontFamily>?>(null, script, page) {
        value = viewModel.lines(script, page) to viewModel.pageFont(script, page)
    }
    val loaded = content ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
    val (lines, font) = loaded
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    // Indo-Pak marks stand above the line ends and its letters reach further down: more room.
    val side = if (script == MushafScript.INDOPAK) INDOPAK_SIDE else SIDE
    val lineRatio = if (script == MushafScript.INDOPAK) INDOPAK_LINE_RATIO else MADANI_LINE_RATIO
    // Natural width of each ayah line at the reference size: its words plus a plain space between them.
    val natural = remember(lines, font) {
        val style = TextStyle(fontFamily = font, fontSize = REFERENCE_SIZE.sp)
        val space = measurer.measure(" ", style).size.width.toFloat()
        lines.filter { it.kind == LineKind.AYAH }.associate { line ->
            val words = line.words()
            line.line to words.sumOf { measurer.measure(it, style, softWrap = false).size.width.toDouble() }
                .toFloat() + space * (words.size - 1)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fit = with(density) {
            val lineHeight = (maxHeight - FOOTER) / LINES
            val heightLimited = (lineHeight / lineRatio).toSp().value
            remember(natural, maxWidth, maxHeight) {
                fitPage(lines, natural, (maxWidth - side * 2).toPx() * INK_ROOM, heightLimited)
            }
        }
        val lineHeight = (maxHeight - FOOTER) / LINES * zoom
        val contentWidth = with(density) { fit.contentWidthPx.toDp() } * zoom
        val horizontal = rememberScrollState()
        val zoomed = zoom > 1f
        Box(
            Modifier.fillMaxSize()
                .then(if (zoomed) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .then(if (zoomed) Modifier.horizontalScroll(horizontal) else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.padding(horizontal = side * zoom).width(contentWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (line in lines) {
                    MushafLineRow(
                        line = line,
                        font = font,
                        fit = fit.lines[line.line] ?: LineFit(fit.base, justify = false),
                        zoom = zoom,
                        height = lineHeight,
                        surahs = surahs,
                        hafs = viewModel.fonts.hafs,
                        onLongPress = onLongPress,
                    )
                }
                Text(
                    page.arabicDigits(),
                    Modifier.height(FOOTER * zoom).padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MushafLineRow(
    line: MushafLine,
    font: FontFamily,
    fit: LineFit,
    zoom: Float,
    height: Dp,
    surahs: List<Surah>,
    hafs: FontFamily,
    onLongPress: (IntRange) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fontSize = (fit.size * zoom).sp
    val press = Modifier.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
        onLongClick = { onLongPress(line.firstAyahId..line.lastAyahId) },
    )
    when (line.kind) {
        LineKind.SURAH_HEADER -> Box(
            Modifier.fillMaxWidth().height(height).padding(vertical = 3.dp)
                .border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "سُورَةُ ${surahs[line.surah - 1].nameArabic}",
                fontFamily = hafs,
                letterSpacing = 0.sp,
                fontSize = fontSize * 0.8f,
                color = ink
            )
        }

        LineKind.BISMILLAH -> Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(BISMILLAH, fontFamily = hafs, letterSpacing = 0.sp, fontSize = fontSize * 0.85f, color = ink)
        }

        LineKind.AYAH -> Row(
            Modifier.fillMaxWidth().height(height).then(press),
            horizontalArrangement = if (fit.justify) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(
                    with(LocalDensity.current) { (fontSize * WORD_GAP).toDp() },
                    Alignment.CenterHorizontally
                )
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Exactly the style the line was measured with (no theme letter spacing), so it fits.
            val style = TextStyle(fontFamily = font, fontSize = fontSize, color = ink)
            line.words().forEach { word ->
                Text(word, style = style, softWrap = false, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- ayah list

@Composable
private fun AyahList(state: ReaderState.Ready, viewModel: ReaderViewModel, onLongPress: (IntRange) -> Unit) {
    val script = state.prefs.script
    val ayahs by produceState<List<Ayah>?>(null, script) { value = viewModel.ayahs(script) }
    val list = ayahs ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.startAyahId - 1)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect {
            viewModel.onAyahShown(
                it + 1
            )
        }
    }
    val font = if (script == MushafScript.MADANI) viewModel.fonts.hafs else viewModel.fonts.nastaleeq
    var scale by remember { mutableFloatStateOf(state.prefs.textScale) }
    LaunchedEffect(state.prefs.textScale) { scale = state.prefs.textScale } // the + and − buttons
    val pinch = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoomed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val change = event.calculateZoom()
                    if (change != 1f) {
                        scale = (scale * change).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
                        zoomed = true
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (zoomed) viewModel.setTextScale(scale)
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(Modifier.fillMaxSize().then(pinch), state = listState) {
            items(list, key = { it.id }) { ayah ->
                Column {
                    if (ayah.number == 1) {
                        SurahBanner(state.surahs[ayah.surah - 1], viewModel.fonts.hafs, ayah.surah)
                    }
                    if (ayah.id in bookmarked) {
                        Text(
                            "Bookmarked · ${ayah.ref}",
                            Modifier.padding(start = 16.dp, top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    @OptIn(ExperimentalFoundationApi::class)
                    Text(
                        if (script == MushafScript.INDOPAK) ayah.display.replace(" ", WIDE_SPACE) else ayah.display,
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { onLongPress(ayah.id..ayah.id) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        fontFamily = font,
                        letterSpacing = 0.sp,
                        fontSize = (BASE_LIST_SIZE * scale).sp,
                        lineHeight = (BASE_LIST_SIZE * scale * 1.9f).sp,
                        color = ink,
                        textAlign = TextAlign.Start,
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = ink.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun SurahBanner(surah: Surah, hafs: FontFamily, number: Int) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth().padding(16.dp)
            .border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("سُورَةُ ${surah.nameArabic}", fontFamily = hafs, letterSpacing = 0.sp, fontSize = 26.sp, color = ink)
        Text(
            "${surah.nameSimple} · ${surah.ayahCount} ayahs",
            style = MaterialTheme.typography.bodySmall,
        )
        if (number != 1 && number != 9) {
            Text(
                BISMILLAH,
                fontFamily = hafs,
                letterSpacing = 0.sp,
                fontSize = 24.sp,
                color = ink,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- ayah actions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahSheet(range: IntRange, state: ReaderState.Ready, viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    val script = state.prefs.script
    val ayahs by produceState<List<Ayah>>(emptyList(), range, script) {
        value = viewModel.ayahs(range.first, range.last, script)
    }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val font = if (script == MushafScript.MADANI) viewModel.fonts.hafs else viewModel.fonts.nastaleeq
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle()
    var noteFor by remember { mutableStateOf<Ayah?>(null) }
    noteFor?.let { ayah ->
        BookmarkDialog(
            title = "Bookmark ${state.surahs[ayah.surah - 1].nameSimple} ${ayah.ref}",
            onSave = { note ->
                viewModel.bookmark(ayah.id, note)
                noteFor = null
            },
            onDismiss = { noteFor = null },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(ayahs, key = { it.id }) { ayah ->
                val surah = state.surahs[ayah.surah - 1]
                val reference = "${surah.nameSimple} ${ayah.surah}:${ayah.number}"
                val quoted = "${ayah.plain}\n— $reference"
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${surah.nameSimple} ${ayah.surah}:${ayah.number}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            ayah.display,
                            Modifier.fillMaxWidth(),
                            fontFamily = font,
                            letterSpacing = 0.sp,
                            fontSize = 22.sp,
                            lineHeight = 40.sp
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (ayah.id in bookmarked) {
                            TextButton(onClick = { viewModel.removeBookmark(ayah.id) }) { Text("Remove bookmark") }
                        } else {
                            TextButton(onClick = { noteFor = ayah }) { Text("Bookmark") }
                        }
                        TextButton(onClick = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Ayah", quoted))) }
                        }) { Text("Copy") }
                        TextButton(onClick = {
                            val send = Intent(
                                Intent.ACTION_SEND
                            ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, quoted)
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text("Share") }
                        if (state.prefs.mode == ReadingMode.MUSHAF) {
                            TextButton(onClick = {
                                viewModel.onAyahShown(ayah.id)
                                viewModel.setMode(ReadingMode.AYAH_LIST)
                                onDismiss()
                            }) { Text("Open in ayah list") }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * A slim bar under the page: − and + on the left for zoom (pages) or text size (ayah list), for when
 * pinching is awkward, and how big it is now. Below the text, so it never covers a line.
 */
@Composable
private fun ZoomBar(state: ReaderState.Ready, viewModel: ReaderViewModel) {
    val page = state.prefs.mode == ReadingMode.MUSHAF
    val value = if (page) state.prefs.pageZoom else state.prefs.textScale
    fun set(next: Float) = if (page) viewModel.setPageZoom(next) else viewModel.setTextScale(next)
    val min = if (page) 1f else MIN_TEXT_SCALE
    val max = if (page) MAX_PAGE_ZOOM else MAX_TEXT_SCALE
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalIconButton(
            onClick = { set((value / ZOOM_STEP).coerceAtLeast(min)) },
            enabled = value > min,
            modifier = Modifier.semantics { contentDescription = if (page) "Zoom out" else "Smaller text" },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        FilledTonalIconButton(
            onClick = { set((value * ZOOM_STEP).coerceAtMost(max)) },
            enabled = value < max,
            modifier = Modifier.semantics { contentDescription = if (page) "Zoom in" else "Larger text" },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BookmarkDialog(title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val LINES = 15
private const val REFERENCE_SIZE = 20f
private const val FALLBACK_SIZE = 20f
private const val BASE_LIST_SIZE = 24f
private val SIDE = 14.dp
private val INDOPAK_SIDE = 20.dp

/** Glyphs and marks reach a little past their measured width at the line ends; leave them room. */
private const val INK_ROOM = 0.97f
private val FOOTER = 28.dp

/** Line height as a multiple of the font size: Nastaleeq needs more room for its marks. */
private const val MADANI_LINE_RATIO = 1.55f
private const val INDOPAK_LINE_RATIO = 1.9f

/** The page's text size follows its 80th-percentile line, so one long line doesn't shrink the rest. */
private const val TYPICAL_LINE = 0.8f

/** Lines at least this full are justified; shorter ones are centred with normal spacing. */
private const val JUSTIFY_FROM = 0.82f

/** A plain space between words, as a fraction of the font size. */
private const val WORD_GAP = 0.28f
private const val ZOOM_STEP = 1.15f

/** Nastaleeq words sit close together; an en space keeps them apart (and still lets lines break). */
private const val WIDE_SPACE = "\u2002"
private const val BISMILLAH = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
