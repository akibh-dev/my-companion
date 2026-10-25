package com.akib.mycompanion.messaging

/** The five chat apps the plan covers (FR-1, FR-5), plus WhatsApp Business. */
enum class ChatApp(val packageName: String, val title: String) {
    WHATSAPP("com.whatsapp", "WhatsApp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
    MESSENGER("com.facebook.orca", "Messenger"),
    SIGNAL("org.thoughtcrime.securesms", "Signal"),
    TELEGRAM("org.telegram.messenger", "Telegram"),
    GOOGLE_MESSAGES("com.google.android.apps.messaging", "Messages"),
    ;

    companion object {
        fun of(packageName: String): ChatApp? = entries.firstOrNull { it.packageName == packageName }
    }
}

/** One incoming chat message, from one notification (plan Phase 2, "one message record"). */
data class IncomingMessage(
    /** The notification's key: replies go through it. */
    val key: String,
    val app: ChatApp,
    val sender: String,
    val text: String,
    /** Group name, or the sender for one-to-one chats; used for cool-downs (FR-1.5). */
    val conversation: String,
    val isGroup: Boolean,
    val postedAt: Long,
    val canReply: Boolean,
)

/**
 * A notification reduced to what the adapters need, so their parsing is plain Kotlin and can be
 * tested on saved samples. [messages] come from MessagingStyle; a null sender means "you".
 */
data class NotificationParts(
    val packageName: String,
    val key: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val messages: List<Pair<String?, String>>,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val category: String?,
    val hasReplyAction: Boolean,
)

/** Something that wants every incoming chat message (auto-reply, voice mode). */
interface MessageConsumer {
    suspend fun onMessage(message: IncomingMessage)
}
