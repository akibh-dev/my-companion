package com.akib.mycompanion.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.core.settings.Feature
import java.text.DateFormat
import java.util.Date

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

/** Feature status and the pause switch (plan §11, "App shell: Home"). */
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
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        item { Text("Features", style = MaterialTheme.typography.titleMedium) }
        items(Feature.entries, key = { it.name }) { feature ->
            val route = featureRoute(feature)
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = if (route != null) Modifier.clickable { onOpen(route) } else Modifier,
                    headlineContent = { Text(feature.title) },
                    supportingContent = { Text(feature.description) },
                    trailingContent = {
                        Switch(
                            checked = feature in state.settings.enabled,
                            onCheckedChange = { onFeatureChange(feature, it) },
                        )
                    },
                )
            }
        }
        item { Text("More", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(links, key = { it.route }) { link ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
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
private fun PauseCard(paused: Boolean, onPauseChange: (Boolean) -> Unit) {
    val colors = if (paused) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (paused) "Paused" else "Running", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (paused) "No automatic actions until you resume" else "Automatic actions are on",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = paused, onCheckedChange = onPauseChange)
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
