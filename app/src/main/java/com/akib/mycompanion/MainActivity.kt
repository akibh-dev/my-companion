package com.akib.mycompanion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.domain.usecase.CleanUpRemindersUseCase
import com.akib.mycompanion.presentation.home.HomeLink
import com.akib.mycompanion.presentation.shortcuts.RefreshHomeShortcuts
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
        setContent {
            CompanionTheme {
                CompanionRoot(
                    requirements = requirements.sortedBy { it.title },
                    extraScreens = screens.toList(),
                    homeLinks = homeLinks.toList(),
                    requestedTab = requestedTab,
                    requestCount = requestCount,
                )
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
    }

    companion object {
        const val EXTRA_TAB = "tab"

        fun openPlayerIntent(context: Context): Intent = openTabIntent(context, Tab.PLAYER)

        /** Brings the app forward on [tab]; an already-open app gets it in onNewIntent. */
        fun openTabIntent(context: Context, tab: Tab): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TAB, tab.name)
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
