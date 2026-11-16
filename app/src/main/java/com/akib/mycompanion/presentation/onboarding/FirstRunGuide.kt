package com.akib.mycompanion.presentation.onboarding

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.akib.mycompanion.ui.components.DotIcon
import com.akib.mycompanion.ui.components.DotPattern
import com.akib.mycompanion.ui.components.DotSymbol
import com.akib.mycompanion.ui.components.DotText
import com.akib.mycompanion.ui.theme.CompanionRed
import kotlinx.coroutines.launch

/** One page of the first-run guide. [symbol] is drawn in the hero; null shows the dotted wordmark. */
data class GuidePage(
    val title: String,
    val body: String,
    val symbol: DotSymbol? = null,
    val points: List<Pair<DotSymbol, String>> = emptyList(),
)

/** The four things worth knowing before using the app (Phase 5, "first-run guide"). */
val guidePages: List<GuidePage>
    get() = listOf(
        GuidePage(
            "Your companion",
            "Things usually locked behind subscriptions, back on your phone.",
            points = listOf(
                DotSymbol.BELL to "Reminders with a call button",
                DotSymbol.PLAY to "YouTube in the background",
                DotSymbol.REPLY to "Auto-replies",
                DotSymbol.VOICE to "Voice mode",
                DotSymbol.RULES to "Rules by place and time",
            ),
        ),
        GuidePage(
            "One switch pauses everything",
            "The Pause switch on Home (also a Quick Settings tile and a home-screen widget) stops every " +
                "automatic action at once: rules, auto-replies, voice mode. Switch it back and they carry on.",
            symbol = DotSymbol.PAUSE,
        ),
        GuidePage(
            "Permissions are per feature",
            "Nothing is asked for up front. Each feature asks only for what it needs when you switch it on, " +
                "and Settings → Permissions & health shows what's missing and why.",
            symbol = DotSymbol.LOCK,
        ),
        GuidePage(
            "Nothing leaves the phone",
            "Your people, reminders, playlists and rules stay on this phone. There are no accounts and " +
                "no cloud. Settings → Export & backup saves a copy of your data wherever you choose.",
            symbol = DotSymbol.SCREEN,
        ),
    )

/**
 * Shown once, before the app's normal screens. Pages can be swiped or stepped with the buttons.
 * [onFinish] gets true when the user asked to go straight to the permissions screen.
 */
@Composable
fun FirstRunGuide(onFinish: (openPermissions: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pages = guidePages
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val index = pager.currentPage
    val last = index == pages.lastIndex
    fun go(page: Int) {
        scope.launch { pager.animateScrollToPage(page) }
    }

    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Progress is shown once, by the page dots above the buttons.
                // Skip jumps to the last page so Start / Open permissions is still one tap away.
                if (!last) TextButton(onClick = { go(pages.lastIndex) }) { Text("Skip") }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
                pageSpacing = 24.dp,
            ) { page -> GuidePageContent(pages[page]) }
            PageDots(count = pages.size, current = index)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val buttonModifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                    if (index > 0) {
                        OutlinedButton(onClick = { go(index - 1) }, modifier = buttonModifier) { Text("Back") }
                    }
                    if (last) {
                        OutlinedButton(onClick = { onFinish(false) }, modifier = buttonModifier) { Text("Start") }
                    } else {
                        Button(onClick = { go(index + 1) }, modifier = buttonModifier) { Text("Next") }
                    }
                }
                // The main action gets the full width (and the thumb-friendly bottom spot) so its label never wraps.
                if (last) {
                    Button(
                        onClick = { onFinish(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text("Open permissions") }
                }
            }
        }
    }
}

@Composable
private fun GuidePageContent(page: GuidePage) {
    val body = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.1.sp)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Hero(page.symbol)
        Text(
            buildAnnotatedString {
                append(page.title)
                withStyle(SpanStyle(color = CompanionRed)) { append(".") }
            },
            style = MaterialTheme.typography.headlineMedium,
            // The red period is decoration; screen readers and tests get the plain title.
            modifier = Modifier
                .padding(top = 12.dp)
                .clearAndSetSemantics {
                    text = AnnotatedString(page.title)
                    heading()
                },
        )
        Text(page.body, style = body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (page.points.isNotEmpty()) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                page.points.forEach { (symbol, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotIcon(symbol, Modifier.size(22.dp))
                        Text(label, style = body, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

/** A dotted card that gives each page a visual anchor, matching Home's display style. */
@Composable
private fun Hero(symbol: DotSymbol?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(187.dp) // 11 rows of DotPattern's 17 dp grid, so no row is cut off
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        DotPattern(Modifier.fillMaxSize())
        if (symbol == null) {
            DotText("My\nCompanion", fontSize = 34.sp)
        } else {
            DotIcon(symbol, Modifier.size(88.dp))
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 24.dp else 8.dp, tween(220), label = "Page dot width")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(220),
                label = "Page dot color",
            )
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Remembers that the guide was seen, in plain SharedPreferences (it's not a setting anyone backs up). */
object FirstRun {
    const val PREFS = "first_run"
    const val KEY_GUIDE_DONE = "guide_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GUIDE_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_GUIDE_DONE, true) }
    }
}
