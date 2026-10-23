package com.akib.mycompanion

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akib.mycompanion.data.CallResult
import com.akib.mycompanion.notifications.NotificationHelper
import com.akib.mycompanion.presentation.MainViewModel
import com.akib.mycompanion.presentation.ui.ContactsScreen
import com.akib.mycompanion.presentation.ui.NotifyScreen
import com.akib.mycompanion.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        setContent { CompanionTheme { CompanionApp() } }
    }
}

@Composable
fun CompanionApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    // Ask once on launch; calling still works without contacts access (opens the chat instead),
    // and notifications simply stay silent until allowed.
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissions.launch(wanted.toTypedArray())
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Call, contentDescription = null) },
                    label = { Text("Contacts") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("Notify") },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            0 -> ContactsScreen(
                contacts = contacts,
                onCall = { contact ->
                    when (vm.callOnWhatsApp(contact)) {
                        CallResult.CALL_STARTED -> Unit
                        CallResult.CHAT_OPENED -> toast(
                            context,
                            "Opened chat. Save this number in your phone contacts for one-tap calling.",
                        )
                        CallResult.WHATSAPP_MISSING -> toast(context, "WhatsApp isn't installed")
                    }
                },
                onAdd = vm::addContact,
                onDelete = vm::removeContact,
                modifier = modifier,
            )
            else -> NotifyScreen(
                onSend = { title, message, delay ->
                    if (!NotificationHelper.canPost(context)) {
                        toast(context, "Allow notifications in system settings first")
                    } else {
                        vm.notify(title, message, delay)
                        toast(context, if (delay == 0) "Sent" else "Scheduled in $delay min")
                    }
                },
                modifier = modifier,
            )
        }
    }
}

private fun toast(context: android.content.Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
