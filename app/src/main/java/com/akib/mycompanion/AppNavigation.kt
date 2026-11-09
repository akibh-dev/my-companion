package com.akib.mycompanion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import com.akib.mycompanion.ui.components.GlassNavigation
import com.akib.mycompanion.ui.components.LocalNavigationScrolled

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
    val scrolled = remember { mutableStateMapOf<String, Boolean>() }
    val backdrop = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showNavigation = screen == null && !settingsOpen && !keyboardVisible
    val reportScroll = remember(route) { { value: Boolean -> scrolled[route.orEmpty()] = value } }

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
            if (showNavigation) {
                GlassNavigation(
                    selected = tab,
                    compact = scrolled[route] == true,
                    backdrop = backdrop,
                    contentOrigin = contentOrigin,
                    onSelect = { navController.openTab(it) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Edge-to-edge: the window isn't resized for the keyboard, so shrink the content above it
        // (and let a focused field scroll into view). consumeWindowInsets stops it counting the bottom bar twice.
        val contentPadding = if (showNavigation) {
            PaddingValues(top = padding.calculateTopPadding())
        } else {
            padding
        }
        val modifier = Modifier
            .padding(contentPadding)
            .consumeWindowInsets(padding)
            .imePadding()
        CompositionLocalProvider(LocalNavigationScrolled provides reportScroll) {
            NavHost(
                navController,
                startDestination = Tab.HOME.route,
                modifier = Modifier.fillMaxSize()
                    .onGloballyPositioned { contentOrigin = it.positionInRoot() }
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    },
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(Tab.HOME.route) {
                    HomeRoute(
                        featureRoute = { feature ->
                            featureRoute(
                                feature
                            )?.takeIf { r -> screens.any { it.route == r } }
                        },
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
}

/** Switches tab like a bottom bar should: one copy of each, state kept, back goes to Home. */
private fun NavHostController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
