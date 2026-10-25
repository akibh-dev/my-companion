package com.akib.mycompanion

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import com.akib.mycompanion.presentation.log.LogRoute
import com.akib.mycompanion.presentation.player.PlayerRoute
import com.akib.mycompanion.presentation.reminders.RemindersRoute

/** A screen opened from Home, over the tabs, with a back arrow. */
data class Screen(
    val route: String,
    val title: String,
    val content: @Composable (modifier: Modifier, snackbars: SnackbarHostState, onBack: () -> Unit) -> Unit,
)

/** Which screen each feature's card opens; features add theirs as they're built. */
fun featureRoute(feature: Feature): String? = when (feature) {
    Feature.CONTEXT_RULES, Feature.KEEP_SCREEN_ON -> "rules"
    Feature.AUTO_REPLY, Feature.MISSED_CALL_SMS -> "autoreply"
    Feature.VOICE_MODE -> "voice"
    Feature.CAPTURE -> "capture"
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
            Screen(PERMISSIONS_ROUTE, "Permissions & health") { modifier, _, _ ->
                PermissionsScreen(requirements, modifier)
            },
            Screen("ai", "AI engines") { modifier, _, _ -> AiEnginesRoute(modifier) },
            Screen("log", "Log") { modifier, _, _ -> LogRoute(modifier) },
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

    LaunchedEffect(requestCount) {
        if (requestedTab != null) navController.openTab(requestedTab)
    }

    Scaffold(
        topBar = {
            if (screen != null) {
                TopAppBar(
                    title = { Text(screen.title) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            } else {
                CenterAlignedTopAppBar(title = { Text(tab?.title ?: "") })
            }
        },
        bottomBar = {
            if (screen == null) {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { navController.openTab(item) },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
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
            screens.forEach { item ->
                composable(item.route) { item.content(modifier, snackbarHostState) { navController.popBackStack() } }
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
