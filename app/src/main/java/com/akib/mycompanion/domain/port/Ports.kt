package com.akib.mycompanion.domain.port

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder

/** Identity is explicit: Business and personal are never interchanged after a chosen handoff fails. */
enum class WhatsAppVariant(val packageName: String, val title: String) {
    STANDARD("com.whatsapp", "WhatsApp"),
    BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
}

enum class WhatsAppCallKind { VOICE, VIDEO }

enum class CallAccess { CONTACTS, PHONE }

enum class WhatsAppCallFailure {
    CALL_PERMISSION_REQUIRED,
    PERMISSION_REQUIRED,
    PROVIDER_DENIED,
    PROVIDER_ERROR,
    NO_PHONEBOOK_MATCH,
    NO_VOICE_ROW,
    AMBIGUOUS,
    INVALID_NUMBER,
    NO_HANDLER,
    LAUNCH_DENIED,
    STALE_TARGET,
    APP_NOT_INSTALLED,
}

sealed interface VoiceCallLookup {
    data class Found(val targets: List<VoiceCallTarget>) : VoiceCallLookup
    data class Unavailable(val reason: WhatsAppCallFailure) : VoiceCallLookup
}

sealed interface WhatsAppLaunchResult {
    data object HandedOff : WhatsAppLaunchResult
    data class Failed(val reason: WhatsAppCallFailure) : WhatsAppLaunchResult
}

/** Diagnostic observations deliberately exclude numbers, contact/account names and provider IDs. */
data class VoiceCallObservation(
    val accountType: String,
    val mimeType: String,
    val variant: WhatsAppVariant?,
    val numberMatches: Boolean,
    val handlerAvailable: Boolean,
)

data class VoiceCallInspection(
    val result: VoiceCallLookup,
    val phonebookMatches: Int = 0,
    val candidateRows: Int = 0,
    val observations: List<VoiceCallObservation> = emptyList(),
)

/** Provider lookup suspends on IO; launching resumes in the foreground caller's coroutine context. */
interface WhatsAppGateway {
    fun isInstalled(): Boolean
    fun findVoiceCallTarget(digits: String): VoiceCallTarget?
    fun startVoiceCall(target: VoiceCallTarget): Boolean
    fun openChat(digits: String): Boolean

    fun installedVariants(): List<WhatsAppVariant> =
        if (isInstalled()) listOf(WhatsAppVariant.STANDARD) else emptyList()

    suspend fun resolveVoiceCall(digits: String): VoiceCallLookup = findVoiceCallTarget(
        com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher.normalize(digits).orEmpty(),
    )?.let {
        VoiceCallLookup.Found(listOf(it))
    } ?: VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW)

    suspend fun resolveCall(digits: String, kind: WhatsAppCallKind): VoiceCallLookup =
        if (kind == WhatsAppCallKind.VOICE) {
            resolveVoiceCall(digits)
        } else {
            VoiceCallLookup.Unavailable(WhatsAppCallFailure.NO_VOICE_ROW)
        }

    suspend fun launchCall(target: VoiceCallTarget): WhatsAppLaunchResult = launchVoiceCall(target)

    suspend fun launchVoiceCall(target: VoiceCallTarget): WhatsAppLaunchResult =
        if (startVoiceCall(target)) {
            WhatsAppLaunchResult.HandedOff
        } else {
            WhatsAppLaunchResult.Failed(WhatsAppCallFailure.NO_HANDLER)
        }

    fun launchChat(digits: String, variant: WhatsAppVariant): WhatsAppLaunchResult =
        if (openChat(digits)) {
            WhatsAppLaunchResult.HandedOff
        } else {
            WhatsAppLaunchResult.Failed(WhatsAppCallFailure.NO_HANDLER)
        }

    fun needsCallPermission(target: VoiceCallTarget): Boolean = false

    fun canHandle(target: VoiceCallTarget): Boolean = false
}

/** Ephemeral lookup result. Never persist this row ID in Room or shortcut extras. */
data class VoiceCallTarget(
    val dataId: Long,
    val mimeType: String,
    val variant: WhatsAppVariant = WhatsAppVariant.STANDARD,
    val accountType: String = "com.whatsapp",
    val normalizedNumber: String = "",
    val kind: WhatsAppCallKind = WhatsAppCallKind.VOICE,
) {
    val packageName: String get() = variant.packageName
}

/** Wakes the app up at a reminder's trigger time. */
interface AlarmScheduler {
    fun schedule(reminder: Reminder)

    fun cancel(reminderId: Long)
}

/** Shows / removes the notification for a reminder. */
interface ReminderNotifier {
    /** Returns false when notifications are blocked. */
    fun show(reminder: Reminder, contact: Contact?): Boolean

    fun cancel(notificationId: Int)
}

/** Reads a number the user picked in the system contacts picker. */
interface PhonebookSource {
    suspend fun read(uri: String): PickedPhone?
}

data class PickedPhone(val name: String, val number: String)
