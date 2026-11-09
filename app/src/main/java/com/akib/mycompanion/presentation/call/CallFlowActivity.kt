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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.domain.port.CallAccess
import com.akib.mycompanion.domain.port.WhatsAppCallKind
import com.akib.mycompanion.presentation.contacts.CallMessages
import com.akib.mycompanion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

/** Visible recovery UI; the noHistory shortcut trampoline never owns a permission result. */
@AndroidEntryPoint
class CallFlowActivity : ComponentActivity() {
    private val flow: CallFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val contactId = intent.getLongExtra(CallActivity.EXTRA_CONTACT_ID, -1)
        if (contactId < 0 || !CallToken.isValid(this, intent.getStringExtra(CallActivity.EXTRA_TOKEN))) {
            finish()
            return
        }
        val notificationId = intent.getIntExtra(CallActivity.EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) NotificationManagerCompat.from(this).cancel(notificationId)
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { value ->
            WhatsAppCallKind.entries.firstOrNull { it.name == value }
        } ?: WhatsAppCallKind.VOICE
        flow.start(contactId, kind)
        setContent {
            CompanionTheme {
                val state by flow.state.collectAsStateWithLifecycle()
                val permissionName = if ((state as? CallFlowState.Permission)?.access == CallAccess.PHONE) {
                    Manifest.permission.CALL_PHONE
                } else {
                    Manifest.permission.READ_CONTACTS
                }
                val currentPermission by rememberUpdatedState(permissionName)
                val permission =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        flow.permissionReturned(granted, !granted && permanentlyDenied(currentPermission))
                    }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            flow.onForeground(
                                hasPermission(currentPermission),
                                permanentlyDenied(currentPermission)
                            )
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(state) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        flow.onForeground(hasPermission(currentPermission), permanentlyDenied(currentPermission))
                    }
                    if (state == CallFlowState.HandedOff) finish()
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            if (kind == WhatsAppCallKind.VIDEO) "WhatsApp video call" else "WhatsApp call",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        when (val current = state) {
                            CallFlowState.Loading -> Text("Checking the current WhatsApp contact action…")
                            is CallFlowState.Permission -> {
                                Text(
                                    if (current.access == CallAccess.PHONE) {
                                        "This WhatsApp version requires Android's Phone permission " +
                                            "for its direct voice and video call action."
                                    } else {
                                        "Contacts access finds WhatsApp's call action for the saved number."
                                    }
                                )
                                if (current.requesting) {
                                    Text("Waiting for your permission choice…")
                                } else {
                                    if (current.permanent) {
                                        Text(
                                            "Permission is off. Enable it in app settings, " +
                                                "or open the chat instead."
                                        )
                                        Button(onClick = ::openSettings) { Text("Open app settings") }
                                    } else {
                                        Button(onClick = {
                                            if (flow.permissionRequested()) {
                                                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                                    .putBoolean("requested_$currentPermission", true).apply()
                                                permission.launch(currentPermission)
                                            }
                                        }) {
                                            Text(
                                                if (current.access == CallAccess.PHONE) {
                                                    "Allow phone calls"
                                                } else {
                                                    "Allow contacts access"
                                                }
                                            )
                                        }
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
                                if (!hasPermission(currentPermission)) {
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

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun permanentlyDenied(permission: String): Boolean {
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val legacyRequested = permission == Manifest.permission.READ_CONTACTS &&
            preferences.getBoolean(REQUESTED, false)
        return !hasPermission(permission) &&
            (preferences.getBoolean("requested_$permission", false) || legacyRequested) &&
            !shouldShowRequestPermissionRationale(permission)
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    companion object {
        const val EXTRA_KIND = "call_kind"
        private const val PREFS = "call_contacts_permission"
        private const val REQUESTED = "requested"

        fun intent(
            context: Context,
            contactId: Long,
            notificationId: Int? = null,
            kind: WhatsAppCallKind = WhatsAppCallKind.VOICE,
        ): Intent =
            Intent(context, CallFlowActivity::class.java)
                .putExtra(CallActivity.EXTRA_CONTACT_ID, contactId)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(CallActivity.EXTRA_TOKEN, CallToken.get(context))
                .apply { if (notificationId != null) putExtra(CallActivity.EXTRA_NOTIFICATION_ID, notificationId) }
    }
}
