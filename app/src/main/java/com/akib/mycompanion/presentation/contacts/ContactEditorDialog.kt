package com.akib.mycompanion.presentation.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.presentation.common.OutlinedStateTextField

@Composable
fun ContactEditorDialog(
    editor: ContactEditor,
    name: TextFieldState,
    phone: TextFieldState,
    onNameEdited: () -> Unit,
    onPhoneEdited: () -> Unit,
    onPickFromPhonebook: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.title) },
        text = {
            ContactEditorForm(editor, name, phone, onPickFromPhonebook, onNameEdited, onPhoneEdited)
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = editor.canSave(name.text, phone.text)) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The dialog's fields, separate so they can be tested without a dialog window. The text is typed straight
 * into [name] and [phone]; [onNameEdited] / [onPhoneEdited] only clear errors.
 */
@Composable
fun ContactEditorForm(
    editor: ContactEditor,
    name: TextFieldState,
    phone: TextFieldState,
    onPickFromPhonebook: () -> Unit,
    onNameEdited: () -> Unit = {},
    onPhoneEdited: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onPickFromPhonebook, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Choose from phone contacts")
        }
        OutlinedStateTextField(
            state = name,
            label = "Name",
            isError = editor.nameError != null,
            supportingText = editor.nameError,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            onUserEdit = onNameEdited,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedStateTextField(
            state = phone,
            label = "WhatsApp number",
            placeholder = "+8801711123456",
            isError = editor.phoneError != null,
            supportingText = editor.phoneError ?: "Local numbers starting with 0 get +880 added",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            ),
            onUserEdit = onPhoneEdited,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
