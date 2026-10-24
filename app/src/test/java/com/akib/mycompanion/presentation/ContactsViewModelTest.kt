package com.akib.mycompanion.presentation

import app.cash.turbine.test
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.PickedPhone
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.domain.usecase.SaveContactUseCase
import com.akib.mycompanion.fakes.FakeContactRepository
import com.akib.mycompanion.fakes.FakeHomeShortcuts
import com.akib.mycompanion.fakes.FakePhonebook
import com.akib.mycompanion.fakes.FakeWhatsAppGateway
import com.akib.mycompanion.fakes.MainDispatcherRule
import com.akib.mycompanion.presentation.contacts.CallMessages
import com.akib.mycompanion.presentation.contacts.ContactsEvent
import com.akib.mycompanion.presentation.contacts.ContactsViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ContactsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val mum = Contact(id = 1, name = "Mum", phone = "+8801711123456")
    private val repository = FakeContactRepository(listOf(mum))
    private val whatsApp = FakeWhatsAppGateway()
    private val phonebook = FakePhonebook()
    private val shortcuts = FakeHomeShortcuts()

    // Lazy: viewModelScope needs the test Main dispatcher, which the rule installs after field init.
    private val viewModel by lazy {
        ContactsViewModel(
            repository = repository,
            saveContact = SaveContactUseCase(repository),
            callContact = CallContactUseCase(repository, whatsApp),
            phonebook = phonebook,
            shortcuts = shortcuts,
            io = mainDispatcher.dispatcher,
        )
    }

    @Test
    fun `state lists saved people`() = runTest {
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.loading).isFalse()
            assertThat(state.contacts).containsExactly(mum)
            assertThat(state.canPinShortcuts).isTrue()
        }
    }

    @Test
    fun `adding a person normalises the number and closes the dialog`() = runTest {
        viewModel.uiState.test {
            viewModel.onAddClick()
            assertThat(expectMostRecentItem().editor!!.isNew).isTrue()

            viewModel.onNameChange("Akib")
            viewModel.onPhoneChange("01811-123456")
            viewModel.onSaveEditor()

            val state = expectMostRecentItem()
            assertThat(state.editor).isNull()
            assertThat(state.contacts.map { it.name to it.phone })
                .containsExactly("Akib" to "+8801811123456", "Mum" to "+8801711123456")
                .inOrder()
        }
    }

    @Test
    fun `an invalid number keeps the dialog open with an error`() = runTest {
        viewModel.uiState.test {
            viewModel.onAddClick()
            viewModel.onNameChange("Akib")
            viewModel.onPhoneChange("12")
            viewModel.onSaveEditor()

            val editor = expectMostRecentItem().editor!!
            assertThat(editor.phoneError).contains("valid number")

            viewModel.onPhoneChange("01811123456")
            assertThat(expectMostRecentItem().editor!!.phoneError).isNull()
        }
    }

    @Test
    fun `a duplicate number names who already has it`() = runTest {
        viewModel.uiState.test {
            viewModel.onAddClick()
            viewModel.onNameChange("Someone")
            viewModel.onPhoneChange("01711123456")
            viewModel.onSaveEditor()

            assertThat(expectMostRecentItem().editor!!.phoneError).isEqualTo("Already saved as Mum")
        }
    }

    @Test
    fun `editing updates the person and their home-screen shortcut`() = runTest {
        viewModel.uiState.test {
            viewModel.onEditClick(mum)
            assertThat(expectMostRecentItem().editor!!.isNew).isFalse()

            viewModel.onNameChange("Mom")
            viewModel.onSaveEditor()

            assertThat(expectMostRecentItem().contacts).containsExactly(mum.copy(name = "Mom"))
            assertThat(shortcuts.updated).containsExactly(mum.copy(name = "Mom"))
        }
    }

    @Test
    fun `picking from the phone book fills the number and an empty name`() = runTest {
        phonebook.result = PickedPhone(name = "Dad", number = "+880 1911 123456")

        viewModel.uiState.test {
            viewModel.onAddClick()
            viewModel.onPhonebookPicked("content://contacts/1")

            val editor = expectMostRecentItem().editor!!
            assertThat(editor.name).isEqualTo("Dad")
            assertThat(editor.phone).isEqualTo("+880 1911 123456")

            viewModel.onNameChange("Abba")
            viewModel.onPhonebookPicked("content://contacts/1")
            assertThat(expectMostRecentItem().editor!!.name).isEqualTo("Abba")
        }
    }

    @Test
    fun `an unreadable phone book entry shows a message`() = runTest {
        viewModel.events.test {
            viewModel.onPhonebookPicked("content://contacts/404")
            assertThat(awaitItem()).isEqualTo(ContactsEvent.Message("Couldn't read that contact"))
        }
    }

    @Test
    fun `a direct call shows no message`() = runTest {
        whatsApp.target = VoiceCallTarget(42, "mime")

        viewModel.events.test {
            viewModel.onCall(mum)
            expectNoEvents()
        }
        assertThat(whatsApp.calls).hasSize(1)
    }

    @Test
    fun `falling back to the chat explains why`() = runTest {
        whatsApp.target = null

        viewModel.events.test {
            viewModel.onCall(mum)
            assertThat(awaitItem()).isEqualTo(ContactsEvent.Message(CallMessages.forResult(CallResult.ChatOpened)!!))
        }
        assertThat(whatsApp.chats).containsExactly("8801711123456")
    }

    @Test
    fun `removing can be undone with the same id`() = runTest {
        viewModel.events.test {
            viewModel.onDelete(mum)
            assertThat(awaitItem()).isEqualTo(ContactsEvent.Removed(mum))
        }
        assertThat(repository.all).isEmpty()

        viewModel.onUndoDelete(mum)
        assertThat(repository.all).containsExactly(mum)
    }

    @Test
    fun `pinning a shortcut asks the launcher`() = runTest {
        viewModel.events.test {
            viewModel.onPinShortcut(mum)
            assertThat((awaitItem() as ContactsEvent.Message).text).contains("home screen")
        }
        assertThat(shortcuts.pinned).containsExactly(mum)
    }

    @Test
    fun `pinning explains when the launcher can't`() = runTest {
        shortcuts.isSupported = false

        viewModel.events.test {
            viewModel.onPinShortcut(mum)
            assertThat((awaitItem() as ContactsEvent.Message).text).contains("doesn't support")
        }
    }

    @Test
    fun `call messages cover every outcome`() {
        assertThat(CallMessages.forResult(CallResult.CallStarted)).isNull()
        assertThat(CallMessages.forResult(CallResult.WhatsAppNotInstalled)).contains("isn't installed")
        assertThat(CallMessages.forResult(CallResult.ContactNotFound)).contains("removed")
    }
}
