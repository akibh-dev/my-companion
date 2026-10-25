package com.akib.mycompanion.messaging

/**
 * One adapter per chat app (plan Phase 2): each knows its app's notification quirks. They share the
 * common parsing in [parse]; an app that changes its notifications only breaks its own adapter.
 */
open class ChatAdapter(val app: ChatApp) {

    /** Notifications that aren't a new message: summaries, status lines, calls. */
    protected open val noise: List<Regex> = listOf(
        Regex("""^\d+ new messages?.*""", RegexOption.IGNORE_CASE),
        Regex("""^\d+ messages? from \d+ chats?.*""", RegexOption.IGNORE_CASE),
        Regex("""^Checking for new messages.*""", RegexOption.IGNORE_CASE),
    )

    fun parse(parts: NotificationParts): IncomingMessage? {
        if (parts.isGroupSummary || parts.isOngoing || parts.category == CATEGORY_CALL) return null
        val (sender, text) = senderAndText(parts) ?: return null
        if (text.isBlank() || noise.any { it.matches(text.trim()) }) return null
        val group = parts.isGroupConversation || isGroupByTitle(parts)
        return IncomingMessage(
            key = parts.key,
            app = app,
            sender = sender,
            text = text.trim(),
            conversation = if (group) parts.conversationTitle ?: groupName(parts) ?: sender else sender,
            isGroup = group,
            postedAt = parts.postedAt,
            canReply = parts.hasReplyAction,
        )
    }

    /** The newest message from someone else; null when it's your own (sent from another device). */
    protected open fun senderAndText(parts: NotificationParts): Pair<String, String>? {
        parts.messages.lastOrNull()?.let { (sender, text) ->
            return sender?.let { it to text }
        }
        val title = parts.title?.takeIf { it.isNotBlank() } ?: return null
        val text = parts.text ?: return null
        if (title.equals(app.title, ignoreCase = true)) return null
        return title to text
    }

    protected open fun isGroupByTitle(parts: NotificationParts): Boolean = false

    protected open fun groupName(parts: NotificationParts): String? = null

    companion object {
        const val CATEGORY_CALL = "call"

        val ALL: Map<ChatApp, ChatAdapter> = listOf(
            WhatsAppAdapter(ChatApp.WHATSAPP),
            WhatsAppAdapter(ChatApp.WHATSAPP_BUSINESS),
            ChatAdapter(ChatApp.MESSENGER),
            SignalAdapter(),
            TelegramAdapter(),
            ChatAdapter(ChatApp.GOOGLE_MESSAGES),
        ).associateBy { it.app }
    }
}

/** WhatsApp without MessagingStyle titles group messages "Group: Sender" or "Sender @ Group". */
class WhatsAppAdapter(app: ChatApp) : ChatAdapter(app) {
    override val noise = super.noise + listOf(
        Regex("""^(Incoming|Missed|Ongoing) (voice|video) call.*""", RegexOption.IGNORE_CASE),
        Regex("""^WhatsApp Web is currently active.*""", RegexOption.IGNORE_CASE),
    )

    override fun senderAndText(parts: NotificationParts): Pair<String, String>? {
        val (sender, text) = super.senderAndText(parts) ?: return null
        return split(sender)?.second?.let { it to text } ?: (sender to text)
    }

    override fun isGroupByTitle(parts: NotificationParts) =
        parts.messages.isEmpty() && split(parts.title.orEmpty()) != null

    override fun groupName(parts: NotificationParts) = split(parts.title.orEmpty())?.first

    /** "Family: Mum" → (Family, Mum); "Mum @ Family" → (Family, Mum). */
    private fun split(title: String): Pair<String, String>? = when {
        " @ " in title -> title.substringAfter(" @ ") to title.substringBefore(" @ ")
        ": " in title -> title.substringBefore(": ") to title.substringAfter(": ")
        else -> null
    }
}

class SignalAdapter : ChatAdapter(ChatApp.SIGNAL) {
    override val noise = super.noise + Regex("""^Background connection enabled.*""", RegexOption.IGNORE_CASE)
}

/** Telegram groups without MessagingStyle: title is the group, text is "Sender: message". */
class TelegramAdapter : ChatAdapter(ChatApp.TELEGRAM) {
    override fun senderAndText(parts: NotificationParts): Pair<String, String>? {
        val text = parts.text
        val plainGroup = parts.messages.isEmpty() && parts.isGroupConversation && parts.title != null
        if (plainGroup && text?.contains(": ") == true) {
            return text.substringBefore(": ") to text.substringAfter(": ")
        }
        return super.senderAndText(parts)
    }

    override fun groupName(parts: NotificationParts) = parts.title
}
