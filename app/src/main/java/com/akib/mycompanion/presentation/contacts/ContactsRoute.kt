package com.akib.mycompanion.presentation.contacts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akib.mycompanion.presentation.call.CallFlowActivity
import com.akib.mycompanion.presentation.common.ObserveAsEvents
import kotlinx.coroutines.launch

/** Connects [ContactsScreen] to its ViewModel and to Android (permissions, contact picker, snackbars). */
@Composable
fun ContactsRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    // Typed text isn't in this state (it's the ViewModel's TextFieldStates), so the usual collection is fine.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ContactsEvent.CallRecovery -> context.startActivity(CallFlowActivity.intent(context, event.contactId))
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

    // Permission and account choice use the same saved-state host as reminder and shortcut actions.
    val pickPhone = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri != null) viewModel.onPhonebookPicked(uri)
    }

    ContactsScreen(
        state = state,
        onAddClick = viewModel::onAddClick,
        onCall = viewModel::onCall,
        onEdit = viewModel::onEditClick,
        onDelete = viewModel::onDelete,
        onPinShortcut = viewModel::onPinShortcut,
        modifier = modifier,
    )

    state.editor?.let { editor ->
        ContactEditorDialog(
            editor = editor,
            name = viewModel.name,
            phone = viewModel.phone,
            onNameEdited = viewModel::onNameEdited,
            onPhoneEdited = viewModel::onPhoneEdited,
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

/** Opens the system picker for a single phone number; returns the picked row's uri. */
class PickPhoneNumber : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.data?.toString() else null
}
