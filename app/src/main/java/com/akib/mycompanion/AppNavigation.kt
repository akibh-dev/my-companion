package com.akib.mycompanion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
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
import com.akib.mycompanion.presentation.home.AI_ROUTE
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
            Screen(PERMISSIONS_ROUTE, "Permissions & health") {
                PermissionsScreen(requirements, it.modifier, onReady = { navController.backToHome() })
            },
            Screen(AI_ROUTE, "AI engines") { AiEnginesRoute(it.modifier) },
            Screen("log", "Log") { LogRoute(it.modifier) },
        ) + extraScreens
    }
    val links = remember(homeLinks) {
        homeLinks + listOf(
            HomeLink("Permissions & health", "What each feature needs, and what's missing", PERMISSIONS_ROUTE),
            HomeLink("Log", "What the app did, kept on this phone", "log"),
        )
    }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    // While the bar slides away (opening a screen), it keeps showing the tab you came from.
    var lastTab by remember { mutableStateOf(Tab.HOME) }
    SideEffect { if (tab != null) lastTab = tab }
    val scrolled = remember { mutableStateMapOf<String, Boolean>() }
    val backdrop = rememberGraphicsLayer()
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showNavigation = tab != null && !keyboardVisible
    val reportScroll = remember(route) { { value: Boolean -> scrolled[route.orEmpty()] = value } }
    val back: () -> Unit = { navController.popBackStack() }

    LaunchedEffect(requestCount) {
        if (requestedTab != null) navController.openTab(requestedTab)
    }

    // Each page draws its own header, so a title moves with its page during transitions instead of
    // switching under it. The nav bar and snackbars float over the pages.
    AppSurface {
        Box(Modifier.fillMaxSize()) {
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
                    enterTransition = { if (betweenTabs()) tabEnter else screenEnter },
                    exitTransition = { if (betweenTabs()) tabExit else screenExit },
                    popEnterTransition = { if (betweenTabs()) tabEnter else screenPopEnter },
                    popExitTransition = { if (betweenTabs()) tabExit else screenPopExit },
                ) {
                    composable(Tab.HOME.route) {
                        Page(header = { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }) {
                            HomeRoute(
                                featureRoute = { feature ->
                                    featureRoute(feature)?.takeIf { r -> screens.any { it.route == r } }
                                },
                                onOpen = { navController.navigate(it) },
                                modifier = it,
                            )
                        }
                    }
                    composable(Tab.PEOPLE.route) {
                        Page(header = { TabHeader(Tab.PEOPLE.label) }) { ContactsRoute(snackbarHostState, it) }
                    }
                    composable(Tab.REMINDERS.route) {
                        Page(header = { TabHeader(Tab.REMINDERS.label) }) { RemindersRoute(snackbarHostState, it) }
                    }
                    composable(Tab.PLAYER.route) {
                        Page(header = { TabHeader(Tab.PLAYER.label) }) { PlayerRoute(snackbarHostState, it) }
                    }
                    composable("settings") {
                        Page(header = { ScreenHeader("Settings", back) }, overNavigationBar = false) {
                            SettingsScreen(links, onOpen = { navController.navigate(it) }, modifier = it)
                        }
                    }
                    screens.forEach { item ->
                        composable(item.route) {
                            Page(header = { ScreenHeader(item.title, back) }, overNavigationBar = false) {
                                item.content(ScreenScope(it, snackbarHostState, { navController.navigate(it) }, back))
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                SnackbarHost(
                    snackbarHostState,
                    Modifier.then(if (showNavigation) Modifier else Modifier.navigationBarsPadding().imePadding()),
                )
                AnimatedVisibility(
                    visible = showNavigation,
                    enter = slideInVertically(tween(260, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it } + fadeOut(tween(160)),
                ) {
                    GlassNavigation(
                        selected = tab ?: lastTab,
                        compact = scrolled[route] == true,
                        backdrop = backdrop,
                        contentOrigin = contentOrigin,
                        onSelect = { navController.openTab(it) },
                    )
                }
            }
        }
    }
}

/**
 * The app's backdrop. A Surface, not a bare Box, so text without its own colour gets onBackground.
 * Default text is otherwise black, which vanished in dark mode (see AppSurfaceTest).
 */
@Composable
internal fun AppSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        content = content,
    )
}

/**
 * One destination: its [header] above its content, on an opaque background so pages don't show
 * through each other mid-transition. Tabs draw under the floating nav bar ([overNavigationBar]);
 * other screens stop above the system navigation bar. The content shrinks for the keyboard.
 */
@Composable
private fun Page(
    header: @Composable () -> Unit,
    overNavigationBar: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        header()
        val bottom = if (overNavigationBar) {
            Modifier
        } else {
            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        }
        content(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(bottom)
                .imePadding(),
        )
    }
}

/** A screen opened over the tabs: back arrow and dotted title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { DotText(title, fontSize = 22.sp) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

// ---- Motion ----
// Tabs fade through each other (a quick fade out, then the new tab fades and settles in): they're
// siblings, so nothing slides. Screens opened from a tab move along the x axis, so going deeper
// slides in from the right and Back (including the predictive back swipe) slides it away again.

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val tabRoutes = Tab.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

private val tabEnter = fadeIn(tween(210, delayMillis = 70, easing = EmphasizedDecelerate)) +
    scaleIn(tween(210, delayMillis = 70, easing = EmphasizedDecelerate), initialScale = .97f)
private val tabExit = fadeOut(tween(90))

private val screenEnter = slideInHorizontally(tween(320, easing = EmphasizedDecelerate)) { it / 4 } +
    fadeIn(tween(200, easing = EmphasizedDecelerate))
private val screenExit = slideOutHorizontally(tween(320, easing = EmphasizedDecelerate)) { -it / 10 } +
    fadeOut(tween(200))
private val screenPopEnter = slideInHorizontally(tween(300, easing = EmphasizedDecelerate)) { -it / 10 } +
    fadeIn(tween(200))
private val screenPopExit = slideOutHorizontally(tween(260, easing = EmphasizedAccelerate)) { it / 4 } +
    fadeOut(tween(200, easing = EmphasizedAccelerate))

/**
 * Closes every screen above Home. Not [openTab]: its saved state would restore the screen being left.
 */
private fun NavHostController.backToHome() {
    if (!popBackStack(Tab.HOME.route, inclusive = false)) openTab(Tab.HOME)
}

/** Switches tab like a bottom bar should: one copy of each, state kept, back goes to Home. */
private fun NavHostController.openTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Title of the People, Reminders and Player tabs. Same geometry as Home's wordmark row
 * (24 dp in from the edge, a 52 dp row 24 dp below the status bar) so the dotted titles line up.
 */
@Composable
private fun TabHeader(title: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, top = 24.dp, end = 24.dp)
            .height(52.dp)
            .semantics { heading() },
        contentAlignment = Alignment.CenterStart,
    ) {
        DotText(title, fontSize = 28.sp)
    }
}
