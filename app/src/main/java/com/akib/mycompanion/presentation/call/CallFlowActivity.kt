package com.akib.mycompanion.presentation.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.presentation.contacts.CallMessages
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

/** Visible recovery UI; the noHistory shortcut trampoline never owns a permission result. */
@AndroidEntryPoint
class CallFlowActivity : ComponentActivity() {
    private val flow: CallFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contactId = intent.getLongExtra(CallActivity.EXTRA_CONTACT_ID, -1)
        if (contactId < 0 || !CallToken.isValid(this, intent.getStringExtra(CallActivity.EXTRA_TOKEN))) {
            finish()
            return
        }
        val notificationId = intent.getIntExtra(CallActivity.EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) NotificationManagerCompat.from(this).cancel(notificationId)
        flow.start(contactId)
        setContent {
            CompanionTheme {
                val state by flow.state.collectAsStateWithLifecycle()
                val permission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        flow.permissionReturned(granted, !granted && permanentlyDenied())
                    }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) flow.onForeground(hasPermission(), permanentlyDenied())
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(state) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        flow.onForeground(hasPermission(), permanentlyDenied())
                    }
                    if (state == CallFlowState.HandedOff) finish()
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("WhatsApp call", style = MaterialTheme.typography.headlineSmall)
                        when (val current = state) {
                            CallFlowState.Loading -> Text("Checking the current WhatsApp contact action…")
                            is CallFlowState.Permission -> {
                                Text(
                                    "Contacts access lets My Companion find " +
                                        "WhatsApp's voice-call action for this number. " +
                                        "Saving a person here does not add them to your phone contacts " +
                                        "or sync WhatsApp."
                                )
                                if (current.requesting) {
                                    Text("Waiting for your contacts permission choice…")
                                } else {
                                    if (current.permanent) {
                                        Text(
                                            "Contacts access is off. Enable it in app settings, " +
                                                "or open the chat instead."
                                        )
                                        Button(onClick = ::openSettings) { Text("Open app settings") }
                                    } else {
                                        Button(onClick = {
                                            if (flow.permissionRequested()) {
                                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                                    .putBoolean(REQUESTED, true).apply()
                                                permission.launch(Manifest.permission.READ_CONTACTS)
                                            }
                                        }) { Text("Allow contacts access") }
                                    }
                                    TextButton(onClick = flow::openChat) { Text("Open chat instead") }
                                }
                            }
                            is CallFlowState.Choose -> {
                                Text("Choose the WhatsApp account for this attempt.")
                                current.variants.forEach { variant ->
                                    Button(onClick = { flow.select(variant) }) { Text(variant.title) }
                                }
                            }
                            is CallFlowState.Unavailable -> {
                                Text(CallMessages.forFailure(current.reason))
                                TextButton(onClick = flow::openChat) { Text("Open chat instead") }
                            }
                            is CallFlowState.Ready -> Text("Opening the selected WhatsApp action…")
                            is CallFlowState.Completed -> {
                                Text(current.message)
                                if (!hasPermission()) {
                                    TextButton(onClick = ::openSettings) { Text("Open app settings") }
                                }
                            }
                            CallFlowState.HandedOff -> Text("Handed over to WhatsApp.")
                        }
                        TextButton(onClick = ::finish) { Text("Close") }
                    }
                }
            }
        }
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

    private fun permanentlyDenied(): Boolean = !hasPermission() &&
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(REQUESTED, false) &&
        !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    companion object {
        private const val PREFS = "call_contacts_permission"
        private const val REQUESTED = "requested"

        fun intent(context: Context, contactId: Long, notificationId: Int? = null): Intent =
            Intent(context, CallFlowActivity::class.java)
                .putExtra(CallActivity.EXTRA_CONTACT_ID, contactId)
                .putExtra(CallActivity.EXTRA_TOKEN, CallToken.get(context))
                .apply { if (notificationId != null) putExtra(CallActivity.EXTRA_NOTIFICATION_ID, notificationId) }
    }
}
