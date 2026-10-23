package com.akib.mycompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.akib.mycompanion.presentation.contacts.ContactsRoute
import com.akib.mycompanion.presentation.reminders.RemindersRoute
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompanionTheme {
                CompanionRoot()
            }
        }
    }
}

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    PEOPLE("People", "Call someone", Icons.Filled.Person),
    REMINDERS("Reminders", "Reminders", Icons.Filled.Notifications),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.PEOPLE) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.PEOPLE -> ContactsRoute(snackbarHostState, modifier)
            Tab.REMINDERS -> RemindersRoute(snackbarHostState, modifier)
        }
    }
}
