package com.akib.mycompanion.presentation.contacts

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppCallKind
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.usecase.CallContactUseCase
import com.akib.mycompanion.domain.usecase.CallPreparation
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

/**
 * The add/edit person dialog. [id] is null for a new person. The typed name and number aren't here: they
 * live in [ContactsViewModel.name] and [ContactsViewModel.phone] (Compose text state, device finding #5).
 */
data class ContactEditor(
    val id: Long? = null,
    val nameError: String? = null,
    val phoneError: String? = null,
) {
    val isNew: Boolean get() = id == null
    val title: String get() = if (isNew) "Add person" else "Edit person"

    fun canSave(name: CharSequence, phone: CharSequence): Boolean = name.isNotBlank() && phone.isNotBlank()
}

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val loading: Boolean = true,
    val editor: ContactEditor? = null,
    val canPinShortcuts: Boolean = false,
)

sealed interface ContactsEvent {
    data class Message(val text: String) : ContactsEvent

    /** Continue in a visible permission/variant-choice host; only the app contact ID crosses the boundary. */
    data class CallRecovery(val contactId: Long, val kind: WhatsAppCallKind = WhatsAppCallKind.VOICE) : ContactsEvent

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

    /** What's typed in the dialog. The keyboard writes here directly; nothing round-trips through a flow. */
    val name = TextFieldState()
    val phone = TextFieldState()

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
        name.clearText()
        phone.clearText()
        editor.value = ContactEditor()
    }

    fun onEditClick(contact: Contact) {
        name.setTextAndPlaceCursorAtEnd(contact.name)
        phone.setTextAndPlaceCursorAtEnd(contact.phone)
        editor.value = ContactEditor(id = contact.id)
    }

    /** The user changed the name: its error no longer applies. */
    fun onNameEdited() = editor.update { it?.copy(nameError = null) }

    /** The user changed the number: its error no longer applies. */
    fun onPhoneEdited() = editor.update { it?.copy(phoneError = null) }

    fun onDismissEditor() {
        editor.value = null
    }

    fun onSaveEditor() {
        val current = editor.value ?: return
        val nameText = name.text.toString()
        val phoneText = phone.text.toString()
        viewModelScope.launch {
            when (val result = saveContact(current.id, nameText, phoneText)) {
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
            if (name.text.isBlank()) name.setTextAndPlaceCursorAtEnd(picked.name)
            phone.setTextAndPlaceCursorAtEnd(picked.number)
            editor.update { current -> (current ?: ContactEditor()).copy(nameError = null, phoneError = null) }
        }
    }

    fun onVideoCall(contact: Contact) = onCall(contact, WhatsAppCallKind.VIDEO)

    fun onCall(contact: Contact, kind: WhatsAppCallKind = WhatsAppCallKind.VOICE) {
        viewModelScope.launch {
            val preparation = withContext(io) { callContact.prepare(contact.id, kind) }
            if (preparation is CallPreparation.PermissionRequired ||
                preparation is CallPreparation.CallPermissionRequired ||
                preparation is CallPreparation.ChooseVariant
            ) {
                eventChannel.send(ContactsEvent.CallRecovery(contact.id, kind))
                return@launch
            }
            // The gateway suspends provider queries on IO and hands off on this foreground/Main context.
            val result = callContact(contact.id, kind)
            val permissionNeeded = result is CallResult.Failed && (
                result.reason == WhatsAppCallFailure.PERMISSION_REQUIRED ||
                    result.reason == WhatsAppCallFailure.CALL_PERMISSION_REQUIRED
                )
            if (result is CallResult.ChooseVariant || permissionNeeded) {
                eventChannel.send(ContactsEvent.CallRecovery(contact.id, kind))
            } else {
                CallMessages.forResult(result)?.let { eventChannel.send(ContactsEvent.Message(it)) }
            }
        }
    }

    fun onDelete(contact: Contact) {
        viewModelScope.launch {
            repository.delete(contact.id)
            shortcuts.disable(contact.id)
            eventChannel.send(ContactsEvent.Removed(contact))
        }
    }

    fun onUndoDelete(contact: Contact) {
        viewModelScope.launch {
            shortcuts.enable(repository.save(contact))
        }
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
    /** A successful activity handoff establishes neither ringing nor a connected call. */
    fun forResult(result: CallResult): String? = when (result) {
        CallResult.CallStarted -> null
        CallResult.ChatOpened ->
            "Opened the WhatsApp chat. " +
                "Direct calling needs a matching WhatsApp phone-contact action."
        CallResult.WhatsAppNotInstalled -> "WhatsApp isn't installed"
        CallResult.ContactNotFound -> "This person was removed from My Companion"
        is CallResult.ChooseVariant -> "Choose WhatsApp or WhatsApp Business for this attempt."
        is CallResult.ChatFallback -> "Opened ${result.variant.title} chat instead. " + forFailure(result.reason)
        is CallResult.Failed -> forFailure(result.reason)
    }

    fun forFailure(reason: WhatsAppCallFailure): String = when (reason) {
        WhatsAppCallFailure.CALL_PERMISSION_REQUIRED ->
            "Phone permission is needed to open WhatsApp's direct call action. Allow it to try again."
        WhatsAppCallFailure.PERMISSION_REQUIRED ->
            "Contacts access is needed to find WhatsApp's call action. Allow it in app settings to try direct calling."
        WhatsAppCallFailure.PROVIDER_DENIED ->
            "The phone's contacts provider denied access. Check contacts permission in app settings."
        WhatsAppCallFailure.PROVIDER_ERROR -> "Couldn't read the phone contacts. Try again later."
        WhatsAppCallFailure.NO_PHONEBOOK_MATCH ->
            "This number wasn't matched in the phone book. Save it there and let WhatsApp sync its contact action."
        WhatsAppCallFailure.NO_VOICE_ROW ->
            "No WhatsApp call action was found for this number. Check its action in the phone's Contacts app."
        WhatsAppCallFailure.AMBIGUOUS ->
            "More than one contact action matched. " +
                "No direct call was started; check the saved number and phone contacts."
        WhatsAppCallFailure.INVALID_NUMBER -> "Check this person's saved phone number before trying again."
        WhatsAppCallFailure.NO_HANDLER ->
            "The selected WhatsApp app couldn't open that action. Check that app or update it."
        WhatsAppCallFailure.LAUNCH_DENIED ->
            "Android or the selected WhatsApp app rejected the action. Try from WhatsApp."
        WhatsAppCallFailure.STALE_TARGET ->
            "The WhatsApp contact action changed. Try again to look up its current action."
        WhatsAppCallFailure.APP_NOT_INSTALLED -> "The selected WhatsApp app is no longer installed."
    }
}
