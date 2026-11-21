package dev.akibh.mycompanion.messaging

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What each notification in the shade already said, so voice mode reads only what's new. Chat apps
 * re-post the same notification many times (a new title, a refreshed list, another chat's message,
 * coming back online) without anything new to read; the notification's timestamps and title change
 * on those re-posts, so they can't tell old from new.
 *
 * A post is new when it adds a message (sender and text) or, without messages, when its title or
 * text changed. A notification is remembered until it's removed from the shade.
 */
@Singleton
class NoticeMemory @Inject constructor() {
    private val seen = LinkedHashMap<String, Map<String, Int>>()

    /** True when [parts] says something its earlier posts didn't; remembers it either way. */
    @Synchronized
    fun isNew(parts: NotificationParts): Boolean {
        val now = contents(parts)
        val before = seen.remove(parts.key)
        store(parts.key, now)
        if (before == null) return true
        return now.any { (item, count) -> count > (before[item] ?: 0) }
    }

    /** For notifications already in the shade when the listener connects: seen, not read. */
    @Synchronized
    fun remember(parts: NotificationParts) {
        seen.remove(parts.key)
        store(parts.key, contents(parts))
    }

    @Synchronized
    fun forget(key: String) {
        seen.remove(key)
    }

    private fun store(key: String, contents: Map<String, Int>) {
        seen[key] = contents
        while (seen.size > MAX_NOTIFICATIONS) seen.remove(seen.keys.first())
    }

    private fun contents(parts: NotificationParts): Map<String, Int> {
        val items = if (parts.messages.isNotEmpty()) {
            parts.messages.map { (sender, text) -> "${sender.orEmpty().trim()}$SEP${text.trim()}" }
        } else {
            val text = parts.bigText?.takeIf { it.isNotBlank() } ?: parts.text
            listOf("${parts.title.orEmpty().trim()}$SEP${text.orEmpty().trim()}")
        }
        return items.groupingBy { it }.eachCount()
    }

    private companion object {
        const val SEP = '\u0000'
        const val MAX_NOTIFICATIONS = 500
    }
}
