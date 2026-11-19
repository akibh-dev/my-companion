package dev.akibh.mycompanion.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.akibh.mycompanion.core.settings.Feature
import dev.akibh.mycompanion.ui.components.DotIcon
import dev.akibh.mycompanion.ui.components.DotSymbol
import dev.akibh.mycompanion.ui.components.DotText
import dev.akibh.mycompanion.ui.components.rememberChromeListState
import dev.akibh.mycompanion.ui.theme.CompanionRed
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
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    HomeScreen(
        state = state,
        featureRoute = featureRoute,
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
    onPauseChange: (Boolean) -> Unit,
    onFeatureChange: (Feature, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val features = listOf(
        Feature.VOICE_MODE,
        Feature.CONTEXT_RULES,
        Feature.AUTO_REPLY,
        Feature.KEEP_SCREEN_ON,
        Feature.MISSED_CALL_SMS,
    )
    val tiles: List<HomeTileSpec> = features.map { feature ->
        HomeTileSpec(
            title = feature.title,
            symbol = feature.symbol,
            onOpen = featureRoute(feature)?.let { route -> { onOpen(route) } },
            enabled = feature in state.settings.enabled,
            onToggle = { onFeatureChange(feature, it) },
        )
    } + HomeTileSpec(title = "AI engines", symbol = DotSymbol.SPARK, onOpen = { onOpen(AI_ROUTE) })
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // One line, like the other tabs' titles; it shrinks rather than touching the settings button.
                BoxWithConstraints(Modifier.weight(1f).padding(end = 12.dp)) {
                    val fit = with(LocalDensity.current) { (maxWidth * 7f / WORDMARK_COLUMNS).toSp() }
                    DotText("My Companion", fontSize = if (fit < 30.sp) fit else 30.sp)
                }
                SettingsButton { onOpen("settings") }
            }
        }
        item {
            Column(Modifier.padding(top = 20.dp, bottom = 14.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Akib", style = MaterialTheme.typography.headlineSmall)
                    Text(".", style = MaterialTheme.typography.headlineSmall, color = CompanionRed)
                }
                Text(
                    "What would you like to do today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        item { KillSwitch(state.settings.paused, state.lastHeartbeat, onPauseChange) }
        items(tiles.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { tile -> HomeTile(tile, paused = state.settings.paused, modifier = Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
    }
}

/**
 * A big Home tile. With [onToggle] it is a feature switch: the whole tile toggles, filled (inverse)
 * when on, with the dotted icon red while the feature is running, and the arrow opens its screen.
 * Without [onToggle] (a tool such as AI engines) the whole tile simply opens [onOpen].
 */
private data class HomeTileSpec(
    val title: String,
    val symbol: DotSymbol,
    val onOpen: (() -> Unit)?,
    val enabled: Boolean = false,
    val onToggle: ((Boolean) -> Unit)? = null,
)

private val Feature.symbol: DotSymbol
    get() = when (this) {
        Feature.VOICE_MODE -> DotSymbol.VOICE
        Feature.CONTEXT_RULES -> DotSymbol.RULES
        Feature.AUTO_REPLY -> DotSymbol.REPLY
        Feature.MISSED_CALL_SMS -> DotSymbol.MESSAGE
        Feature.KEEP_SCREEN_ON -> DotSymbol.SCREEN
    }

@Composable
private fun HomeTile(tile: HomeTileSpec, paused: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val active = tile.onToggle != null && tile.enabled
    val motion = tween<Color>(200)
    val background by animateColorAsState(
        if (active) colors.primary else colors.surfaceContainer,
        motion,
        label = "Tile background",
    )
    val foreground by animateColorAsState(
        if (active) colors.onPrimary else colors.onSurface,
        motion,
        label = "Tile content",
    )
    // Red means "working right now"; a paused feature keeps its fill but loses the red.
    val iconColor by animateColorAsState(
        if (active && !paused) CompanionRed else foreground,
        motion,
        label = "Tile icon",
    )
    // Paused: automatic features are shaded and can't be switched; their arrow still opens settings.
    val locked = paused && tile.onToggle != null
    val shade by animateFloatAsState(if (locked) .38f else 1f, tween(200), label = "Tile shade")
    val action = when {
        tile.onToggle != null ->
            Modifier
                .toggleable(value = tile.enabled, enabled = !locked, role = Role.Switch, onValueChange = tile.onToggle)
                .semantics(mergeDescendants = true) {
                    stateDescription = when {
                        locked -> if (tile.enabled) "On, unavailable while paused" else "Off, unavailable while paused"
                        !tile.enabled -> "Off"
                        else -> "On"
                    }
                }
        tile.onOpen != null -> Modifier.clickable(role = Role.Button, onClick = tile.onOpen)
        else -> Modifier
    }
    Box(
        modifier
            .graphicsLayer { alpha = shade }
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .then(action),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 4.dp)) {
            DotIcon(tile.symbol, Modifier.size(40.dp), color = iconColor)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tile.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = foreground,
                    modifier = Modifier.weight(1f),
                )
                when {
                    tile.onOpen != null && tile.onToggle != null -> IconButton(
                        onClick = tile.onOpen,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = foreground),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open ${tile.title}",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    tile.onOpen != null -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    else -> Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

/** Nothing-style settings control: a dot-matrix gear on a quiet round chip, with a red centre dot. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Settings" },
        contentAlignment = Alignment.Center,
    ) {
        // A solid dot-matrix cog, like the player controls, with the brand-red hub in its hollow centre.
        DotIcon(DotSymbol.GEAR, Modifier.size(32.dp))
        Box(Modifier.size(5.dp).clip(CircleShape).background(CompanionRed))
    }
}

/**
 * The kill switch, first on Home. The card itself never changes colour or size; only the title
 * (Active / Paused), the live dot and the round button change, so toggling doesn't shift the page.
 */
@Composable
private fun KillSwitch(paused: Boolean, lastHeartbeat: Long?, onPauseChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val button by animateColorAsState(
        if (paused) CompanionRed else colors.primary,
        tween(160),
        label = "Kill switch button",
    )
    // A heartbeat, not a constant pulse: one short beat every few seconds, then the dot rests.
    // A never-ending animation kept Home (and the blurred nav bar behind it) redrawing every frame.
    val live = remember { Animatable(1f) }
    LaunchedEffect(paused) {
        live.snapTo(1f)
        if (paused) return@LaunchedEffect
        while (true) {
            delay(HEARTBEAT_REST_MILLIS)
            live.animateTo(.25f, tween(380))
            live.animateTo(1f, tween(420))
        }
    }
    val beat = lastHeartbeat?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(colors.surfaceContainer)
            // No card-wide ripple: the button's colour change is the feedback, without a flash.
            .toggleable(
                value = paused,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onPauseChange,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Pause all automatic actions"
                stateDescription = if (paused) "Paused" else "Active"
            }
            .padding(start = 20.dp, top = 18.dp, end = 16.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Alpha is read while drawing, so a beat redraws only this dot: no recomposition.
                androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                    drawCircle(CompanionRed.copy(alpha = if (paused) 1f else live.value))
                }
                Text(
                    if (paused) "Paused" else "Active",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                "Pausing stops every automatic feature below until you resume.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                beat?.let { "Last heartbeat $it" } ?: "No heartbeat yet",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .padding(start = 12.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(button),
            contentAlignment = Alignment.Center,
        ) {
            DotIcon(
                if (paused) DotSymbol.PLAY else DotSymbol.PAUSE,
                Modifier.size(26.dp),
                color = if (paused) Color.White else colors.onPrimary,
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
const val AI_ROUTE = "ai"

/** Dot columns "My Companion" spans in the dot alphabet (glyph widths plus one-dot gaps). */
private const val WORDMARK_COLUMNS = 66f

private const val HEARTBEAT_REST_MILLIS = 3_200L
