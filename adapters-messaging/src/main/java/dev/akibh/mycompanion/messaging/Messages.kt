package dev.akibh.mycompanion.messaging

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
    val messageTimes: List<Long> = emptyList(),
    /** The expanded text (BigTextStyle), when the app gives one. */
    val bigText: String? = null,
    val isForegroundService: Boolean = false,
    /** Media controls (MediaStyle): a player, not something to read. */
    val isMedia: Boolean = false,
    /** Posted without sound or vibration (importance low or min), from the listener's ranking. */
    val isSilent: Boolean = false,
)

/** Something that wants every incoming chat message (auto-reply, voice mode). */
interface MessageConsumer {
    suspend fun onMessage(message: IncomingMessage)
}

/**
 * Any app's notification, reduced to what voice mode reads. Chat messages carry [sender]; other
 * notifications have a [title] (the bold header line) and [text].
 */
data class Notice(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val sender: String? = null,
    val conversation: String? = null,
    val isGroup: Boolean = false,
    val postedAt: Long = 0,
    val isSilent: Boolean = false,
) {
    val isChatApp: Boolean get() = ChatApp.of(packageName) != null

    /** Same for an update that changes nothing worth reading, so it's read once. */
    val fingerprint: String get() = listOf(packageName, key, title, sender, text, postedAt).joinToString("|")
}

/** A chat message as a [Notice], for voice mode and tests. */
fun IncomingMessage.toNotice(title: String? = null, isSilent: Boolean = false): Notice = Notice(
    key = key,
    packageName = app.packageName,
    appName = app.title,
    title = title ?: conversation,
    text = text,
    sender = sender,
    conversation = conversation,
    isGroup = isGroup,
    postedAt = postedAt,
    isSilent = isSilent,
)

/** Something that wants every readable notification from any app (voice mode). */
interface NoticeConsumer {
    suspend fun onNotice(notice: Notice)
}
