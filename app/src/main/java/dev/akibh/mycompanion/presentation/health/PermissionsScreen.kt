package dev.akibh.mycompanion.presentation.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.akibh.mycompanion.core.permissions.Requirement
import dev.akibh.mycompanion.ui.components.Note
import dev.akibh.mycompanion.ui.components.PageMargin
import dev.akibh.mycompanion.ui.components.PrimaryButton
import dev.akibh.mycompanion.ui.components.SecondaryButton
import dev.akibh.mycompanion.ui.components.SectionCard
import dev.akibh.mycompanion.ui.components.SectionGap
import dev.akibh.mycompanion.ui.components.SectionLabel
import dev.akibh.mycompanion.ui.theme.CompanionRed

/**
 * Every permission, the feature that needs it, granted or not, and a button to the right system
 * screen, with the "Allow restricted settings" steps for sideloaded apps (plan Phase 1, NFR-3, NFR-8).
 * Once everything is granted, a "Let's go" button calls [onReady] (Home, from the app's navigation).
 */
@Composable
fun PermissionsScreen(
    requirements: List<Requirement>,
    modifier: Modifier = Modifier,
    onReady: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf<Requirement?>(null) }
    // Runtime permissions are asked in the app; if that's refused (or more is needed), the system screen opens.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        checks++
        asking?.let { if (!it.isGranted(context)) open(context, it) }
        asking = null
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks++ }
    val sorted = remember(requirements, checks) {
        requirements.map {
            it to it.isGranted(context)
        }.sortedWith(compareBy({ it.second }, { it.first.feature?.ordinal ?: -1 }))
    }
    val (granted, missing) = sorted.partition { it.second }
    val onOpen: (Requirement) -> Unit = { requirement ->
        val needed = requirement.runtimePermissions.filter { p ->
            ContextCompat.checkSelfPermission(context, p) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            open(context, requirement)
        } else {
            asking = requirement
            ask.launch(needed.toTypedArray())
        }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = PageMargin, top = 8.dp, end = PageMargin, bottom = SectionGap),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Note(
                "Granted ${granted.size} of ${sorted.size}. Each feature only works with its own.",
                Modifier.padding(horizontal = 8.dp),
            )
        }
        if (onReady != null && missing.isEmpty()) {
            item(key = "ready") { ReadyCard(onReady) }
        }
        if (missing.isNotEmpty()) {
            item(key = "label-missing") { GroupLabel("Needs your OK") }
            items(missing, key = { it.first.id }) { (requirement, _) ->
                RequirementCard(requirement, granted = false) { onOpen(requirement) }
            }
        }
        if (granted.isNotEmpty()) {
            item(key = "label-granted") { GroupLabel("Granted") }
            items(granted, key = { it.first.id }) { (requirement, _) ->
                RequirementCard(requirement, granted = true) { onOpen(requirement) }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    SectionLabel(text, Modifier.padding(start = 8.dp, top = SectionGap - 12.dp))
}

@Composable
private fun ReadyCard(onReady: () -> Unit) {
    SectionCard(contentPadding = PaddingValues(20.dp)) {
        Text(
            buildAnnotatedString {
                append("Ready to roll")
                withStyle(SpanStyle(color = CompanionRed)) { append(".") }
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Everything is granted, so every feature can do its job.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(onClick = onReady, modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp)) {
            Text("Let's go")
        }
    }
}

@Composable
private fun RequirementCard(requirement: Requirement, granted: Boolean, onOpen: () -> Unit) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(requirement.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(requirement.feature?.title, requirement.why).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                requirement.steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                }
                SecondaryButton(onClick = onOpen) {
                    Text(if (requirement.runtimePermissions.isEmpty()) "Open settings" else "Allow")
                }
            }
        }
    }
}

private fun open(context: Context, requirement: Requirement) {
    try {
        context.startActivity(requirement.settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some screens don't exist on every phone: the app's own info page always does.
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
