package com.akib.mycompanion.domain.usecase

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.repository.ContactRepository
import javax.inject.Inject

sealed interface CallResult {
    /** The WhatsApp voice call screen was launched. */
    data object CallStarted : CallResult

    /** No direct call entry (number not in phone contacts, or no contacts access): chat opened instead. */
    data object ChatOpened : CallResult

    data object WhatsAppNotInstalled : CallResult

    data object ContactNotFound : CallResult
}

/**
 * One tap → WhatsApp voice call.
 *
 * WhatsApp has no public "call this number" API, so the direct call works through the entry
 * WhatsApp adds to the phone's contacts. When there is none, the chat is the closest thing.
 */
class CallContactUseCase @Inject constructor(
    private val contacts: ContactRepository,
    private val whatsApp: WhatsAppGateway,
) {
    suspend operator fun invoke(contactId: Long): CallResult {
        val contact = contacts.get(contactId) ?: return CallResult.ContactNotFound
        return invoke(contact)
    }

    operator fun invoke(contact: Contact): CallResult {
        if (!whatsApp.isInstalled()) return CallResult.WhatsAppNotInstalled
        val target = whatsApp.findVoiceCallTarget(contact.digits)
        if (target != null && whatsApp.startVoiceCall(target)) return CallResult.CallStarted
        return if (whatsApp.openChat(contact.digits)) CallResult.ChatOpened else CallResult.WhatsAppNotInstalled
    }
}
