package dev.akibh.mycompanion.messaging

/**
 * Turns any app's notification into a [Notice] worth reading aloud, or null for the ones that only
 * make noise: summaries, ongoing and foreground-service notifications, players, progress bars, calls.
 */
object NoticeParser {

    /** Categories that are status, not news. Alarms and timers ring on their own. */
    private val SKIPPED_CATEGORIES = setOf(
        ChatAdapter.CATEGORY_CALL,
        "alarm",
        "location_sharing",
        "navigation",
        "progress",
        "service",
        "status",
        "stopwatch",
        "sys",
        "transport",
    )

    /**
     * @param chat what the chat adapter made of it; for a chat app, null means "not a new message"
     *   (a summary, your own message, a status line) and nothing is read.
     */
    fun parse(parts: NotificationParts, appName: String, chat: IncomingMessage?): Notice? {
        if (parts.isGroupSummary || parts.isOngoing || parts.isForegroundService || parts.isMedia) return null
        if (parts.category in SKIPPED_CATEGORIES) return null
        if (ChatApp.of(parts.packageName) != null) {
            return chat?.toNotice(title = parts.title?.trim()?.ifEmpty { null }, isSilent = parts.isSilent)
                ?.copy(appName = appName.ifBlank { chat.app.title })
        }
        parts.messages.lastOrNull()?.let { (sender, text) ->
            // MessagingStyle: a null sender is you, writing from another device.
            if (sender.isNullOrBlank() || text.isBlank()) return null
            val conversation = parts.conversationTitle?.trim()?.ifEmpty { null }
            return Notice(
                key = parts.key,
                packageName = parts.packageName,
                appName = appName,
                title = conversation ?: sender.trim(),
                text = text.trim(),
                sender = sender.trim(),
                conversation = conversation ?: sender.trim(),
                isGroup = parts.isGroupConversation,
                postedAt = parts.messageTimes.lastOrNull() ?: 0,
                isSilent = parts.isSilent,
            )
        }
        val title = parts.title?.trim().orEmpty().takeUnless { it.equals(appName, ignoreCase = true) }.orEmpty()
        val text = (parts.bigText?.takeIf { it.isNotBlank() } ?: parts.text)?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null
        return Notice(
            key = parts.key,
            packageName = parts.packageName,
            appName = appName,
            title = title,
            text = text,
            isSilent = parts.isSilent,
        )
    }
}
