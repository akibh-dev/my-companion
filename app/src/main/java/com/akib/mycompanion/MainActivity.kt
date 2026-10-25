package com.akib.mycompanion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.domain.usecase.CleanUpRemindersUseCase
import com.akib.mycompanion.presentation.home.HomeLink
import com.akib.mycompanion.presentation.home.PERMISSIONS_ROUTE
import com.akib.mycompanion.presentation.onboarding.FirstRun
import com.akib.mycompanion.presentation.onboarding.FirstRunGuide
import com.akib.mycompanion.presentation.shortcuts.RefreshHomeShortcuts
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A FragmentActivity (still a ComponentActivity) so the vault can show the system unlock prompt. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var refreshHomeShortcuts: RefreshHomeShortcuts

    @Inject lateinit var cleanUpReminders: CleanUpRemindersUseCase

    @Inject lateinit var requirements: Set<@JvmSuppressWildcards Requirement>

    /** Feature modules' screens, contributed through Hilt (see FeatureScreensModule). */
    @Inject lateinit var screens: Set<@JvmSuppressWildcards Screen>

    @Inject lateinit var homeLinks: Set<@JvmSuppressWildcards HomeLink>

    /** Tab asked for by an intent (e.g. "Play now" from the share sheet, tapping the media notification). */
    private var requestedTab by mutableStateOf<Tab?>(null)

    /** Bumped on every request so asking for the same tab twice still switches to it. */
    private var requestCount by mutableIntStateOf(0)

    /** Screen asked for by an intent (e.g. "vault" from the home-screen widget); cleared once opened. */
    private var requestedRoute by mutableStateOf<String?>(null)
    private var routeRequestCount by mutableIntStateOf(0)

    /** The one-time guide (Phase 5) is shown before the app's own screens until it's been seen. */
    private var showGuide by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
                        val known = runCatching { navController.graph.findNode(route) != null }.getOrDefault(false)
                        if (known) navController.navigate(route) { launchSingleTop = true }
                    }
                }
            }
        }
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

        /** A screen route to open over Home, e.g. "vault" (see AppNavigation's Screen routes). */
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
    REMINDERS("Reminders", "Reminders", R.drawable.ic_notification, "reminders"),
    PLAYER("Player", "Player", R.drawable.ic_music_note, "player"),
}
