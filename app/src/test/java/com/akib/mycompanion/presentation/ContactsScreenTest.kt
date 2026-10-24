package com.akib.mycompanion.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.presentation.contacts.ContactEditor
import com.akib.mycompanion.presentation.contacts.ContactEditorForm
import com.akib.mycompanion.presentation.contacts.ContactsScreen
import com.akib.mycompanion.presentation.contacts.ContactsUiState
import com.akib.mycompanion.ui.theme.CompanionTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val mum = Contact(id = 1, name = "Mum", phone = "+8801711123456")
    private val events = mutableListOf<String>()

    private fun show(state: ContactsUiState) = compose.setContent {
        CompanionTheme {
            ContactsScreen(
                state = state,
                onAddClick = { events += "add" },
                onCall = { events += "call:${it.name}" },
                onEdit = { events += "edit:${it.name}" },
                onDelete = { events += "delete:${it.name}" },
                onPinShortcut = { events += "pin:${it.name}" },
            )
        }
    }

    @Test
    fun `empty list invites adding someone`() {
        show(ContactsUiState(contacts = emptyList(), loading = false))

        compose.onNodeWithText("No one here yet").assertIsDisplayed()
        compose.onNodeWithText("Add person").performClick()

        assertThat(events).containsExactly("add")
    }

    @Test
    fun `each person has a big call button`() {
        show(ContactsUiState(contacts = listOf(mum), loading = false))

        compose.onNodeWithText("Mum").assertIsDisplayed()
        compose.onNodeWithText("+8801711123456").assertIsDisplayed()
        compose.onNodeWithContentDescription("Call Mum on WhatsApp").performClick()

        assertThat(events).containsExactly("call:Mum")
    }

    @Test
    fun `the menu offers edit, home screen and remove`() {
        show(ContactsUiState(contacts = listOf(mum), loading = false, canPinShortcuts = true))

        compose.onNodeWithContentDescription("More options for Mum").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithContentDescription("More options for Mum").performClick()
        compose.onNodeWithText("Add to home screen").performClick()
        compose.onNodeWithContentDescription("More options for Mum").performClick()
        compose.onNodeWithText("Remove").performClick()

        assertThat(events).containsExactly("edit:Mum", "pin:Mum", "delete:Mum").inOrder()
    }

    @Test
    fun `home screen option is hidden when the launcher can't pin`() {
        show(ContactsUiState(contacts = listOf(mum), loading = false, canPinShortcuts = false))

        compose.onNodeWithContentDescription("More options for Mum").performClick()

        compose.onNodeWithText("Add to home screen").assertDoesNotExist()
    }

    @Test
    fun `editor form takes typing, picks from the phone book and shows errors`() {
        var editor by mutableStateOf(ContactEditor())
        compose.setContent {
            CompanionTheme {
                ContactEditorForm(
                    editor = editor,
                    onNameChange = { editor = editor.copy(name = it) },
                    onPhoneChange = { editor = editor.copy(phone = it) },
                    onPickFromPhonebook = { events += "pick" },
                )
            }
        }

        compose.onNodeWithText("Name").performTextInput("Akib")
        compose.onNodeWithText("WhatsApp number").performTextInput("01811123456")
        compose.onNodeWithText("Choose from phone contacts").performClick()
        compose.onNodeWithText("Local numbers starting with 0 get +880 added").assertIsDisplayed()

        editor = editor.copy(phoneError = "Already saved as Mum")
        compose.onNodeWithText("Already saved as Mum").assertIsDisplayed()

        assertThat(editor.name).isEqualTo("Akib")
        assertThat(editor.phone).isEqualTo("01811123456")
        assertThat(events).containsExactly("pick")
    }

    @Test
    fun `editor can only save with both fields and is titled by mode`() {
        assertThat(ContactEditor().canSave).isFalse()
        assertThat(ContactEditor(name = "Akib").canSave).isFalse()
        assertThat(ContactEditor(name = "Akib", phone = "0181").canSave).isTrue()
        assertThat(ContactEditor().title).isEqualTo("Add person")
        assertThat(ContactEditor(id = 1).title).isEqualTo("Edit person")
    }
}
