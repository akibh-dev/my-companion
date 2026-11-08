package com.akib.mycompanion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.presentation.ai.AiEnginesRoute
import com.akib.mycompanion.presentation.contacts.ContactsRoute
import com.akib.mycompanion.presentation.health.PermissionsScreen
import com.akib.mycompanion.presentation.home.HomeLink
import com.akib.mycompanion.presentation.home.HomeRoute
import com.akib.mycompanion.presentation.home.PERMISSIONS_ROUTE
import com.akib.mycompanion.presentation.home.SettingsScreen
import com.akib.mycompanion.presentation.log.LogRoute
import com.akib.mycompanion.presentation.player.PlayerRoute
import com.akib.mycompanion.presentation.reminders.RemindersRoute
import com.akib.mycompanion.ui.components.DotText

/** What a screen gets: where to draw, the snackbar host, and how to move between screens. */
class ScreenScope(
    val modifier: Modifier,
    val snackbars: SnackbarHostState,
    val navigate: (String) -> Unit,
    val back: () -> Unit,
)

/**
 * A screen opened from Home, over the tabs, with a back arrow. [route] may carry an `{id}` argument
 * ("rule/{id}"), which the screen's ViewModel reads from its SavedStateHandle.
 */
data class Screen(
    val route: String,
    val title: String,
    val content: @Composable (ScreenScope) -> Unit,
)

/** Which screen each feature's card opens; features add theirs as they're built. */
fun featureRoute(feature: Feature): String? = when (feature) {
    Feature.CONTEXT_RULES -> "rules"
    Feature.KEEP_SCREEN_ON -> "screen-on"
    Feature.AUTO_REPLY, Feature.MISSED_CALL_SMS -> "autoreply"
    Feature.VOICE_MODE -> "voice"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionRoot(
    requirements: List<Requirement> = emptyList(),
    extraScreens: List<Screen> = emptyList(),
    homeLinks: List<HomeLink> = emptyList(),
    requestedTab: Tab? = null,
    requestCount: Int = 0,
    navController: NavHostController = rememberNavController(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val screens = remember(requirements, extraScreens) {
        listOf(
            Screen(PERMISSIONS_ROUTE, "Permissions & health") { PermissionsScreen(requirements, it.modifier) },
            Screen("ai", "AI engines") { AiEnginesRoute(it.modifier) },
            Screen("log", "Log") { LogRoute(it.modifier) },
        ) + extraScreens
    }
    val links = remember(homeLinks) {
        homeLinks + listOf(
            HomeLink("Permissions & health", "What each feature needs, and what's missing", PERMISSIONS_ROUTE),
            HomeLink("AI engines", "Switch each engine between fallback and model, and try it", "ai"),
            HomeLink("Log", "What the app did, kept on this phone", "log"),
        )
    }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    val screen = screens.firstOrNull { it.route == route }
    val settingsOpen = route == "settings"

    LaunchedEffect(requestCount) {
        if (requestedTab != null) navController.openTab(requestedTab)
    }

    Scaffold(
        topBar = {
            if (screen != null || settingsOpen) {
                TopAppBar(
                    title = { DotText(screen?.title ?: "Settings", fontSize = 22.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            } else if (tab != Tab.HOME) {
                TopAppBar(
                    title = { DotText(tab?.label ?: "", fontSize = 28.sp) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (screen == null && !settingsOpen) {
                CompanionNavigation(tab, onSelect = { navController.openTab(it) })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Edge-to-edge: the window isn't resized for the keyboard, so shrink the content above it
        // (and let a focused field scroll into view). consumeWindowInsets stops it counting the bottom bar twice.
        val modifier = Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
        NavHost(navController, startDestination = Tab.HOME.route) {
            composable(Tab.HOME.route) {
                HomeRoute(
                    featureRoute = { feature -> featureRoute(feature)?.takeIf { r -> screens.any { it.route == r } } },
                    links = links,
                    onOpen = { navController.navigate(it) },
                    modifier = modifier,
                )
            }
            composable(Tab.PEOPLE.route) { ContactsRoute(snackbarHostState, modifier) }
            composable(Tab.REMINDERS.route) { RemindersRoute(snackbarHostState, modifier) }
            composable(Tab.PLAYER.route) { PlayerRoute(snackbarHostState, modifier) }
            composable("settings") {
                SettingsScreen(links, onOpen = { navController.navigate(it) }, modifier = modifier)
            }
            screens.forEach { item ->
                composable(item.route) {
                    item.content(
                        ScreenScope(
                            modifier,
                            snackbarHostState,
                            { navController.navigate(it) },
                            { navController.popBackStack() }
                        )
                    )
                }
            }
        }
    }
}

/** Switches tab like a bottom bar should: one copy of each, state kept, back goes to Home. */
private fun NavHostController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Translucent neutral surface, with an inverse pill for the active destination. */
@Composable
private fun CompanionNavigation(selected: Tab?, onSelect: (Tab) -> Unit) {
    Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .96f),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.selectableGroup().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tab.entries.forEach { tab ->
                    val active = selected == tab
                    val background = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                    val foreground = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(25.dp))
                            .background(background)
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painterResource(tab.icon),
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            tab.label,
                            color = foreground,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
