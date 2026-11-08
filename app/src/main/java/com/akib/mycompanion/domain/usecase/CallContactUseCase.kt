package com.akib.mycompanion.domain.usecase

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.port.WhatsAppLaunchResult
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import javax.inject.Inject

sealed interface CallPreparation {
    data class Ready(val variant: WhatsAppVariant) : CallPreparation
    data object PermissionRequired : CallPreparation
    data class ChooseVariant(val variants: List<WhatsAppVariant>) : CallPreparation
    data class Unavailable(val reason: WhatsAppCallFailure) : CallPreparation
    data object ContactNotFound : CallPreparation
    data object AppMissing : CallPreparation
}

sealed interface CallResult {
    /** Android accepted the handoff; this is not proof of ringing or a connected call. */
    data object CallStarted : CallResult
    data object ChatOpened : CallResult
    data object WhatsAppNotInstalled : CallResult
    data object ContactNotFound : CallResult
    data class ChooseVariant(val variants: List<WhatsAppVariant>) : CallResult
    data class ChatFallback(val reason: WhatsAppCallFailure, val variant: WhatsAppVariant) : CallResult
    data class Failed(val reason: WhatsAppCallFailure) : CallResult
}

class CallContactUseCase @Inject constructor(
    private val contacts: ContactRepository,
    private val whatsApp: WhatsAppGateway,
) {
    /** Non-calling check for permission recovery/variant choice; no Android launch occurs here. */
    suspend fun prepare(contactId: Long): CallPreparation {
        val contact = contacts.get(contactId) ?: return CallPreparation.ContactNotFound
        if (whatsApp.installedVariants().isEmpty()) return CallPreparation.AppMissing
        val number = WhatsAppNumberMatcher.normalize(contact.phone)
            ?: return CallPreparation.Unavailable(WhatsAppCallFailure.INVALID_NUMBER)
        return when (val result = whatsApp.resolveVoiceCall("+$number")) {
            is VoiceCallLookup.Unavailable -> if (result.reason == WhatsAppCallFailure.PERMISSION_REQUIRED) {
                CallPreparation.PermissionRequired
            } else {
                CallPreparation.Unavailable(result.reason)
            }
            is VoiceCallLookup.Found -> {
                val variants = result.targets.map { it.variant }.distinct()
                when (variants.size) {
                    0 -> CallPreparation.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW)
                    1 -> CallPreparation.Ready(variants.single())
                    else -> CallPreparation.ChooseVariant(variants)
                }
            }
        }
    }

    suspend operator fun invoke(contactId: Long): CallResult =
        contacts.get(contactId)?.let { invoke(it) } ?: CallResult.ContactNotFound

    suspend operator fun invoke(contactId: Long, variant: WhatsAppVariant): CallResult =
        contacts.get(contactId)?.let { invoke(it, variant) } ?: CallResult.ContactNotFound

    suspend operator fun invoke(contact: Contact, variant: WhatsAppVariant? = null): CallResult {
        val installed = whatsApp.installedVariants()
        if (installed.isEmpty()) return CallResult.WhatsAppNotInstalled
        if (variant != null && variant !in installed) return CallResult.Failed(WhatsAppCallFailure.APP_NOT_INSTALLED)
        val number = WhatsAppNumberMatcher.normalize(contact.phone)
            ?: return CallResult.Failed(WhatsAppCallFailure.INVALID_NUMBER)
        val lookup = whatsApp.resolveVoiceCall("+$number")
        if (lookup is VoiceCallLookup.Found) {
            val candidates = lookup.targets.filter { variant == null || it.variant == variant }
            val variants = candidates.map { it.variant }.distinct()
            if (variants.size > 1) return CallResult.ChooseVariant(variants)
            if (candidates.size > 1) return fallback(contact, WhatsAppCallFailure.AMBIGUOUS, variant)
            val target = candidates.singleOrNull()
            if (target != null) {
                return when (val launch = whatsApp.launchVoiceCall(target)) {
                    WhatsAppLaunchResult.HandedOff -> CallResult.CallStarted
                    is WhatsAppLaunchResult.Failed -> fallback(contact, launch.reason, target.variant)
                }
            }
        }
        val reason = (lookup as? VoiceCallLookup.Unavailable)?.reason ?: WhatsAppCallFailure.NO_VOICE_ROW
        return fallback(contact, reason, variant)
    }

    suspend fun openChat(
        contactId: Long,
        reason: WhatsAppCallFailure,
        variant: WhatsAppVariant? = null,
    ): CallResult = contacts.get(contactId)?.let { fallback(it, reason, variant) } ?: CallResult.ContactNotFound

    private fun fallback(contact: Contact, reason: WhatsAppCallFailure, variant: WhatsAppVariant?): CallResult {
        val installed = whatsApp.installedVariants()
        if (installed.isEmpty()) return CallResult.WhatsAppNotInstalled
        if (variant == null && installed.size > 1) return CallResult.ChooseVariant(installed)
        val selected = variant ?: installed.single()
        if (selected !in installed) return CallResult.Failed(WhatsAppCallFailure.APP_NOT_INSTALLED)
        val digits = WhatsAppNumberMatcher.normalize(contact.phone)
            ?: return CallResult.Failed(WhatsAppCallFailure.INVALID_NUMBER)
        return when (val launch = whatsApp.launchChat(digits, selected)) {
            WhatsAppLaunchResult.HandedOff -> CallResult.ChatFallback(reason, selected)
            is WhatsAppLaunchResult.Failed -> CallResult.Failed(launch.reason)
        }
    }
}
