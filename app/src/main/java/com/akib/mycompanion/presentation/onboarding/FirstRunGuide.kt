package com.akib.mycompanion.presentation.onboarding

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit

/** One page of the first-run guide. */
data class GuidePage(val title: String, val body: String)

/** The four things worth knowing before using the app (Phase 5, "first-run guide"). */
val guidePages: List<GuidePage>
    get() = listOf(
        GuidePage(
            "Your companion",
            "My Companion brings back things usually locked behind subscriptions: reminders with a call " +
                "button, YouTube in the background, auto-replies, voice mode, quick capture, an encrypted vault " +
                "and rules that change your phone's settings by place and time.",
        ),
        GuidePage(
            "One switch pauses everything",
            "The Pause switch on Home (also a Quick Settings tile and a home-screen widget) stops every " +
                "automatic action at once: rules, auto-replies, voice mode. Switch it back and they carry on.",
        ),
        GuidePage(
            "Permissions are per feature",
            "Nothing is asked for up front. Each feature asks only for what it needs when you switch it on, " +
                "and Permissions & health on Home shows what's missing and why.",
        ),
        GuidePage(
            "Nothing leaves the phone",
            "Your people, reminders, rules, captures and vault stay on this phone. There are no accounts and " +
                "no cloud. Home → Export & backup saves a copy of your data wherever you choose.",
        ),
    )

/**
 * Shown once, before the app's normal screens. [onFinish] gets true when the user asked to go straight
 * to the permissions screen.
 */
@Composable
fun FirstRunGuide(onFinish: (openPermissions: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val pages = guidePages
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[index]
    val last = index == pages.lastIndex

    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = { onFinish(false) }) { Text("Skip") }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(page.title, style = MaterialTheme.typography.headlineMedium)
                Text(page.body, style = MaterialTheme.typography.bodyLarge)
            }
            PageDots(count = pages.size, current = index)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (index > 0) {
                    OutlinedButton(onClick = { index-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                }
                if (last) {
                    OutlinedButton(onClick = { onFinish(false) }, modifier = Modifier.weight(1f)) { Text("Start") }
                    Button(onClick = { onFinish(true) }, modifier = Modifier.weight(1f)) { Text("Open permissions") }
                } else {
                    Button(onClick = { index++ }, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }
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
            Surface(
                shape = CircleShape,
                color = if (i == current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(8.dp),
            ) { Spacer(Modifier.size(8.dp)) }
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
