package com.akib.mycompanion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import com.akib.mycompanion.domain.usecase.CleanUpRemindersUseCase
import com.akib.mycompanion.presentation.contacts.ContactsRoute
import com.akib.mycompanion.presentation.player.PlayerRoute
import com.akib.mycompanion.presentation.reminders.RemindersRoute
import com.akib.mycompanion.presentation.shortcuts.RefreshHomeShortcuts
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var refreshHomeShortcuts: RefreshHomeShortcuts

    @Inject lateinit var cleanUpReminders: CleanUpRemindersUseCase

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
                CompanionRoot(requestedTab, requestCount)
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

enum class Tab(val label: String, val title: String, @DrawableRes val icon: Int) {
    PEOPLE("People", "Call someone", R.drawable.ic_call),
    REMINDERS("Reminders", "Reminders", R.drawable.ic_notification),
    PLAYER("Player", "Player", R.drawable.ic_music_note),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionRoot(requestedTab: Tab? = null, requestCount: Int = 0) {
    var tab by rememberSaveable { mutableStateOf(requestedTab ?: Tab.PEOPLE) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(requestCount) {
        if (requestedTab != null) tab = requestedTab
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Edge-to-edge: the window isn't resized for the keyboard, so shrink the tab content above it
        // (and let a focused field scroll into view). consumeWindowInsets stops it counting the bottom bar twice.
        val modifier = Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
        when (tab) {
            Tab.PEOPLE -> ContactsRoute(snackbarHostState, modifier)
            Tab.REMINDERS -> RemindersRoute(snackbarHostState, modifier)
            Tab.PLAYER -> PlayerRoute(snackbarHostState, modifier)
        }
    }
}
