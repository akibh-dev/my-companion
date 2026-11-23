package dev.akibh.mycompanion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.core.host.ServiceHost
import dev.akibh.mycompanion.core.permissions.Requirement
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.domain.usecase.CleanUpRemindersUseCase
import dev.akibh.mycompanion.presentation.home.HomeLink
import dev.akibh.mycompanion.presentation.home.PERMISSIONS_ROUTE
import dev.akibh.mycompanion.presentation.onboarding.FirstRun
import dev.akibh.mycompanion.presentation.onboarding.FirstRunGuide
import dev.akibh.mycompanion.presentation.shortcuts.RefreshHomeShortcuts
import dev.akibh.mycompanion.ui.theme.CompanionTheme
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Hosts the Compose screens and handles navigation intents. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var refreshHomeShortcuts: RefreshHomeShortcuts

    @Inject lateinit var cleanUpReminders: CleanUpRemindersUseCase

    @Inject lateinit var serviceHost: ServiceHost

    @Inject lateinit var settingsStore: SettingsStore

    @Inject lateinit var requirements: Set<@JvmSuppressWildcards Requirement>

    /** Feature modules' screens, contributed through Hilt (see FeatureScreensModule). */
    @Inject lateinit var screens: Set<@JvmSuppressWildcards Screen>

    @Inject lateinit var homeLinks: Set<@JvmSuppressWildcards HomeLink>

    /** Tab asked for by an intent (e.g. "Play now" from the share sheet, tapping the media notification). */
    private var requestedTab by mutableStateOf<Tab?>(null)

    /** Bumped on every request so asking for the same tab twice still switches to it. */
    private var requestCount by mutableIntStateOf(0)

    /** Screen asked for by an intent (e.g. "export"); cleared once opened. */
    private var requestedRoute by mutableStateOf<String?>(null)
    private var routeRequestCount by mutableIntStateOf(0)

    /** The one-time guide (Phase 5) is shown before the app's own screens until it's been seen. */
    private var showGuide by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        letLaunchAnimationFinish()
        if (savedInstanceState == null) {
            handle(intent)
            lifecycleScope.launch {
                refreshHomeShortcuts()
                cleanUpReminders()
            }
        }
        showGuide = !FirstRun.isDone(this)
        setContent {
            CompanionTheme {
                if (showGuide) {
                    FirstRunGuide(onFinish = ::finishGuide)
                } else {
                    val navController = rememberNavController()
                    CompanionRoot(
                        requirements = requirements.sortedBy { it.title },
                        extraScreens = screens.toList(),
                        homeLinks = homeLinks.toList(),
                        requestedTab = requestedTab,
                        requestCount = requestCount,
                        navController = navController,
                    )
                    LaunchedEffect(routeRequestCount) {
                        val route = requestedRoute ?: return@LaunchedEffect
                        requestedRoute = null
                        // The graph is set while CompanionRoot composes; skip routes it doesn't have
                        // (e.g. a feature screen that isn't part of this build).
                        val node = runCatching { navController.graph.findNode(route) }.getOrNull()
                            ?: return@LaunchedEffect
                        // Already on that screen with other arguments (the Quran widget opening another
                        // page): single-top would keep the old screen, so replace it.
                        val current = navController.currentBackStackEntry?.destination?.id
                        if (current == node.id) navController.popBackStack()
                        navController.navigate(route) { launchSingleTop = true }
                    }
                }
            }
        }
    }

    /**
     * The splash screen normally leaves as soon as the first frame is drawn, which cuts Buddy's wink
     * short. Taking over its exit keeps it until the icon animation has played, then fades it out.
     */
    private fun letLaunchAnimationFinish() {
        splashScreen.setOnExitAnimationListener { view ->
            val start = view.iconAnimationStart
            val length = view.iconAnimationDuration
            // Both are null when the icon isn't animated (or was already done): just fade.
            val wait = if (start == null || length == null) {
                0L
            } else {
                Duration.between(Instant.now(), start.plus(length)).toMillis().coerceIn(0L, MAX_SPLASH_WAIT_MS)
            }
            view.animate()
                .alpha(0f)
                .setStartDelay(wait + SPLASH_HOLD_MS)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction { view.remove() }
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        // A start from the background may have been refused (e.g. a settings change while the app
        // was closed); the app being in front is always allowed, so try again here.
        lifecycleScope.launch { serviceHost.sync(settingsStore.settings.first()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }
        if (tab != null) {
            requestedTab = tab
            requestCount++
        }
        intent?.getStringExtra(EXTRA_ROUTE)?.let(::openRoute)
    }

    private fun openRoute(route: String) {
        requestedRoute = route
        routeRequestCount++
    }

    private fun finishGuide(openPermissions: Boolean) {
        FirstRun.markDone(this)
        if (openPermissions) openRoute(PERMISSIONS_ROUTE)
        showGuide = false
    }

    companion object {
        const val EXTRA_TAB = "tab"

        /** Launch animation: never hold the app back more than this, a short beat on the red eye, then the fade. */
        private const val MAX_SPLASH_WAIT_MS = 1000L
        private const val SPLASH_HOLD_MS = 150L
        private const val SPLASH_FADE_MS = 220L

        /** A screen route to open over Home, e.g. "export" (see AppNavigation's Screen routes). */
        const val EXTRA_ROUTE = "route"

        fun openPlayerIntent(context: Context): Intent = openTabIntent(context, Tab.PLAYER)

        /** Brings the app forward on [tab]; an already-open app gets it in onNewIntent. */
        fun openTabIntent(context: Context, tab: Tab): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TAB, tab.name)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )

        /** Brings the app forward on the screen with this [route]; an already-open app gets it in onNewIntent. */
        fun openRouteIntent(context: Context, route: String): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
    }
}

enum class Tab(val label: String, val title: String, @DrawableRes val icon: Int, val route: String) {
    HOME("Home", "My Companion", R.drawable.ic_home, "home"),
    PEOPLE("People", "Call someone", R.drawable.ic_call, "people"),
    REMINDERS("Reminders", "Reminders", R.drawable.ic_reminder, "reminders"),
    PLAYER("Player", "Player", R.drawable.ic_music_note, "player"),
}
