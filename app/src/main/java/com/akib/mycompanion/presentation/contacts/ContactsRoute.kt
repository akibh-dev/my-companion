package com.akib.mycompanion.presentation.contacts

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.domain.model.Contact
import kotlinx.coroutines.launch

/** Connects [ContactsScreen] to its ViewModel and to Android (permissions, contact picker, snackbars). */
@Composable
fun ContactsRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            scope.launch {
                when (event) {
                    is ContactsEvent.Message ->
                        snackbarHostState.showSnackbar(event.text, duration = SnackbarDuration.Long)
                    is ContactsEvent.Removed -> {
                        val result = snackbarHostState.showSnackbar(
                            message = "Removed ${event.contact.name}",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(event.contact)
                    }
                }
            }
        }
    }

    // Contacts access lets WhatsApp calls start directly. Asked the first time the user taps Call.
    var pendingCall by remember { mutableStateOf<Contact?>(null) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingCall?.let(viewModel::onCall)
        pendingCall = null
    }
    val pickPhone = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri != null) viewModel.onPhonebookPicked(uri)
    }

    ContactsScreen(
        state = state,
        onAddClick = viewModel::onAddClick,
        onCall = { contact ->
            if (hasContactsPermission(context)) {
                viewModel.onCall(contact)
            } else {
                pendingCall = contact
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        },
        onEdit = viewModel::onEditClick,
        onDelete = viewModel::onDelete,
        onPinShortcut = viewModel::onPinShortcut,
        modifier = modifier,
    )

    state.editor?.let { editor ->
        ContactEditorDialog(
            editor = editor,
            onNameChange = viewModel::onNameChange,
            onPhoneChange = viewModel::onPhoneChange,
            onPickFromPhonebook = {
                try {
                    pickPhone.launch(Unit)
                } catch (_: ActivityNotFoundException) {
                    scope.launch { snackbarHostState.showSnackbar("No contacts app found") }
                }
            },
            onSave = viewModel::onSaveEditor,
            onDismiss = viewModel::onDismissEditor,
        )
    }
}

private fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/** Opens the system picker for a single phone number; returns the picked row's uri. */
class PickPhoneNumber : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.data?.toString() else null
}
