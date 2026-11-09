package com.akib.mycompanion.domain.usecase

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppCallKind
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.port.WhatsAppLaunchResult
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import javax.inject.Inject

sealed interface CallPreparation {
    data class Ready(val variant: WhatsAppVariant) : CallPreparation
    data object PermissionRequired : CallPreparation
    data object CallPermissionRequired : CallPreparation
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
    suspend fun prepare(contactId: Long, kind: WhatsAppCallKind = WhatsAppCallKind.VOICE): CallPreparation {
        val contact = contacts.get(contactId) ?: return CallPreparation.ContactNotFound
        if (whatsApp.installedVariants().isEmpty()) return CallPreparation.AppMissing
        val number = WhatsAppNumberMatcher.normalize(contact.phone)
            ?: return CallPreparation.Unavailable(WhatsAppCallFailure.INVALID_NUMBER)
        return when (val result = whatsApp.resolveCall("+$number", kind)) {
            is VoiceCallLookup.Unavailable -> if (result.reason == WhatsAppCallFailure.PERMISSION_REQUIRED) {
                CallPreparation.PermissionRequired
            } else {
                CallPreparation.Unavailable(result.reason)
            }
            is VoiceCallLookup.Found -> {
                if (result.targets.any { it.kind == kind && whatsApp.needsCallPermission(it) }) {
                    return CallPreparation.CallPermissionRequired
                }
                val variants = result.targets.filter { it.kind == kind }.map { it.variant }.distinct()
                when (variants.size) {
                    0 -> CallPreparation.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW)
                    1 -> CallPreparation.Ready(variants.single())
                    else -> CallPreparation.ChooseVariant(variants)
                }
            }
        }
    }

    suspend operator fun invoke(contactId: Long, kind: WhatsAppCallKind = WhatsAppCallKind.VOICE): CallResult =
        contacts.get(contactId)?.let { invoke(it, kind = kind) } ?: CallResult.ContactNotFound

    suspend operator fun invoke(
        contactId: Long,
        variant: WhatsAppVariant,
        kind: WhatsAppCallKind = WhatsAppCallKind.VOICE,
    ): CallResult =
        contacts.get(contactId)?.let { invoke(it, variant, kind) } ?: CallResult.ContactNotFound

    suspend operator fun invoke(
        contact: Contact,
        variant: WhatsAppVariant? = null,
        kind: WhatsAppCallKind = WhatsAppCallKind.VOICE,
    ): CallResult {
        val installed = whatsApp.installedVariants()
        if (installed.isEmpty()) return CallResult.WhatsAppNotInstalled
        if (variant != null && variant !in installed) return CallResult.Failed(WhatsAppCallFailure.APP_NOT_INSTALLED)
        val number = WhatsAppNumberMatcher.normalize(contact.phone)
            ?: return CallResult.Failed(WhatsAppCallFailure.INVALID_NUMBER)
        val lookup = whatsApp.resolveCall("+$number", kind)
        if (lookup is VoiceCallLookup.Found) {
            val candidates = lookup.targets.filter {
                it.kind == kind && (variant == null || it.variant == variant)
            }
            val variants = candidates.map { it.variant }.distinct()
            if (variants.size > 1) return CallResult.ChooseVariant(variants)
            if (candidates.size > 1) return fallback(contact, WhatsAppCallFailure.AMBIGUOUS, variant)
            val target = candidates.singleOrNull()
            if (target != null) {
                return when (val launch = whatsApp.launchCall(target)) {
                    WhatsAppLaunchResult.HandedOff -> CallResult.CallStarted
                    is WhatsAppLaunchResult.Failed -> {
                        if (launch.reason == WhatsAppCallFailure.CALL_PERMISSION_REQUIRED) {
                            CallResult.Failed(launch.reason)
                        } else {
                            fallback(contact, launch.reason, target.variant)
                        }
                    }
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
