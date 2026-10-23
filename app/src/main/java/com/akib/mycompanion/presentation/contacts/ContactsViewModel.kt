package com.akib.mycompanion.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallResult
import com.akib.mycompanion.domain.usecase.SaveContactResult
import com.akib.mycompanion.domain.usecase.SaveContactUseCase
import com.akib.mycompanion.presentation.shortcuts.HomeShortcuts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The add/edit person dialog. [id] is null for a new person. */
data class ContactEditor(
    val id: Long? = null,
    val name: String = "",
    val phone: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = name.isNotBlank() && phone.isNotBlank()
}

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val loading: Boolean = true,
    val editor: ContactEditor? = null,
    val canPinShortcuts: Boolean = false,
)

sealed interface ContactsEvent {
    data class Message(val text: String) : ContactsEvent

    /** Shown as a snackbar with Undo. */
    data class Removed(val contact: Contact) : ContactsEvent
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactRepository,
    private val saveContact: SaveContactUseCase,
    private val callContact: CallContactUseCase,
    private val phonebook: PhonebookSource,
    private val shortcuts: HomeShortcuts,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val editor = MutableStateFlow<ContactEditor?>(null)
    private val eventChannel = Channel<ContactsEvent>(Channel.BUFFERED)
    val events: Flow<ContactsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<ContactsUiState> =
        combine(repository.observeAll(), editor) { contacts, editor ->
            ContactsUiState(
                contacts = contacts,
                loading = false,
                editor = editor,
                canPinShortcuts = shortcuts.isSupported,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ContactsUiState(canPinShortcuts = shortcuts.isSupported),
        )

    fun onAddClick() {
        editor.value = ContactEditor()
    }

    fun onEditClick(contact: Contact) {
        editor.value = ContactEditor(id = contact.id, name = contact.name, phone = contact.phone)
    }

    fun onNameChange(name: String) = editor.update { it?.copy(name = name, nameError = null) }

    fun onPhoneChange(phone: String) = editor.update { it?.copy(phone = phone, phoneError = null) }

    fun onDismissEditor() {
        editor.value = null
    }

    fun onSaveEditor() {
        val current = editor.value ?: return
        viewModelScope.launch {
            when (val result = saveContact(current.id, current.name, current.phone)) {
                is SaveContactResult.Saved -> {
                    editor.value = null
                    if (!current.isNew) shortcuts.update(result.contact)
                }
                SaveContactResult.MissingName ->
                    editor.update { it?.copy(nameError = "Enter a name") }
                SaveContactResult.InvalidPhone ->
                    editor.update { it?.copy(phoneError = "Enter a valid number, e.g. +8801711123456") }
                is SaveContactResult.DuplicatePhone ->
                    editor.update { it?.copy(phoneError = "Already saved as ${result.existing.name}") }
            }
        }
    }

    /** Result of the system contact picker (a content:// uri). Fills the dialog. */
    fun onPhonebookPicked(uri: String) {
        viewModelScope.launch {
            val picked = phonebook.read(uri)
            if (picked == null) {
                eventChannel.send(ContactsEvent.Message("Couldn't read that contact"))
                return@launch
            }
            editor.update { current ->
                val base = current ?: ContactEditor()
                base.copy(
                    name = base.name.ifBlank { picked.name },
                    phone = picked.number,
                    nameError = null,
                    phoneError = null,
                )
            }
        }
    }

    fun onCall(contact: Contact) {
        viewModelScope.launch {
            val result: CallResult = withContext(io) { callContact(contact) }
            CallMessages.forResult(result)?.let { eventChannel.send(ContactsEvent.Message(it)) }
        }
    }

    fun onDelete(contact: Contact) {
        viewModelScope.launch {
            repository.delete(contact.id)
            eventChannel.send(ContactsEvent.Removed(contact))
        }
    }

    fun onUndoDelete(contact: Contact) {
        viewModelScope.launch { repository.save(contact) }
    }

    fun onPinShortcut(contact: Contact) {
        viewModelScope.launch {
            val requested = shortcuts.pin(contact)
            val text = if (requested) {
                "Confirm to add ${contact.name} to your home screen"
            } else {
                "Your launcher doesn't support home-screen shortcuts"
            }
            eventChannel.send(ContactsEvent.Message(text))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

object CallMessages {
    /** What to tell the user after a call attempt; null when the call simply started. */
    fun forResult(result: CallResult): String? = when (result) {
        CallResult.CallStarted -> null
        CallResult.ChatOpened ->
            "Opened the WhatsApp chat. For one-tap calls, save this number in your phone contacts " +
                "and allow contacts access."
        CallResult.WhatsAppNotInstalled -> "WhatsApp isn't installed"
        CallResult.ContactNotFound -> "This person was removed from My Companion"
    }
}
