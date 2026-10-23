package com.akib.mycompanion.domain.port

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.Reminder

/** Talks to WhatsApp. Implemented in the data layer with intents and the Contacts provider. */
interface WhatsAppGateway {
    fun isInstalled(): Boolean

    /** The WhatsApp "voice call" entry for this number in the phone's contacts, if WhatsApp created one. */
    fun findVoiceCallTarget(digits: String): VoiceCallTarget?

    /** Starts the call. Returns false if nothing could handle it. */
    fun startVoiceCall(target: VoiceCallTarget): Boolean

    /** Opens the chat with this number (wa.me link). Returns false if nothing could handle it. */
    fun openChat(digits: String): Boolean
}

/** A row in ContactsContract.Data that WhatsApp (or WhatsApp Business) can launch as a voice call. */
data class VoiceCallTarget(val dataId: Long, val mimeType: String)

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
