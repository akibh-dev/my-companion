package com.akib.mycompanion.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.ui.components.DotIcon
import com.akib.mycompanion.ui.components.DotSymbol
import com.akib.mycompanion.ui.components.DotText
import com.akib.mycompanion.ui.components.rememberChromeListState
import com.akib.mycompanion.ui.theme.CompanionRed
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Something Home can open: a feature screen or a tool. */
data class HomeLink(val title: String, val description: String, val route: String)

@Composable
fun HomeRoute(
    featureRoute: (Feature) -> String?,
    links: List<HomeLink>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    HomeScreen(
        state = state,
        featureRoute = featureRoute,
        links = links,
        onPauseChange = viewModel::onPauseChange,
        onFeatureChange = viewModel::onFeatureChange,
        onOpen = onOpen,
        modifier = modifier,
    )
}

/** Approved monochrome home, with the real feature switches and service health. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    featureRoute: (Feature) -> String?,
    links: List<HomeLink>,
    onPauseChange: (Boolean) -> Unit,
    onFeatureChange: (Feature, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(Feature.VOICE_MODE, Feature.CONTEXT_RULES, Feature.AUTO_REPLY, Feature.KEEP_SCREEN_ON)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hour by produceState(initialValue = LocalTime.now().hour, key1 = lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = LocalTime.now().hour
                delay(60_000L)
            }
        }
    }
    val greeting = when (hour) {
        in 5..11 -> "Good morning,"
        in 12..16 -> "Good afternoon,"
        else -> "Good evening,"
    }
    LazyColumn(
        state = rememberChromeListState(),
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DotText("My\nCompanion", fontSize = 26.sp)
                androidx.compose.material3.IconButton(onClick = {
                    onOpen("settings")
                }, modifier = Modifier.semantics { contentDescription = "Settings" }) {
                    DotIcon(DotSymbol.TOOLS, Modifier.size(34.dp))
                }
            }
        }
        item {
            Column(Modifier.padding(top = 20.dp, bottom = 14.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineLarge)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Akib", style = MaterialTheme.typography.headlineLarge)
                    Text(".", style = MaterialTheme.typography.headlineLarge, color = CompanionRed)
                }
                Text(
                    "What would you like to do today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        items(tiles.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { feature ->
                    FeatureTile(
                        feature = feature,
                        enabled = feature in state.settings.enabled,
                        paused = state.settings.paused,
                        inverted = feature == Feature.VOICE_MODE,
                        onOpen = featureRoute(feature)?.let { route -> { onOpen(route) } },
                        onToggle = { onFeatureChange(feature, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { PauseCard(state.settings.paused, onPauseChange) }
        if (state.hostDown) {
            item {
                Warning(
                    "Background features aren't running",
                    "Android stopped the service. Check battery settings in Permissions.",
                ) { onOpen(PERMISSIONS_ROUTE) }
            }
        }
        if (state.missing.isNotEmpty()) {
            item {
                Warning(
                    "${state.missing.size} permission${if (state.missing.size == 1) "" else "s"} missing",
                    state.missing.joinToString(", ") { it.title },
                ) { onOpen(PERMISSIONS_ROUTE) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    headlineContent = { Text(Feature.MISSED_CALL_SMS.title) },
                    supportingContent = { Text(Feature.MISSED_CALL_SMS.description) },
                    modifier = Modifier.clickable { featureRoute(Feature.MISSED_CALL_SMS)?.let(onOpen) },
                    trailingContent = {
                        Switch(
                            checked = Feature.MISSED_CALL_SMS in state.settings.enabled,
                            onCheckedChange = { onFeatureChange(Feature.MISSED_CALL_SMS, it) },
                            modifier = Modifier.semantics { contentDescription = "Enable missed-call SMS" },
                        )
                    },
                )
            }
        }
        item { Text("More", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
        items(links, key = { it.route }) { link ->
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.clickable { onOpen(link.route) },
                    headlineContent = { Text(link.title) },
                    supportingContent = { Text(link.description) },
                )
            }
        }
        item {
            val beat = state.lastHeartbeat?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
            Text(
                beat?.let { "Last heartbeat: $it" } ?: "No heartbeat yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureTile(
    feature: Feature,
    enabled: Boolean,
    paused: Boolean,
    inverted: Boolean,
    onOpen: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (inverted) colors.primary else colors.surfaceContainer
    val foreground = if (inverted) colors.onPrimary else colors.onSurface
    Card(
        onClick = { onOpen?.invoke() },
        enabled = onOpen != null,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = background, contentColor = foreground),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DotIcon(
                    when (feature) {
                        Feature.VOICE_MODE -> DotSymbol.VOICE
                        Feature.CONTEXT_RULES -> DotSymbol.RULES
                        Feature.AUTO_REPLY, Feature.MISSED_CALL_SMS -> DotSymbol.REPLY
                        Feature.KEEP_SCREEN_ON -> DotSymbol.SCREEN
                    },
                    Modifier.size(40.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .toggleable(
                            value = enabled,
                            role = androidx.compose.ui.semantics.Role.Switch,
                            onValueChange = onToggle
                        )
                        .semantics { contentDescription = "Enable ${feature.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (enabled) {
                            if (paused) "Paused" else "On"
                        } else {
                            "Off"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(feature.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PauseCard(paused: Boolean, onPauseChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(5.dp)) { drawCircle(CompanionRed) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(if (paused) "Paused" else "Running", style = MaterialTheme.typography.labelLarge)
            Text(
                if (paused) "Automatic actions are resting" else "Quietly, on your phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pause", style = MaterialTheme.typography.labelSmall)
            Switch(
                checked = paused,
                onCheckedChange = onPauseChange,
                modifier = Modifier.semantics { contentDescription = "Pause all automatic actions" },
            )
        }
    }
}

@Composable
private fun Warning(title: String, detail: String, onFix: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

const val PERMISSIONS_ROUTE = "permissions"
