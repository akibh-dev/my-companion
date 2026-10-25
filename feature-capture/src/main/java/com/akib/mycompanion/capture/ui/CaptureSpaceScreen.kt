package com.akib.mycompanion.capture.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.capture.domain.Capture
import com.akib.mycompanion.capture.domain.CaptureSearch
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.capture.system.captureServiceEnabled
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The capture space (FR-4.3): every capture on a timeline, newest first and grouped by day, with
 * search and type filters. Needs the phone unlocked (FR-4.8).
 */
@Composable
fun CaptureSpaceRoute(
    onOpenCapture: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureSpaceViewModel = hiltViewModel(),
) {
    if (rememberIsLocked()) {
        LockedNotice(modifier)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Switched on in system settings: look again whenever you come back.
    var serviceOn by remember { mutableStateOf(captureServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceOn = captureServiceEnabled(context) }
    // Owned by Compose, not round-tripped through the ViewModel, so typing never lags or jumps.
    var query by rememberSaveable { mutableStateOf("") }
    val zone = remember { ZoneId.systemDefault() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hold both volume keys", style = MaterialTheme.typography.titleSmall)
                    Text(
                        triggerStatus(state.triggerOn, state.paused, serviceOn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.triggerOn && !serviceOn) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text("Turn on the accessibility service") }
                    }
                }
                Switch(checked = state.triggerOn, onCheckedChange = viewModel::setTrigger)
            }
        }
        item {
            Button(
                onClick = { context.startActivity(CapturePanelActivity.intent(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("New capture") }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { text ->
                    query = text
                    viewModel.onFilter { it.copy(query = text) }
                },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CaptureType.entries.forEach { type ->
                    FilterChip(
                        selected = state.filter.type == type,
                        onClick = { viewModel.onFilter { it.copy(type = if (it.type == type) null else type) } },
                        label = { Text(type.title) },
                    )
                }
            }
        }
        if (state.loaded && state.groups.isEmpty()) {
            item {
                Text(
                    if (state.total == 0) {
                        "Nothing captured yet. Hold both volume keys for a second, use the Quick capture tile, " +
                            "or tap New capture."
                    } else {
                        "No captures match."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.groups.forEach { group ->
            item(key = "day-${group.day}") {
                Text(
                    CaptureSearch.dayLabel(group.day, state.today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(group.captures, key = { it.id }) { capture ->
                CaptureRow(capture, zone) { onOpenCapture(capture.id) }
            }
        }
    }
}

@Composable
private fun CaptureRow(capture: Capture, zone: ZoneId, onClick: () -> Unit) {
    val detail = preview(capture)
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            overlineContent = {
                Text(
                    listOfNotNull(
                        capture.type.title,
                        formatTime(capture.created, zone),
                        "In the vault".takeIf { capture.movedToVault },
                    ).joinToString(" · "),
                )
            },
            headlineContent = { Text(capture.displayTitle, maxLines = 1) },
            supportingContent = if (detail != null) {
                { Text(detail, maxLines = 2) }
            } else {
                null
            },
        )
    }
}

private fun preview(capture: Capture): String? = when {
    capture.movedToVault -> null
    capture.summary.isNotBlank() -> capture.summary
    capture.transcript.isNotBlank() -> capture.transcript
    capture.text.isNotBlank() && capture.title.isNotBlank() -> capture.text
    capture.type == CaptureType.VOICE -> "Audio only"
    else -> null
}

internal fun formatTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(millis).atZone(zone))

internal fun formatDateTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(millis).atZone(zone))

/** FR-4.8: captures are only shown with the phone unlocked. Checked again whenever the screen resumes. */
@Composable
internal fun rememberIsLocked(): Boolean {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(isLocked(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { locked = isLocked(context) }
    return locked
}

/** "Unlock to see captures", with a button that asks for the unlock. */
@Composable
internal fun LockedNotice(modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Unlock to see captures", style = MaterialTheme.typography.titleLarge)
        Text(
            "New captures work on the lock screen; your saved ones stay hidden until the phone is unlocked.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = {
            context.findActivity()?.let { activity ->
                context.getSystemService(KeyguardManager::class.java).requestDismissKeyguard(activity, null)
            }
        }) { Text("Unlock") }
    }
}

private fun isLocked(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What the volume-key switch says, given whether the system service it relies on is on (FR-4.1). */
internal fun triggerStatus(triggerOn: Boolean, paused: Boolean, serviceOn: Boolean): String = when {
    !triggerOn -> "Off. You can still use the tile or the button below."
    !serviceOn -> "On, but the quick capture accessibility service is off, so the keys aren't watched."
    paused -> "On, but everything is paused right now."
    else -> "Hold both for a second to open the capture panel."
}
